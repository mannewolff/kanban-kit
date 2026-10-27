package org.mwolff.manban.common;

/**
 * Eine Exception mit eigenem Problem-Typ (Issue #1918, Plan #1914 E6): Der {@code
 * GlobalExceptionHandler} setzt ihn als {@code type} im Problem-Format (RFC 9457), damit ein Client
 * die Ablehnung erkennt, ohne den übersetzten {@code detail} zu lesen.
 *
 * <p>Sie liegt aus demselben Grund wie {@link FieldScopedException} in {@code common} und nicht in
 * {@code common.web}: Die ArchUnit-Schichtenregel verbietet jedem {@code ..application..}-Paket den
 * Zugriff auf {@code ..web..}, und das Muster trifft auch {@code common.web}.
 */
// PMD.ImplicitFunctionalInterface: bewusst kein @FunctionalInterface — wie FieldScopedException
// wird sie ausschliesslich von Exception-Klassen implementiert.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface MitProblemTyp {

  /** Der Problem-Typ, eine relative URI-Referenz wie {@code vorschau-verborgen}. */
  String problemTyp();
}
