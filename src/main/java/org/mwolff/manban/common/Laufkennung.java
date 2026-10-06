package org.mwolff.manban.common;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;

/**
 * Laufkennung eines Nachtlaufs (Issue #1426, Plan #1423): Der Runner schickt den {@code startedAt}
 * seines Laufs als ISO-8601-Instant im Header {@link #HEADER} mit. Der Wert ist eine
 * Client-Selbstauskunft wie {@code X-Agent-Model} — er ordnet Spuren einem Lauf zu, verifiziert
 * aber nichts.
 *
 * <p>Modulübergreifend, weil {@code card} und {@code comment} getrennte Module sind und denselben
 * Header lesen.
 */
public final class Laufkennung {

  /** Header-Name, abgeglichen mit Kit-Karte 1194 (Issue #1425). */
  public static final String HEADER = "X-Night-Run";

  private static final int MAX_LENGTH = 40;

  private Laufkennung() {}

  /**
   * Liest die Laufkennung aus dem Header-Wert: getrimmt, höchstens 40 Zeichen, als Instant geparst
   * und auf Millisekunden gekürzt. Fehlt der Wert, ist er leer, zu lang oder kein Instant, ist das
   * Ergebnis {@code null}.
   */
  public static @Nullable Instant ausHeader(@Nullable String wert) {
    if (wert == null) {
      return null;
    }
    String trimmed = wert.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
      return null;
    }
    try {
      return Instant.parse(trimmed).truncatedTo(ChronoUnit.MILLIS);
    } catch (DateTimeException e) {
      return null;
    }
  }
}
