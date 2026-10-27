package org.mwolff.manban.auth.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.VorschauEndgrund;

/**
 * Ausgehender Port für die Vorschauen „Ansicht als XY“ (Issue #1917, Plan #1914 E2, E12).
 *
 * <p><strong>Warum das Beenden eine einzige Anweisung ist:</strong> Mehrere Tabs derselben
 * Anmeldung können gleichzeitig ein Ende feststellen, etwa den Ablauf und „Beenden“. Die Bedingung
 * „noch offen“ steht deshalb im {@code WHERE} desselben {@code UPDATE} (Muster aus Issue #497):
 * Genau ein Aufruf schreibt Zeitpunkt und Grund, alle anderen treffen keine Zeile mehr.
 */
public interface AdminVorschauRepository {

  AdminVorschau save(AdminVorschau vorschau);

  Optional<AdminVorschau> findByKennung(String kennung);

  /**
   * Beendet die noch offene Vorschau <strong>atomar</strong>.
   *
   * @return {@code true}, wenn dieser Aufruf das Ende geschrieben hat; {@code false}, wenn die
   *     Vorschau schon beendet war
   */
  boolean beende(long id, Instant beendetAm, VorschauEndgrund endgrund);

  /**
   * Beendet alle offenen Vorschauen des Zielkontos mit {@code laeuftAbAm <= now} mit ihrem
   * Ablaufzeitpunkt und dem Grund {@link VorschauEndgrund#ABLAUF}.
   *
   * @return Zahl der nachgeschriebenen Vorschauen
   */
  int beendeAbgelaufene(long zielId, Instant now);

  /** Alle Vorschauen als das Zielkonto, die zuletzt gestartete zuerst. */
  List<AdminVorschau> findByZiel(long zielId);
}
