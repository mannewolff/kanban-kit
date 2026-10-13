package org.mwolff.manban.common.i18n;

/**
 * Port: die Sprache der Installation, wie sie gerade gilt (Issue #1639, Plan #1638 A3).
 *
 * <p>Liegt in {@code common}, damit Fachmodule die Sprache erfahren, ohne das Modul {@code
 * language} zu kennen — das hängt an {@code auth}, und die direkte Kante schlösse einen
 * Modulzyklus. Die Antwort ist nie leer und wirft nicht: Fehlt der Wert oder ist er nicht lesbar,
 * gilt Deutsch.
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — das Interface ist ein Port
// mit Adapter in language.infrastructure, kein Lambda-Ziel.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface SpracheQuelle {

  /** Die geltende Sprache; ohne gespeicherten oder lesbaren Wert {@link Sprache#DE}. */
  Sprache aktuelle();
}
