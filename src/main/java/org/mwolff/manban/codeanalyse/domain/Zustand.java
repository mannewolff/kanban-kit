package org.mwolff.manban.codeanalyse.domain;

/**
 * Ein Zustand einer Messung: das Urteil der Codeanalyse vor ihr oder über den veröffentlichten
 * Stand (Issue #2273, Fachplan #2271 „Begriffe“). Keine Zahl ist negativ.
 *
 * @param fehler Funde der Art Fehler
 * @param sicherheitsluecken Funde der Art Sicherheitslücke
 * @param wartbarkeitsmaengel Funde der Art Wartbarkeitsmangel
 * @param zeilen geänderte Zeilen
 */
public record Zustand(int fehler, int sicherheitsluecken, int wartbarkeitsmaengel, int zeilen) {

  public Zustand {
    nichtNegativ(fehler, "fehler");
    nichtNegativ(sicherheitsluecken, "sicherheitsluecken");
    nichtNegativ(wartbarkeitsmaengel, "wartbarkeitsmaengel");
    nichtNegativ(zeilen, "zeilen");
  }

  /** Die Summe der Funde aller drei Arten. */
  public long funde() {
    return (long) fehler + sicherheitsluecken + wartbarkeitsmaengel;
  }

  private static void nichtNegativ(int wert, String name) {
    if (wert < 0) {
      throw new IllegalArgumentException(name + " darf nicht negativ sein");
    }
  }
}
