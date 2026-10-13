package org.mwolff.manban.language.infrastructure;

import java.util.Arrays;
import java.util.Optional;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.SpracheQuelle;
import org.mwolff.manban.language.application.SpracheSpeicher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Die Sprache der Installation in der Tabelle {@code app_setting}, Schlüssel {@value #KEY} (Issue
 * #1639, Plan #1638 A3/A4).
 *
 * <p>Bewusst JDBC statt einer zweiten JPA-Entity derselben Tabelle: Die Entity gehört dem Modul
 * {@code card}, und zwei Entities über eine Tabelle teilten sich still den Persistenzkontext. Die
 * Tabelle besteht seit V17; eine Migration braucht es nicht, denn eine fehlende Zeile heißt
 * Deutsch.
 */
@Component
public class AppSettingSpracheAdapter implements SpracheSpeicher, SpracheQuelle {

  static final String KEY = "ui.language";

  private static final Logger LOG = LoggerFactory.getLogger(AppSettingSpracheAdapter.class);

  private static final String SELECT =
      "SELECT setting_value FROM app_setting WHERE setting_key = ?";

  private static final String UPSERT =
      "INSERT INTO app_setting (setting_key, setting_value) VALUES (?, ?)"
          + " ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value";

  private final JdbcTemplate jdbc;

  public AppSettingSpracheAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /** {@inheritDoc} Ein gespeicherter, aber unbekannter Wert gilt als nicht gesetzt. */
  @Override
  public Optional<Sprache> lesen() {
    return jdbc.queryForList(SELECT, String.class, KEY).stream()
        .findFirst()
        .flatMap(AppSettingSpracheAdapter::bekannteSprache);
  }

  @Override
  public void schreiben(Sprache sprache) {
    jdbc.update(UPSERT, KEY, sprache.code());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Fängt jede {@link DataAccessException}: Die Sprache bestimmt nur die Darstellung, und eine
   * nicht erreichbare Datenbank soll keine Fehlermeldung verhindern, die gerade davon berichtet.
   */
  @Override
  public Sprache aktuelle() {
    try {
      return lesen().orElse(Sprache.DE);
    } catch (DataAccessException e) {
      LOG.warn("Sprache der Installation nicht lesbar — es gilt Deutsch", e);
      return Sprache.DE;
    }
  }

  private static Optional<Sprache> bekannteSprache(String code) {
    return Arrays.stream(Sprache.values()).filter(s -> s.code().equals(code)).findFirst();
  }
}
