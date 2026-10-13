package org.mwolff.manban.language.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.common.i18n.Sprache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Sprache der Installation in der Tabelle {@code app_setting} (Issue #1639, Plan #1638 A3/A4):
 * Upsert auf den Schlüssel {@code ui.language}, Lesen und der Rückfall auf Deutsch.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AppSettingSpracheAdapterIT extends AbstractIntegrationTest {

  @Autowired private AppSettingSpracheAdapter adapter;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void ohneZeile_lesenIstLeerUndAktuelleIstDeutsch() {
    assertThat(adapter.lesen()).isEmpty();
    assertThat(adapter.aktuelle()).isEqualTo(Sprache.DE);
  }

  @Test
  void schreiben_legtDieZeileAnUndLesenLiefertSie() {
    adapter.schreiben(Sprache.EN);

    assertThat(adapter.lesen()).contains(Sprache.EN);
    assertThat(adapter.aktuelle()).isEqualTo(Sprache.EN);
    assertThat(wert()).isEqualTo("en");
  }

  @Test
  void schreiben_ueberschreibtDieVorhandeneZeile() {
    adapter.schreiben(Sprache.EN);
    adapter.schreiben(Sprache.DE);

    assertThat(adapter.lesen()).contains(Sprache.DE);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM app_setting WHERE setting_key = 'ui.language'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void unbekannterGespeicherterWert_aktuelleFaelltAufDeutschZurueck() {
    jdbc.update(
        "INSERT INTO app_setting (setting_key, setting_value) VALUES ('ui.language', 'fr')");

    assertThat(adapter.lesen()).isEmpty();
    assertThat(adapter.aktuelle()).isEqualTo(Sprache.DE);
  }

  @Test
  void aktuelle_liefertDeutsch_beiDataAccessException() {
    // Jeder Aufruf scheitert, gleich welche Methode der Adapter wählt.
    JdbcTemplate kaputt =
        mock(
            JdbcTemplate.class,
            aufruf -> {
              throw new DataAccessResourceFailureException("Datenbank weg");
            });

    assertThat(new AppSettingSpracheAdapter(kaputt).aktuelle()).isEqualTo(Sprache.DE);
  }

  private String wert() {
    return jdbc.queryForObject(
        "SELECT setting_value FROM app_setting WHERE setting_key = 'ui.language'", String.class);
  }
}
