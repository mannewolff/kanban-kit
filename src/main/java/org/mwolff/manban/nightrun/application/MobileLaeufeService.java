package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.nightrun.domain.Meldegrund;
import org.mwolff.manban.nightrun.domain.Meldegrund.Zustand;
import org.mwolff.manban.nightrun.domain.MobilerLauf;
import org.mwolff.manban.nightrun.domain.MobilerLauf.LetzteMeldung;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Die Läufe für die mobile Oberfläche (Issue #1994, Plan #1989 E9 und E19, fachliche Quelle #1961
 * AK 9 bis 11): die laufenden, hängenden und gescheiterten Nachtläufe der laufenden und der vorigen
 * Nacht über alle Projekte, und ein einzelner Lauf mit seiner letzten Meldung.
 *
 * <p><b>Wer sieht was (E9).</b> Gezeigt werden die Läufe der Projekte, in denen {@link
 * PermissionChecker#hatNachtlaufZugriff} gilt — dieselbe Regel wie am großen Bildschirm: der Owner,
 * ein Plattform-Admin nur bei teilnehmendem Projekt. Die Auswahl der Projekte kommt aus {@link
 * ProjectService#listAccessible}; die Prüfung je Projekt entscheidet. Auch der einzelne Lauf wird
 * nur in diesen Projekten gesucht: Ein Lauf außerhalb ist so unbekannt wie eine erfundene ID.
 *
 * <p><b>Was gezeigt wird.</b> Den Zustand rechnet {@link Meldegrund}, dieselbe Rechnung, die später
 * der Meldejob nutzt: Was hier als hängend oder gescheitert steht, ist genau das, wozu eine
 * Nachricht kommt. Nur lesen — kein Schließen, kein Quittieren; der Plattform-Leitstand bleibt, wie
 * er ist.
 */
@Service
public class MobileLaeufeService {

  private final NightRunRepository runs;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final BoardService boards;
  private final NightRunProperties properties;
  private final Clock clock;

  public MobileLaeufeService(
      NightRunRepository runs,
      PermissionChecker permissions,
      ProjectService projects,
      BoardService boards,
      NightRunProperties properties,
      Clock clock) {
    this.runs = runs;
    this.permissions = permissions;
    this.projects = projects;
    this.boards = boards;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Eine Zeile der Liste.
   *
   * @param laufId Kennung des Laufs
   * @param projectId Kennung seines Projekts
   * @param projectName Name seines Projekts
   * @param boardId das Board, über dessen Zugangsschlüssel er gemeldet wird; {@code null}, wenn
   *     keines erkennbar ist (Issue #1993)
   * @param boardName Name dieses Boards; {@code null} ohne Board
   * @param zustand wie der Lauf erscheint
   * @param startedAt Beginn
   * @param dauer bisherige Dauer, beim beendeten Lauf die gemeldete
   */
  public record LaufZeile(
      long laufId,
      long projectId,
      String projectName,
      @Nullable Long boardId,
      @Nullable String boardName,
      Zustand zustand,
      Instant startedAt,
      Duration dauer) {}

  /** Ein einzelner Lauf: die Zeile und seine letzte Meldung, {@code null}, wenn er keine hat. */
  public record LaufDetail(LaufZeile zeile, @Nullable LetzteMeldung meldung) {}

  /**
   * Die laufenden, hängenden und gescheiterten Nachtläufe der laufenden und der vorigen Nacht,
   * jüngster zuoberst.
   *
   * @param zone Zone des Lesers, in der die Nachtgrenze um 12:00 gezogen wird
   */
  @Transactional(readOnly = true)
  public List<LaufZeile> laeufe(long userId, ZoneId zone) {
    Instant jetzt = clock.instant();
    NightRunPeriod nacht = NightRunPeriod.laufendeNacht(jetzt, zone);
    NightRunPeriod vorige = nacht.previous();
    Map<Long, String> boardNamen = new HashMap<>();
    List<LaufZeile> zeilen = new ArrayList<>();
    for (AccessibleProject projekt : sichtbareProjekte(userId)) {
      List<NightRun> laeufe =
          runs.findByProjectAndKindOrderByStartedAtDesc(projekt.id(), NightRunKind.NIGHT).stream()
              .filter(run -> vorige.contains(run.startedAt()) || nacht.contains(run.startedAt()))
              .toList();
      Map<Long, List<NightRunItem>> pakete = paketeJeLauf(laeufe);
      for (NightRun run : laeufe) {
        MobilerLauf sicht = sicht(run, pakete.getOrDefault(run.requireId(), List.of()), jetzt);
        if (sicht.zustand() != Zustand.BEENDET) {
          zeilen.add(zeile(projekt, run, sicht, boardNamen));
        }
      }
    }
    zeilen.sort(Comparator.comparing(LaufZeile::startedAt).reversed());
    return zeilen;
  }

  /**
   * Ein einzelner Lauf, auch ein gelungener oder älterer — der Tipp auf eine Nachricht zeigt den
   * aktuellen Stand (AK 11).
   *
   * @throws NightRunNotFoundException wenn es den Nachtlauf in keinem Projekt gibt, dessen Läufe
   *     der Aufrufer sehen darf (404, kein Existenz-Leak)
   */
  @Transactional(readOnly = true)
  public LaufDetail lauf(long userId, long laufId) {
    for (AccessibleProject projekt : sichtbareProjekte(userId)) {
      NightRun run = runs.findByIdAndProjectId(laufId, projekt.id()).orElse(null);
      if (run != null && run.kind() == NightRunKind.NIGHT) {
        MobilerLauf sicht = sicht(run, runs.findItemsByRunIds(List.of(laufId)), clock.instant());
        return new LaufDetail(zeile(projekt, run, sicht, new HashMap<>()), sicht.meldung());
      }
    }
    throw new NightRunNotFoundException();
  }

  /** Die Projekte, deren Läufe der Aufrufer sehen darf (E9). */
  private List<AccessibleProject> sichtbareProjekte(long userId) {
    return projects.listAccessible(userId).stream()
        .filter(p -> permissions.hatNachtlaufZugriff(userId, p.id()))
        .toList();
  }

  /** Die Pakete der Läufe je Lauf, in einer Abfrage; die Laufreihenfolge der Pakete bleibt. */
  private Map<Long, List<NightRunItem>> paketeJeLauf(List<NightRun> laeufe) {
    if (laeufe.isEmpty()) {
      return Map.of();
    }
    return runs.findItemsByRunIds(laeufe.stream().map(NightRun::requireId).toList()).stream()
        .collect(Collectors.groupingBy(item -> Objects.requireNonNull(item.nightRunId())));
  }

  private MobilerLauf sicht(NightRun run, List<NightRunItem> items, Instant jetzt) {
    return MobilerLauf.von(run, items, jetzt, properties.stilleFrist());
  }

  private LaufZeile zeile(
      AccessibleProject projekt, NightRun run, MobilerLauf sicht, Map<Long, String> boardNamen) {
    Long boardId = run.boardId();
    return new LaufZeile(
        run.requireId(),
        projekt.id(),
        projekt.name(),
        boardId,
        boardId == null
            ? null
            : boardNamen.computeIfAbsent(boardId, id -> boards.requireBoardSummary(id).name()),
        sicht.zustand(),
        run.startedAt(),
        sicht.dauer());
  }
}
