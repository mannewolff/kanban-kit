package org.mwolff.manban.nightrun.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Ausgehender Port des Plattform-Leitstands: offene Störungen über alle teilnehmenden Projekte und
 * die Quittung je Lauf (Issue #1080, Plan #1072 E1).
 *
 * <p><b>Eine Störung ist kein gespeicherter Datensatz.</b> Gespeichert wird allein die Quittung;
 * die Störung selbst entsteht bei jeder Abfrage neu aus den aufbewahrten Läufen. Das ist keine
 * Sparsamkeit, sondern die einzige Bauform, in der AK 12, 18 und 19 keine Regeln brauchen: Eine
 * Störung verschwindet mit ihrem Lauf, weil sie ohne ihn nicht mehr entsteht; sie erscheint beim
 * Anhaken auch für ältere Läufe, weil die Abfrage sie dann mitnimmt; und sie kommt beim
 * Wiederanhaken zurück, weil nichts sie gelöscht hat. Eine materialisierte Tabelle bräuchte dafür
 * einen Backfill und drei Aufräumwege.
 *
 * <p>Die Rechteprüfung liegt <b>beim Aufrufer</b>: {@link DisruptionService} verlangt die
 * Plattform-Rolle ADMIN, bevor er hier etwas holt.
 */
public interface DisruptionRepository {

  /**
   * Die Kandidaten für die Störungsliste, jüngster Lauf zuoberst (AK 13).
   *
   * <p>Gefiltert wird auf das, was die Datenbank entscheiden kann: Gattung {@code NIGHT}
   * (interaktive Sitzungen sind keine Nachtläufe), abgeschlossener Lauf, teilnehmendes Projekt und
   * fehlende Quittung. <b>Ob ein Lauf tatsächlich gestört ist</b>, entscheidet danach {@link
   * org.mwolff.manban.nightrun.domain.NightRunOutcome} — dieser Maßstab gehört in die Domäne und
   * nicht in SQL, sonst gäbe es ihn zweimal.
   */
  List<DisruptionCandidate> openCandidates();

  /**
   * Das Ziel einer Quittung: der Lauf, sofern er existiert <b>und</b> sein Projekt teilnimmt.
   *
   * <p>Beides zusammen, weil der Aufrufer beides gleich beantwortet — mit 404. Ein verdrängter Lauf
   * und ein abgehaktes Projekt sind für den Plattform-Leitstand dasselbe: nichts, das er quittieren
   * dürfte.
   */
  Optional<AckTarget> ackTarget(long nightRunId);

  /**
   * Schreibt die Quittung. Liegt sie schon vor, bleibt sie unverändert stehen — der erste
   * Quittierende bleibt vermerkt, und der zweite bekommt keinen Fehler (AK 8).
   */
  void acknowledge(long nightRunId, long userId, Instant at);

  /**
   * Ein möglicher Störungs-Lauf mit allem, was der Maßstab braucht.
   *
   * @param nightRunId Lauf-Id, zugleich die anklickbare Kennung der Störzeile
   * @param projectId Projekt des Laufs
   * @param projectName Projektname zum Zeitpunkt der Abfrage
   * @param startedAt Startzeitpunkt des Laufs
   * @param noWorkReason Grund, warum der Lauf nichts abgearbeitet hat; {@code null}, wenn er
   *     gearbeitet hat
   */
  record DisruptionCandidate(
      long nightRunId,
      long projectId,
      String projectName,
      Instant startedAt,
      @Nullable String noWorkReason) {}

  /** Der Lauf, auf den sich eine Quittung bezieht. */
  record AckTarget(long nightRunId, long projectId) {}
}
