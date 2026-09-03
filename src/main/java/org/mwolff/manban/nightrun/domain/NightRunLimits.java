package org.mwolff.manban.nightrun.domain;

/**
 * Längengrenzen der Nachtlauf-Auswertung (Plan #718, A16).
 *
 * <p>Die Zahl lebt je Sprache an genau einem Ort: hier und als {@code NIGHT_RUN_EXCERPT_MAX} in
 * {@code frontend/src/lib/nightRunLog.ts}. {@code NightRunErrorClassSyncTest} hält beide samt der
 * Spaltenlänge in {@code V29__night_run.sql} gleich. Liefen sie auseinander, antwortete ein Auszug
 * knapp über der Spaltengrenze mit 500 statt 400 — der {@code GlobalExceptionHandler} bildet nur
 * SQLState 23505 auf 409 ab, eine verletzte Spaltenzusicherung bleibt ein Serverfehler.
 *
 * <p><strong>Zählweise</strong> wie in {@code TextLimits}: eine UTF-16-Codeeinheit ist ein Zeichen
 * — die Einheit, die {@code String.length()} und {@code String.prototype.length} teilen und die
 * {@code @Size} ohnehin prüft. PostgreSQL zählt bei {@code varchar(n)} Codepoints und ist damit nie
 * enger als die Anwendung.
 */
public final class NightRunLimits {

  /**
   * Obergrenze für gespeicherte Protokollauszüge — {@code excerpt} je Arbeitspaket und {@code
   * unparsedSample} je Lauf.
   */
  public static final int EXCERPT_MAX = 4000;

  private NightRunLimits() {}
}
