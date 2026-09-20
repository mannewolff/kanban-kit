package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Cases des Plattform-Leitstands: die offenen Störungen aller teilnehmenden Projekte und das
 * Quittieren einer Störung (Issue #1080, Plan #1072 E29).
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
  private final Clock clock;

  public DisruptionService(
      DisruptionRepository repository,
      NightRunRepository runs,
      PlatformAdminChecker platformAdminChecker,
      Clock clock) {
    this.repository = repository;
    this.runs = runs;
    this.platformAdminChecker = platformAdminChecker;
    this.clock = clock;
  }

  /**
   * Die offenen Störungen aller teilnehmenden Projekte, jüngste zuoberst (AK 4, 13).
   *
   * <p>Die Datenbank liefert die Kandidaten, die Domäne entscheidet: Nur ein Lauf, dessen {@link
   * NightRunOutcome} eine Störung ist, kommt in die Liste. Ein gelungener Lauf eines teilnehmenden
   * Projekts ist ein Kandidat, aber keine Störung.
   *
   * @throws AdminAccessDeniedException wenn der Aufrufer kein Plattform-Admin ist (403)
   */
  @Transactional(readOnly = true)
  public List<DisruptionView> disruptions(long userId) {
    requirePlatformAdmin(userId);
    List<DisruptionRepository.DisruptionCandidate> kandidaten = repository.openCandidates();
    if (kandidaten.isEmpty()) {
      return List.of();
    }
    Map<Long, List<NightRunItem>> jeLauf =
        runs
            .findItemsByRunIds(
                kandidaten.stream()
                    .map(DisruptionRepository.DisruptionCandidate::nightRunId)
                    .toList())
            .stream()
            .collect(Collectors.groupingBy(NightRunItem::nightRunId));
    return kandidaten.stream()
        .map(k -> view(k, jeLauf.getOrDefault(k.nightRunId(), List.of())))
        .filter(v -> v.outcome().isDisruption())
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

  private static DisruptionView view(
      DisruptionRepository.DisruptionCandidate k, List<NightRunItem> items) {
    return new DisruptionView(
        k.nightRunId(),
        k.projectId(),
        k.projectName(),
        k.startedAt(),
        NightRunOutcome.of(true, k.noWorkReason(), items));
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
}
