package org.mwolff.manban.auth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AdminVorschauRepository;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AdminVorschau;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.auth.domain.VorschauEndgrund;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Die Zusagen des Ports {@link AdminVorschauRepository} gegen echtes Postgres (Issue #1917). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdminVorschauRepositoryAdapterIT extends AbstractIntegrationTest {

  private static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");
  private static final Instant ABLAUF = NOW.plusSeconds(1800);

  @Autowired private AdminVorschauRepository vorschauen;
  @Autowired private AppUserRepository users;

  private long konto(String email, PlatformRole rolle) {
    return users.save(new AppUser(null, email, email, true, rolle)).requireId();
  }

  private static AdminVorschau offen(
      String kennung, @Nullable Long adminId, long zielId, Instant start) {
    return new AdminVorschau(
        null,
        kennung,
        "hash-" + kennung,
        adminId,
        "Ada",
        zielId,
        3L,
        start,
        start.plusSeconds(1800),
        null,
        null);
  }

  @Test
  void speichertAlleSpaltenUndFindetDieZeileUeberDieKennung() {
    long admin = konto("a@example.org", PlatformRole.ADMIN);
    long ziel = konto("x@example.org", PlatformRole.USER);

    AdminVorschau gespeichert = vorschauen.save(offen("k1", admin, ziel, NOW));

    assertThat(gespeichert.id()).isNotNull();
    assertThat(vorschauen.findByKennung("k1")).contains(gespeichert);
    assertThat(gespeichert)
        .usingRecursiveComparison()
        .ignoringFields("id")
        .isEqualTo(offen("k1", admin, ziel, NOW));
    assertThat(vorschauen.findByKennung("unbekannt")).isEmpty();
  }

  @Test
  void beendetGenauEinmal() {
    long admin = konto("a@example.org", PlatformRole.ADMIN);
    long ziel = konto("x@example.org", PlatformRole.USER);
    long id = vorschauen.save(offen("k1", admin, ziel, NOW)).requireId();

    assertThat(vorschauen.beende(id, NOW.plusSeconds(60), VorschauEndgrund.BEENDET)).isTrue();
    assertThat(vorschauen.beende(id, NOW.plusSeconds(90), VorschauEndgrund.ABMELDUNG)).isFalse();

    AdminVorschau zeile = vorschauen.findByKennung("k1").orElseThrow();
    assertThat(zeile.beendetAm()).isEqualTo(NOW.plusSeconds(60));
    assertThat(zeile.endgrund()).isEqualTo(VorschauEndgrund.BEENDET);
  }

  @Test
  void beendetAbgelaufeneZeilenDesZielsMitIhremAblaufzeitpunkt() {
    long admin = konto("a@example.org", PlatformRole.ADMIN);
    long ziel = konto("x@example.org", PlatformRole.USER);
    long anderes = konto("y@example.org", PlatformRole.USER);
    vorschauen.save(offen("abgelaufen", admin, ziel, NOW));
    vorschauen.save(offen("laeuft", admin, ziel, NOW.plusSeconds(600)));
    vorschauen.save(offen("fremd", admin, anderes, NOW));
    long zu = vorschauen.save(offen("zu", admin, ziel, NOW.minusSeconds(60))).requireId();
    vorschauen.beende(zu, NOW, VorschauEndgrund.BEENDET);

    assertThat(vorschauen.beendeAbgelaufene(ziel, ABLAUF)).isEqualTo(1);

    assertThat(vorschauen.findByKennung("abgelaufen").orElseThrow())
        .extracting(AdminVorschau::beendetAm, AdminVorschau::endgrund)
        .containsExactly(ABLAUF, VorschauEndgrund.ABLAUF);
    assertThat(vorschauen.findByKennung("laeuft").orElseThrow().beendet()).isFalse();
    assertThat(vorschauen.findByKennung("fremd").orElseThrow().beendet()).isFalse();
    assertThat(vorschauen.findByKennung("zu").orElseThrow().endgrund())
        .isEqualTo(VorschauEndgrund.BEENDET);
  }

  @Test
  void findetDieZeilenEinesZielsNeuesteZuerst() {
    long admin = konto("a@example.org", PlatformRole.ADMIN);
    long ziel = konto("x@example.org", PlatformRole.USER);
    long anderes = konto("y@example.org", PlatformRole.USER);
    vorschauen.save(offen("alt", admin, ziel, NOW));
    vorschauen.save(offen("neu", admin, ziel, NOW.plusSeconds(60)));
    vorschauen.save(offen("fremd", admin, anderes, NOW));

    assertThat(vorschauen.findByZiel(ziel))
        .extracting(AdminVorschau::kennung)
        .containsExactly("neu", "alt");
  }
}
