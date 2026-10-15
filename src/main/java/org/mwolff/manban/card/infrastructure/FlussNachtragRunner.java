package org.mwolff.manban.card.infrastructure;

import org.mwolff.manban.card.application.FlussNachtrag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Startet beim Hochfahren den einmaligen {@link FlussNachtrag} (Issue #1770, Plan #1765 E6, E20).
 * Ist er schon gelaufen, tut er nichts. Scheitert er, bleibt die Marke offen, und der Start läuft
 * weiter: Die Platte „Fluss“ zeigt die Tage vor der Migration dann als „keine Daten“ (E9), der
 * nächste Start versucht es erneut.
 */
// PMD.AvoidCatchingGenericException: Der Nachtrag darf den Start nie verhindern (E20) — jeder
// Fehler, auch einer der Datenbank, wird geloggt; die offene Marke ist seine Behandlung.
@SuppressWarnings("PMD.AvoidCatchingGenericException")
@Component
class FlussNachtragRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(FlussNachtragRunner.class);

  private final FlussNachtrag nachtrag;

  FlussNachtragRunner(FlussNachtrag nachtrag) {
    this.nachtrag = nachtrag;
  }

  @Override
  public void run(ApplicationArguments args) {
    try {
      nachtrag.nachtragen();
    } catch (RuntimeException e) {
      log.error("Fluss-Nachtrag gescheitert, die Marke bleibt offen", e);
    }
  }
}
