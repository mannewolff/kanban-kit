package org.mwolff.manban.fremdboard.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Herkunft einer Karte aus ihrem Text (Issue #2158, Plan #2152 A12): Das fremde System kennt kein
 * {@code derived_from}, das Kit schreibt aber die Zeilen {@code Fachliche Quelle: Issue #N} und
 * {@code Plan: Issue #N} in jedes Dokument.
 *
 * <p>Gelesen wird nach der Zeilenregel von {@code herkunftNummern} im Kit: nur eine ganze Zeile,
 * führender Leerraum erlaubt, der Feldname in beliebiger Schreibung und unter beiden Namen, hinter
 * der Nummer nur Leerraum — so trennt das Zeilenende #30 von #300, und eine Erwähnung im Fließtext
 * zählt nicht. Führende Nullen fallen weg. Beim Plan darf {@code Issue} fehlen ({@code Plan: #N}).
 * Gilt die erste passende Zeile.
 */
public final class HerkunftAusText {

  private static final Pattern FACHLICHE_QUELLE_ZEILE =
      Pattern.compile(
          "\\s*(?:Fachliche Quelle|Requirement):\\s*Issue\\s*#0*(\\d{1,18})\\s*",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private static final Pattern PLAN_ZEILE =
      Pattern.compile(
          "\\s*Plan:\\s*(?:Issue\\s*)?#0*(\\d{1,18})\\s*",
          Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

  private HerkunftAusText() {}

  /** Die Nummer aus {@code Fachliche Quelle: Issue #N} (englisch {@code Requirement:}). */
  public static Optional<Long> fachlicheQuelle(@Nullable String text) {
    return ersteNummer(text, FACHLICHE_QUELLE_ZEILE);
  }

  /** Die Nummer aus {@code Plan: Issue #N} oder {@code Plan: #N}. */
  public static Optional<Long> plan(@Nullable String text) {
    return ersteNummer(text, PLAN_ZEILE);
  }

  private static Optional<Long> ersteNummer(@Nullable String text, Pattern zeile) {
    if (text == null) {
      return Optional.empty();
    }
    return text.lines()
        .map(zeile::matcher)
        .filter(Matcher::matches)
        .findFirst()
        .map(m -> Long.parseLong(m.group(1)));
  }
}
