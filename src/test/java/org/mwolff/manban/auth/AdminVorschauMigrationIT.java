package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Verifiziert {@code V62__admin_vorschau.sql} (Issue #1917, Plan #1914 E2, E12): Lebenslauf der
 * Einträge beim Löschen von Ziel- und Admin-Konto und die Prüfbedingungen der Tabelle.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdminVorschauMigrationIT extends AbstractIntegrationTest {

  private static final OffsetDateTime START = OffsetDateTime.parse("2026-10-26T10:00:00Z");
  private static final OffsetDateTime ABLAUF = OffsetDateTime.parse("2026-10-26T10:30:00Z");

  @Autowired private JdbcTemplate jdbc;

  private long konto(String email, String rolle) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO app_user (email, display_name, email_verified, platform_role)"
                + " VALUES (?, ?, true, ?) RETURNING id",
            Long.class,
            email,
            email,
            rolle);
    return id == null ? -1 : id;
  }

  private void vorschau(String kennung, long adminId, long zielId) {
    jdbc.update(
        "INSERT INTO admin_vorschau (kennung, geheimnis_hash, admin_id, admin_name, ziel_id,"
            + " sitzungs_generation, gestartet_am, laeuft_ab_am) VALUES (?, ?, ?, ?, ?, 0, ?, ?)",
        kennung,
        "hash-" + kennung,
        adminId,
        "Ada Admin",
        zielId,
        START,
        ABLAUF);
  }

  private int zeilen() {
    Integer anzahl = jdbc.queryForObject("SELECT count(*) FROM admin_vorschau", Integer.class);
    return anzahl == null ? -1 : anzahl;
  }

  @Test
  void mitDemZielkontoEntfallenSeineEintraege() {
    long admin = konto("a@example.org", "ADMIN");
    long ziel = konto("x@example.org", "USER");
    long anderes = konto("y@example.org", "USER");
    vorschau("k1", admin, ziel);
    vorschau("k2", admin, anderes);

    jdbc.update("DELETE FROM app_user WHERE id = ?", ziel);

    assertThat(zeilen()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT kennung FROM admin_vorschau", String.class))
        .isEqualTo("k2");
  }

  @Test
  void mitDemAdminKontoBleibtDerEintragMitNamenStehen() {
    long admin = konto("a@example.org", "ADMIN");
    long ziel = konto("x@example.org", "USER");
    vorschau("k1", admin, ziel);

    jdbc.update("DELETE FROM app_user WHERE id = ?", admin);

    assertThat(zeilen()).isEqualTo(1);
    assertThat(
            jdbc.queryForMap(
                "SELECT admin_id, admin_name FROM admin_vorschau WHERE kennung = 'k1'"))
        .containsEntry("admin_id", null)
        .containsEntry("admin_name", "Ada Admin");
  }

  @Test
  void dieKennungIstEindeutig() {
    long admin = konto("a@example.org", "ADMIN");
    long ziel = konto("x@example.org", "USER");
    vorschau("k1", admin, ziel);

    assertThatThrownBy(() -> vorschau("k1", admin, ziel))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void einUnbekannterEndgrundWirdAbgewiesen() {
    long admin = konto("a@example.org", "ADMIN");
    long ziel = konto("x@example.org", "USER");
    vorschau("k1", admin, ziel);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE admin_vorschau SET beendet_am = ?, endgrund = 'UNBEKANNT'", ABLAUF))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void endeOhneEndgrundWirdAbgewiesen() {
    long admin = konto("a@example.org", "ADMIN");
    long ziel = konto("x@example.org", "USER");
    vorschau("k1", admin, ziel);

    assertThatThrownBy(() -> jdbc.update("UPDATE admin_vorschau SET beendet_am = ?", ABLAUF))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dieTabelleTraegtIndizesAufZielUndKennung() {
    assertThat(
            jdbc.queryForList(
                "SELECT indexdef FROM pg_indexes WHERE tablename = 'admin_vorschau'", String.class))
        .anySatisfy(d -> assertThat(d).contains("(ziel_id)"))
        .anySatisfy(d -> assertThat(d).contains("UNIQUE").contains("(kennung)"));
  }
}
