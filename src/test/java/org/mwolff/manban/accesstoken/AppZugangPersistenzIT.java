package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.StellbareUhrConfig;
import org.mwolff.manban.accesstoken.application.AccessTokenRepository;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.accesstoken.domain.AccessTokenArt;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Art eines Zugangs übersteht den Weg in die Datenbank und zurück (Issue #2102, Plan #2100 E1).
 *
 * <p>Dieselbe Konfiguration wie {@code RevokeRaceIT}, damit sich beide einen Kontext teilen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(StellbareUhrConfig.class)
class AppZugangPersistenzIT extends AbstractIntegrationTest {

  @Autowired private AccessTokenRepository tokens;
  @Autowired private AccessTokenService accessTokens;
  @Autowired private AppUserRepository users;
  @Autowired private TokenCryptoPort crypto;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void einZugangDerAppWirdMitSeinerArtGespeichertUndGelesen() {
    long zugang = gespeichert(AccessTokenArt.APP, crypto.generate()).requireId();

    assertThat(tokens.findById(zugang)).map(AccessToken::art).contains(AccessTokenArt.APP);
  }

  @Test
  void einGewoehnlichesTokenBleibtStandard() {
    long token = accessTokens.create(konto("standard@example.com"), "CI", null, null).id();

    assertThat(tokens.findById(token)).map(AccessToken::art).contains(AccessTokenArt.STANDARD);
  }

  @Test
  void dieAufloesungEinesGespeichertenZugangsSetztDasMerkmal() {
    GeneratedToken generiert = crypto.generate();
    gespeichert(AccessTokenArt.APP, generiert);

    assertThat(accessTokens.resolveBinding(generiert.plaintext()))
        .map(KanbanPrincipal::appZugang)
        .contains(true);
  }

  private AccessToken gespeichert(AccessTokenArt art, GeneratedToken generiert) {
    long besitzer = konto("app@example.com");
    Long projekt =
        jdbc.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES ('P', ?) RETURNING id",
            Long.class,
            besitzer);
    Long board =
        jdbc.queryForObject(
            "INSERT INTO board (project_id, name) VALUES (?, 'B') RETURNING id",
            Long.class,
            projekt);
    return tokens.save(
        new AccessToken(
            null,
            besitzer,
            projekt,
            board,
            "App",
            generiert.hash(),
            "App",
            Instant.parse("2026-01-02T03:04:05Z"),
            null,
            false,
            art));
  }

  private long konto(String email) {
    return users.save(new AppUser(null, email, "A", true, PlatformRole.USER)).requireId();
  }
}
