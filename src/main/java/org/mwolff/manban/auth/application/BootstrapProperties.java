package org.mwolff.manban.auth.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration des Admin-Bootstraps.
 *
 * @param adminToken Einmal-Token zum Erzeugen des ersten Plattform-Admins; {@code null}/leer
 *     bedeutet deaktiviert. Wirkt nur, solange kein Plattform-Admin existiert.
 */
@ConfigurationProperties(prefix = "manban.bootstrap")
public record BootstrapProperties(String adminToken) {
}
