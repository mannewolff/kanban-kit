package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;
import org.mwolff.manban.nightrun.domain.RunnerLage;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Was der Runner jetzt tut und warum ein Paket wartet (Plan #1724): der Meldeweg der Bewertung der
 * Ready-Karten (Issue #1726) und die Lage des Runners (Issue #1727).
 *
 * <p>Die Methoden nehmen Primitive und keinen {@code KanbanPrincipal}: Application und Domäne von
 * {@code nightrun} kennen das Modul {@code accesstoken} nicht (ArchitectureTest).
 */
@Service
public class NightRunNowService {

  private final NightReadyAssessmentRepository bewertungen;
  private final NightRunRepository runs;
  private final PermissionChecker permissions;
  private final NightRunProperties properties;
  private final Clock clock;

  public NightRunNowService(
      NightReadyAssessmentRepository bewertungen,
      NightRunRepository runs,
      PermissionChecker permissions,
      NightRunProperties properties,
      Clock clock) {
    this.bewertungen = bewertungen;
    this.runs = runs;
    this.permissions = permissions;
    this.properties = properties;
    this.clock = clock;
  }

  /**
   * Was der Runner des Projekts jetzt tut (Plan #1724 E8, E14): je Lauf „läuft“ oder „verstummt“,
   * sonst „kein Runner aktiv“ mit dem letzten Lauf oder „noch kein Lauf gestartet“.
   *
   * <p>Gelesen werden die Nachtläufe — Ketten und Umsetzungsnächte —, nie interaktive Sitzungen:
   * Eine Sitzung am Rechner eines Menschen ist kein Runner. Die Schweigefrist ist die Stillefrist
   * aus {@link NightRunProperties} (E3). Pakete werden nur für den letzten beendeten Lauf geladen,
   * denn nur sein Ergebnis wird gezeigt.
   */
  @Transactional(readOnly = true)
  public RunnerLage jetzt(long userId, long projectId) {
    permissions.requireNightRunAccess(userId, projectId);
    List<NightRun> laeufe =
        runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT);
    List<NightRunItem> pakete =
        RunnerLage.letzterBeendeter(laeufe)
            .map(lauf -> runs.findItemsByRunIds(List.of(lauf.requireId())))
            .orElse(List.of());
    return RunnerLage.ermittle(laeufe, pakete, clock.instant(), properties.stilleFrist());
  }

  /**
   * Speichert die gemeldete Bewertung als die des Projekts (Plan #1724 E7, Vertrag V2).
   *
   * <p>Melden darf, wer auch Läufe meldet ({@link PermissionChecker#requireOwner}). Die
   * Projektsperre aus {@link NightRunRepository#lockProject} geht dem Lesen voraus: Zwei
   * gleichzeitige Meldungen sehen sonst beide den alten Stand, und die ältere könnte die jüngere
   * verdrängen. Eine gleich alte ersetzt — sie ist die Wiederholung derselben Meldung.
   *
   * @throws NightReadyAssessmentOutdatedException wenn die gespeicherte Bewertung jünger ist
   */
  @Transactional
  public void meldeBewertung(long userId, long projectId, ReadyBewertung bewertung) {
    permissions.requireOwner(userId, projectId);
    runs.lockProject(projectId);
    Optional<ReadyBewertung> gespeichert = bewertungen.findByProjectId(projectId);
    if (gespeichert.isPresent() && gespeichert.get().assessedAt().isAfter(bewertung.assessedAt())) {
      throw new NightReadyAssessmentOutdatedException();
    }
    bewertungen.ersetze(projectId, bewertung);
  }
}
