package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Reiner Unit-Test ohne Spring-Kontext — hält {@code mvn verify} auf dem leeren
 * Skelett grün, ohne eine laufende Datenbank vorauszusetzen. Der erste
 * kontext-/DB-abhängige Integrationstest kommt mit Issue F2 (Testcontainers).
 */
class ManbanApplicationSanityTest {

    @Test
    void skeletonCompilesAndBuilds() {
        assertThat("manban").isNotBlank();
    }
}
