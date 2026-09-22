package org.mwolff.manban;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Ersetzt die {@code Clock}-Bean eines IT-Kontexts durch eine stellbare Uhr (Issue #997).
 *
 * <p>Bewusst als eigene Klasse statt als verschachtelte {@code @TestConfiguration} je Testklasse:
 * Jede verschachtelte Variante ergibt einen eigenen Spring-Kontext mit eigenem Verbindungspool;
 * mehrere Testklassen, die <em>dieselbe</em> Konfiguration importieren, teilen sich dagegen einen
 * Kontext (Issue #900 zur Verbindungsknappheit).
 *
 * <p>Der Startzeitpunkt ist auf Mikrosekunden gekürzt: {@code timestamptz(6)} speichert nicht
 * feiner, und ein Vergleich „geschriebener Stempel = Zeitpunkt der Uhr" schlüge sonst an den
 * Nanosekunden fehl, die die Datenbank gar nicht behalten kann.
 */
@TestConfiguration
public class StellbareUhrConfig {

  @Bean
  @Primary
  public MutableClock stellbareUhr() {
    return new MutableClock(Instant.now().truncatedTo(ChronoUnit.MICROS));
  }
}
