package org.mwolff.manban.deployment.application;

import java.time.Instant;
import java.util.Optional;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;

/**
 * Port der gemeldeten Auslieferungsversuche (Issue #1833, Plan #1831 A2, E6, E12). Er speichert
 * jede Meldung vollständig und entscheidet nichts nach der Reihenfolge des Eingangs; die
 * Transaktion stellt der Dienst.
 */
public interface DeploymentStore {

  /**
   * Speichert einen neuen Versuch mit allen Commits der Meldung. Eine schon vorhandene Kennung im
   * Projekt verletzt die Eindeutigkeit {@code (project_id, attempt_id)}; der Dienst prüft vorher.
   */
  void fuegeEin(long projectId, String tokenName, NeueMeldung meldung, Instant eingang);

  /** Der gespeicherte Versuch zur Kennung, in der Form der Meldung; leer ohne. */
  Optional<NeueMeldung> findeVersuch(long projectId, String attemptId);

  /**
   * Alle Versuche des Projekts und die Commits, deren Auslieferung am oder nach {@code
   * fensterBeginn} liegen kann. Ausgelassen wird nur ein Commit, den schon ein erfolgreicher
   * Versuch vor {@code fensterBeginn} ausgeliefert hat — gerechnet wie in der {@code
   * AuslieferungsErmittlung}, gleich in welcher Reihenfolge die Meldungen eingingen.
   */
  Auslieferungsdaten ladeAuswertung(long projectId, Instant fensterBeginn);

  /**
   * Der Versuch mit dem größten {@code finishedAt}, gleich welchen Ausgangs; bei Gleichstand der
   * zuletzt eingegangene. Leer ohne Versuch.
   */
  Optional<Auslieferungsversuch> letzterVersuch(long projectId);
}
