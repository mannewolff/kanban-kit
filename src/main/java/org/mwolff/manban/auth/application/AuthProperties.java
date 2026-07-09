package org.mwolff.manban.auth.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Auth-Konfiguration.
 *
 * @param baseUrl         öffentliche Basis-URL für Links in E-Mails (z. B. Verifikation)
 * @param verificationTtl Gültigkeitsdauer eines E-Mail-Verifikations-Tokens
 */
@ConfigurationProperties(prefix = "manban.auth")
public record AuthProperties(String baseUrl, Duration verificationTtl) {

    public AuthProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8080";
        }
        if (verificationTtl == null) {
            verificationTtl = Duration.ofHours(24);
        }
    }
}
