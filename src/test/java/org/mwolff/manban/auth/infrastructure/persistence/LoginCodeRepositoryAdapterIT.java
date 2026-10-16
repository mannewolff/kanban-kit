package org.mwolff.manban.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.LoginCodeRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.LoginCode;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Zusagen des Ports {@link LoginCodeRepository} gegen echtes Postgres (Plan #1801, A1, A2).
 * Dass Verbrauch und Fehlversuch auch nebenläufig halten, prüft {@code LoginCodeConcurrencyIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LoginCodeRepositoryAdapterIT extends AbstractIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");
  private static final int MAX = 5;
  private static final String EMAIL = "email-hash-a";
  private static final String CODE = "code-hmac-a";
  private static final String LINK = "link-hash-a";

  @Autowired private LoginCodeRepository codes;
  @Autowired private AppUserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private Long konto() {
    return users
        .save(new AppUser(null, "person@example.com", "Person", true, PlatformRole.USER))
        .id();
  }

  private LoginCode offen(Long userId, String emailHash, String code, String link) {
    return new LoginCode(
        null, emailHash, userId, code, link, NOW, NOW.plusSeconds(600), 0, null, null);
  }

  private LoginCode speichern(Long userId) {
    return codes.save(offen(userId, EMAIL, CODE, LINK));
  }

  private @Nullable Instant spalte(String spalte, Long id) {
    OffsetDateTime wert =
        jdbc.queryForObject(
            "SELECT " + spalte + " FROM login_code WHERE id = ?", OffsetDateTime.class, id);
    return wert == null ? null : wert.toInstant();
  }

  @Test
  void speichertAlleSpaltenUndVergibtEineId() {
    Long userId = konto();

    LoginCode gespeichert = speichern(userId);

    assertThat(gespeichert.id()).isNotNull();
    assertThat(gespeichert)
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(offen(userId, EMAIL, CODE, LINK));
  }

  @Test
  void speichertEineAnforderungOhneKonto() {
    LoginCode ohneKonto =
        new LoginCode(null, EMAIL, null, null, null, NOW, NOW.plusSeconds(600), 0, null, null);

    LoginCode gespeichert = codes.save(ohneKonto);

    assertThat(gespeichert.userId()).isNull();
    assertThat(gespeichert.codeHash()).isNull();
    assertThat(gespeichert.linkHash()).isNull();
  }

  @Test
  void zaehltDieAnforderungenEinerAdresseSeitEinemZeitpunkt() {
    Long userId = konto();
    codes.save(offen(userId, EMAIL, "c1", "l1"));
    codes.save(
        new LoginCode(
            null,
            EMAIL,
            userId,
            "c2",
            "l2",
            NOW.minusSeconds(7200),
            NOW.minusSeconds(6600),
            0,
            null,
            null));
    codes.save(offen(userId, "andere-adresse", "c3", "l3"));

    assertThat(codes.countSince(EMAIL, NOW.minusSeconds(3600))).isEqualTo(1);
    assertThat(codes.countSince(EMAIL, NOW)).isEqualTo(1);
    assertThat(codes.countSince(EMAIL, NOW.plusSeconds(1))).isZero();
    assertThat(codes.countSince(EMAIL, NOW.minusSeconds(7200))).isEqualTo(2);
  }

  @Test
  void loestDenCodeEinUndLiefertDasKonto() {
    Long userId = konto();
    Long id = speichern(userId).id();

    assertThat(codes.consumeByCode(EMAIL, CODE, NOW, MAX)).hasValue(userId);
    assertThat(spalte("consumed_at", id)).isEqualTo(NOW);
  }

  @Test
  void loestDenLinkEinUndLiefertDasKonto() {
    Long userId = konto();
    Long id = speichern(userId).id();

    assertThat(codes.consumeByLink(LINK, NOW, MAX)).hasValue(userId);
    assertThat(spalte("consumed_at", id)).isEqualTo(NOW);
  }

  @Test
  void einFalscherCodeOderEineFremdeAdresseVerbrauchtNichts() {
    Long userId = konto();
    speichern(userId);

    assertThat(codes.consumeByCode(EMAIL, "falsch", NOW, MAX)).isEmpty();
    assertThat(codes.consumeByCode("andere-adresse", CODE, NOW, MAX)).isEmpty();
    assertThat(codes.consumeByLink("unbekannt", NOW, MAX)).isEmpty();
    assertThat(codes.consumeByCode(EMAIL, CODE, NOW, MAX)).hasValue(userId);
  }

  @Test
  void codeUndLinkGeltenGemeinsamNurEinmal() {
    Long userId = konto();
    speichern(userId);

    assertThat(codes.consumeByCode(EMAIL, CODE, NOW, MAX)).hasValue(userId);
    assertThat(codes.consumeByCode(EMAIL, CODE, NOW, MAX)).isEmpty();
    assertThat(codes.consumeByLink(LINK, NOW, MAX)).isEmpty();
  }

  @Test
  void derLinkVerbrauchtAuchDenCode() {
    Long userId = konto();
    speichern(userId);

    assertThat(codes.consumeByLink(LINK, NOW, MAX)).hasValue(userId);
    assertThat(codes.consumeByLink(LINK, NOW, MAX)).isEmpty();
    assertThat(codes.consumeByCode(EMAIL, CODE, NOW, MAX)).isEmpty();
  }

  @Test
  void eineAbgelaufeneAnforderungGiltNicht() {
    Long userId = konto();
    speichern(userId);
    Instant ablauf = NOW.plusSeconds(600);

    assertThat(codes.consumeByCode(EMAIL, CODE, ablauf, MAX)).isEmpty();
    assertThat(codes.consumeByLink(LINK, ablauf, MAX)).isEmpty();
    assertThat(codes.consumeByLink(LINK, ablauf.minusSeconds(1), MAX)).hasValue(userId);
  }

  @Test
  void ersetztOffeneAnforderungenDerAdresse() {
    Long userId = konto();
    Long alt = speichern(userId).id();
    Long fremd = codes.save(offen(userId, "andere-adresse", "c2", "l2")).id();

    assertThat(codes.supersedeOpen(EMAIL, NOW)).isEqualTo(1);

    assertThat(spalte("superseded_at", alt)).isEqualTo(NOW);
    assertThat(spalte("superseded_at", fremd)).isNull();
    assertThat(codes.consumeByCode(EMAIL, CODE, NOW, MAX)).isEmpty();
    assertThat(codes.consumeByLink(LINK, NOW, MAX)).isEmpty();
  }

  @Test
  void ersetztKeineVerbrauchteOderSchonErsetzteAnforderung() {
    Long userId = konto();
    Long verbraucht = codes.save(offen(userId, EMAIL, "c1", "l1")).id();
    codes.consumeByCode(EMAIL, "c1", NOW, MAX);
    codes.save(offen(userId, EMAIL, "c2", "l2"));
    codes.supersedeOpen(EMAIL, NOW);

    assertThat(codes.supersedeOpen(EMAIL, NOW.plusSeconds(5))).isZero();
    assertThat(spalte("superseded_at", verbraucht)).isNull();
  }

  @Test
  void zaehltFehlversucheBisZumMaximum() {
    Long userId = konto();
    Long id = speichern(userId).id();

    for (int i = 0; i < MAX; i++) {
      assertThat(codes.recordFailure(EMAIL, NOW, MAX)).isEqualTo(1);
    }

    assertThat(codes.recordFailure(EMAIL, NOW, MAX)).isZero();
    assertThat(fehlversuche(id)).isEqualTo(MAX);
  }

  @Test
  void abFuenfFehlversuchenGeltenCodeUndLinkNichtMehr() {
    Long userId = konto();
    speichern(userId);
    for (int i = 0; i < MAX - 1; i++) {
      codes.recordFailure(EMAIL, NOW, MAX);
    }
    assertThat(codes.consumeByCode(EMAIL, "falsch", NOW, MAX)).isEmpty();

    codes.recordFailure(EMAIL, NOW, MAX);

    assertThat(codes.consumeByCode(EMAIL, CODE, NOW, MAX)).isEmpty();
    assertThat(codes.consumeByLink(LINK, NOW, MAX)).isEmpty();
  }

  @Test
  void vierFehlversucheLassenDieEinloesungZu() {
    Long userId = konto();
    speichern(userId);
    for (int i = 0; i < MAX - 1; i++) {
      codes.recordFailure(EMAIL, NOW, MAX);
    }

    assertThat(codes.consumeByCode(EMAIL, CODE, NOW, MAX)).hasValue(userId);
  }

  @Test
  void fehlversucheTreffenNurOffeneAnforderungenDerAdresse() {
    Long userId = konto();
    Long verbraucht = codes.save(offen(userId, EMAIL, "c1", "l1")).id();
    codes.consumeByCode(EMAIL, "c1", NOW, MAX);
    Long fremd = codes.save(offen(userId, "andere-adresse", "c2", "l2")).id();
    Long abgelaufen =
        codes
            .save(
                new LoginCode(
                    null,
                    EMAIL,
                    userId,
                    "c3",
                    "l3",
                    NOW.minusSeconds(900),
                    NOW.minusSeconds(300),
                    0,
                    null,
                    null))
            .id();

    assertThat(codes.recordFailure(EMAIL, NOW, MAX)).isZero();

    assertThat(fehlversuche(verbraucht)).isZero();
    assertThat(fehlversuche(fremd)).isZero();
    assertThat(fehlversuche(abgelaufen)).isZero();
  }

  @Test
  void ersetzteAnforderungZaehltKeineFehlversuche() {
    Long userId = konto();
    Long id = speichern(userId).id();
    codes.supersedeOpen(EMAIL, NOW);

    assertThat(codes.recordFailure(EMAIL, NOW, MAX)).isZero();
    assertThat(fehlversuche(id)).isZero();
  }

  @Test
  void loeschtNurAnforderungenVorDemStichtag() {
    Long userId = konto();
    codes.save(
        new LoginCode(
            null,
            EMAIL,
            userId,
            "c1",
            "l1",
            NOW.minusSeconds(90_000),
            NOW.minusSeconds(89_400),
            0,
            null,
            null));
    Long jung = speichern(userId).id();

    assertThat(codes.deleteOlderThan(NOW.minusSeconds(86_400))).isEqualTo(1);

    assertThat(codes.countSince(EMAIL, Instant.EPOCH)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM login_code WHERE id = ?", Long.class, jung))
        .isEqualTo(1L);
  }

  @Test
  void loeschtKeineAnforderungGenauAmStichtag() {
    speichern(konto());

    assertThat(codes.deleteOlderThan(NOW)).isZero();
  }

  private int fehlversuche(Long id) {
    Integer wert =
        jdbc.queryForObject(
            "SELECT failed_attempts FROM login_code WHERE id = ?", Integer.class, id);
    return wert == null ? -1 : wert;
  }
}
