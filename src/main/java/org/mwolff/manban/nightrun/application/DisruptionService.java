package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;
import org.mwolff.manban.nightrun.domain.NightRunPeriod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Cases des Plattform-Leitstands: seine drei Listen über alle teilnehmenden Projekte und das
 * Quittieren einer Störung (Issue #1080, Plan #1072 E29; um die Listen der laufenden Nacht
 * erweitert in Issue #1095).
 *
 * <p><b>Eigener Dienst neben {@link NightRunService}</b>, obwohl beide dieselben Tabellen lesen.
 * Der Grund ist die Rechteregel: Jeder Use-Case von {@code NightRunService} verlangt eine
 * <em>Projekt</em>-Rolle, und der Klassenkommentar dort trägt genau diese Zusage. Die beiden
 * Use-Cases hier gehen über alle Projekte und prüfen die <em>Plattform</em>-Rolle — sie wären dort
 * die Ausnahme, die man beim Lesen übersieht. {@link NightRunUsageService} ist der Präzedenzfall
 * für einen zweiten Dienst im selben Modul.
 */
@Service
public class DisruptionService {

  private final DisruptionRepository repository;
  private final NightRunRepository runs;
  private final PlatformAdminChecker platformAdminChecker;
  private final NightRunProperties properties;
  private final Clock clock;

  public DisruptionService(
      DisruptionRepository repository,
      NightRunRepository runs,
      PlatformAdminChecker platformAdminChecker,
      NightRunProperties properties,
      Clock clock) {
    this.repository = repository;
    this.runs = runs;
    this.platformAdminChecker = platformAdminChecker;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Die drei Listen des Plattform-Leitstands in <b>einem</b> Abruf (Issue #1095, Plan #1088 E5).
   *
   * <p><b>Warum einer und nicht drei:</b> Die Seite frischt sich alle 30 Sekunden auf (Kriterium
   * 19). Drei Abfragen wären drei Rundreisen gegen einen Stand, der sich dazwischen ändert — ein
   * Lauf, der zwischen der ersten und der zweiten Antwort endet, erschiene doppelt oder gar nicht.
   * Aus demselben Grund steht hier <b>eine</b> Rechteprüfung für alle drei Listen (Kriterium 15).
   *
   * <p>Die Datenbank liefert die Kandidaten, die Domäne entscheidet: Über die Aufteilung in
   * laufende und durchgeführte Läufe befindet allein der {@link NightRunOutcome}, nicht {@code
   * complete}. Ein verstummter Lauf trägt {@code complete = false} und steht trotzdem unter den
   * durchgeführten — das leistet die Stillefrist aus Issue #1091.
   *
   * <p><b>Die Zone kommt vom Leser</b> (Plan #1088 E6): Im Container läuft die JVM regelmäßig in
   * UTC, und „12:00 zonenlokal" wäre dann 14:00 in Berlin — die Nachtgrenze läge um Stunden
   * verschoben gegen die, die die Nachtlauf-Auswertung zieht.
   *
   * @param zone Zone, in der die Grenzen der laufenden Nacht gezogen werden
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   */
  @Transactional(readOnly = true)
  public LeitstandView leitstand(long userId, ZoneId zone) {
    requirePlatformAdmin(userId);
    NightRunPeriod nacht = NightRunPeriod.laufendeNacht(clock.instant(), zone);
    List<DisruptionRepository.DisruptionCandidate> derNacht =
        repository.candidatesOfNight(nacht.from(), nacht.to());
    List<DisruptionRepository.DisruptionCandidate> offene = repository.openCandidates();
    Map<Long, List<NightRunItem>> jeLauf = pakete(derNacht, offene);
    List<DisruptionView> laeufeDerNacht = views(derNacht, jeLauf);
    return new LeitstandView(
        laeufeDerNacht.stream().filter(v -> v.outcome().verdict() == Verdict.RUNNING).toList(),
        laeufeDerNacht.stream().filter(v -> v.outcome().verdict() != Verdict.RUNNING).toList(),
        views(offene, jeLauf).stream().filter(v -> v.outcome().isDisruption()).toList());
  }

  /**
   * Die Pakete beider Abfragen in <b>einem</b> Zug, je Lauf gebündelt.
   *
   * <p>Ein Lauf kann in beiden Listen stehen — eine Störung der laufenden Nacht steht unter den
   * durchgeführten <em>und</em> unter den Störungen. Zwei Abfragen liefen deshalb zweimal über
   * dieselben Zeilen; die Vereinigung hält die Reihenfolge der Nacht-Abfrage vorn, damit der Aufruf
   * vorhersagbar bleibt.
   */
  private Map<Long, List<NightRunItem>> pakete(
      List<DisruptionRepository.DisruptionCandidate> derNacht,
      List<DisruptionRepository.DisruptionCandidate> offene) {
    Set<Long> laufIds =
        Stream.concat(derNacht.stream(), offene.stream())
            .map(DisruptionRepository.DisruptionCandidate::nightRunId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    if (laufIds.isEmpty()) {
      return Map.of();
    }
    return runs.findItemsByRunIds(List.copyOf(laufIds)).stream()
        .collect(Collectors.groupingBy(NightRunItem::nightRunId));
  }

  private List<DisruptionView> views(
      List<DisruptionRepository.DisruptionCandidate> kandidaten,
      Map<Long, List<NightRunItem>> jeLauf) {
    return kandidaten.stream()
        .map(k -> view(k, jeLauf.getOrDefault(k.nightRunId(), List.of())))
        .toList();
  }

  /**
   * Quittiert die Störung eines Laufs — „ich habe es gesehen" (AK 8).
   *
   * <p><b>Idempotent:</b> Ein zweiter Aufruf ist kein Fehler. Zwei Admins können dieselbe Zeile
   * gleichzeitig wegräumen, und der zweite soll dabei nichts Rotes sehen.
   *
   * <p>Der Lauf selbst bleibt unberührt (AK 10) — die Quittung liegt in einer eigenen Tabelle und
   * sagt nichts über den Lauf, nur über seine Sichtung.
   *
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   * @throws DisruptionNotFoundException wenn der Lauf unbekannt ist — auch, weil ihn der Ringpuffer
   *     verdrängt hat — oder sein Projekt nicht teilnimmt (404)
   */
  @Transactional
  public void acknowledge(long userId, long nightRunId) {
    requirePlatformAdmin(userId);
    if (repository.ackTarget(nightRunId).isEmpty()) {
      throw new DisruptionNotFoundException();
    }
    repository.acknowledge(nightRunId, userId, clock.instant());
  }

  private void requirePlatformAdmin(long userId) {
    if (!platformAdminChecker.isPlatformAdmin(userId)) {
      throw new AdminAccessDeniedException();
    }
  }

  /**
   * Die Zeile eines Kandidaten — dieselbe für alle drei Listen.
   *
   * <p><b>Abschluss und Lebenszeichen kommen vom Kandidaten</b>, nicht als Festwert. Über {@link
   * DisruptionRepository#openCandidates()} ist {@code complete} stets {@code true}, weil die
   * Abfrage darauf filtert; über {@link DisruptionRepository#candidatesOfNight} nicht — dort
   * entscheidet erst die Stillefrist (Issue #1091), ob ein unfertiger Lauf noch läuft oder
   * verstummt ist.
   */
  private DisruptionView view(
      DisruptionRepository.DisruptionCandidate k, List<NightRunItem> items) {
    return new DisruptionView(
        k.nightRunId(),
        k.projectId(),
        k.projectName(),
        k.startedAt(),
        NightRunOutcome.of(
            k.complete(),
            k.noWorkReason(),
            items,
            k.startedAt(),
            k.updatedAt(),
            clock.instant(),
            properties.stilleFrist()));
  }

  /**
   * Eine Störzeile, wie der Plattform-Leitstand sie zeigt.
   *
   * <p>Der <b>Grund</b> steht nicht als Text hier, sondern als {@link NightRunOutcome}: Der Browser
   * bildet ihn aus denselben Tabellen, aus denen die Nachtlauf-Auswertung ihn zeigt. Ein zweiter
   * Satz im Server wäre die zweite Formulierung desselben Sachverhalts, die AK 6 verbietet.
   */
  public record DisruptionView(
      long nightRunId,
      long projectId,
      String projectName,
      Instant startedAt,
      NightRunOutcome outcome) {}

  /**
   * Die drei Bereiche des Plattform-Leitstands in ihrer Ordnung (Kriterium 18).
   *
   * <p>Dieselbe Zeilenform für alle drei: Ein laufender Lauf, ein durchgeführter und eine Störung
   * tragen dieselben Angaben — Projekt, Startzeitpunkt, Befund —, und woraus der Browser welchen
   * Melder und welches Wort bildet, steht im Befund.
   *
   * @param laufende Läufe der laufenden Nacht, die noch arbeiten; jüngster zuoberst
   * @param durchgefuehrte beendete Läufe derselben Nacht, verstummte eingeschlossen; jüngster
   *     zuoberst
   * @param stoerungen offene Störungen über <b>alle</b> Nächte (Kriterium 17), jüngste zuoberst
   */
  public record LeitstandView(
      List<DisruptionView> laufende,
      List<DisruptionView> durchgefuehrte,
      List<DisruptionView> stoerungen) {}
}
