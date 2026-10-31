package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.nightrun.domain.Meldegrund;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesedienst des Meldejobs (Issue #1998, Plan #1989 E11, E12, E20): die Nachtläufe der laufenden
 * und der vorigen Nacht über alle Projekte, die einen {@link Meldegrund} haben, samt Projekt und
 * Board.
 *
 * <p><b>Ohne Rechteprüfung.</b> Der Meldejob fragt ohne Anfrage und ohne Aufrufer; wem eine
 * Nachricht gilt, prüft er selbst je Konto. Darum geht diese Sicht nie an einen Endpunkt — die
 * mobile Oberfläche liest über {@link MobileLaeufeService}.
 *
 * <p><b>Welche Nacht.</b> Die Nachtgrenze um 12:00 zieht der Dienst in der Zone der Uhr der
 * Anwendung: Der Job hat keinen Leser, dessen Zone gälte. Ein Lauf, der in keiner der beiden Nächte
 * begann, wird nicht mehr gemeldet — so meldet ein neu gestarteter Server keine alten Läufe nach.
 *
 * <p>Den Grund rechnet {@link Meldegrund}, dieselbe Rechnung wie in der mobilen Liste: Wozu eine
 * Nachricht kommt, steht dort als hängend oder gescheitert.
 */
@Service
public class MeldbareLaeufe {

  private final NightRunRepository runs;
  private final ProjectService projects;
  private final BoardService boards;
  private final NightRunProperties properties;
  private final Clock clock;

  public MeldbareLaeufe(
      NightRunRepository runs,
      ProjectService projects,
      BoardService boards,
      NightRunProperties properties,
      Clock clock) {
    this.runs = runs;
    this.projects = projects;
    this.boards = boards;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Ein Lauf mit Meldegrund.
   *
   * @param laufId Kennung des Laufs
   * @param projectId Kennung seines Projekts
   * @param projectName Name seines Projekts
   * @param boardId das Board seines Zugangsschlüssels; {@code null}, wenn keines erkennbar ist
   * @param boardName Name dieses Boards; {@code null} ohne Board
   * @param grund der schwerste Grund des Laufs
   */
  public record MeldbarerLauf(
      long laufId,
      long projectId,
      String projectName,
      @Nullable Long boardId,
      @Nullable String boardName,
      Meldegrund grund) {}

  /** Die Läufe mit Meldegrund, ältester zuerst. */
  @Transactional(readOnly = true)
  public List<MeldbarerLauf> laeufe() {
    Instant jetzt = clock.instant();
    Instant von = NightRunPeriod.laufendeNacht(jetzt, clock.getZone()).previous().from();
    List<NightRun> laeufe = runs.findByKindStartedSince(NightRunKind.NIGHT, von);
    if (laeufe.isEmpty()) {
      return List.of();
    }
    Map<Long, List<NightRunItem>> pakete =
        runs.findItemsByRunIds(laeufe.stream().map(NightRun::requireId).toList()).stream()
            .collect(Collectors.groupingBy(item -> Objects.requireNonNull(item.nightRunId())));
    List<MeldbarerLauf> meldbar = new ArrayList<>();
    for (NightRun run : laeufe) {
      Meldegrund grund =
          Meldegrund.von(
                  run,
                  pakete.getOrDefault(run.requireId(), List.of()),
                  jetzt,
                  properties.stilleFrist())
              .orElse(null);
      String projektName = grund == null ? null : projects.findName(run.projectId()).orElse(null);
      if (grund != null && projektName != null) {
        meldbar.add(lauf(run, projektName, grund));
      }
    }
    return meldbar;
  }

  private MeldbarerLauf lauf(NightRun run, String projektName, Meldegrund grund) {
    Long boardId = run.boardId();
    return new MeldbarerLauf(
        run.requireId(),
        run.projectId(),
        projektName,
        boardId,
        boardId == null ? null : boards.requireBoardSummary(boardId).name(),
        grund);
  }
}
