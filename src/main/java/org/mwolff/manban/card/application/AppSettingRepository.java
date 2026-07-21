package org.mwolff.manban.card.application;

import java.util.Optional;

/**
 * Port für die generische Key-Value-Persistenz globaler App-Einstellungen (Tabelle {@code
 * app_setting}). Werte werden als Strings gehalten; die fachliche Deutung (z. B. Integer für den
 * Done-Retention-Override) liegt beim aufrufenden Service.
 */
public interface AppSettingRepository {

  /** Liefert den Wert zum Schlüssel, sofern gesetzt. */
  Optional<String> find(String key);

  /** Legt den Wert an oder überschreibt ihn (Upsert; der Schlüssel ist der Primärschlüssel). */
  void save(String key, String value);
}
