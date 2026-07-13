package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Reiner Unit-Test ohne Spring-Kontext — hält {@code mvn verify} grün, ohne eine laufende Datenbank
 * vorauszusetzen. Prüft nur, dass der Einstiegspunkt korrekt als Spring-Boot-Anwendung annotiert
 * ist; der vollständige Kontext-Aufbau läuft über die *IT-Tests (Testcontainers).
 */
class ManbanApplicationSanityTest {

  @Test
  void skeletonCompilesAndBuilds() {
    assertThat(ManbanApplication.class.getAnnotation(SpringBootApplication.class)).isNotNull();
  }
}
