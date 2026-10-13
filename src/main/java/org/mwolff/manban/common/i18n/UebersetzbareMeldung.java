package org.mwolff.manban.common.i18n;

/**
 * Eine Meldung mit festem Text, die in der Sprache der Anfrage erscheint (Issue #1640, Plan #1638
 * A5).
 *
 * <p>Der Text steht nicht in der Klasse, sondern unter {@link #schluessel()} in {@code
 * i18n/messages_de.properties} und {@code i18n/messages_en.properties}; aufgelöst wird er über
 * {@link Texte}. Eine Exception, die dieses Interface trägt, liefert mit {@code getMessage()}
 * weiter den heutigen deutschen Text — {@code MessagesVollstaendigkeitTest} hält beide Fassungen
 * gleich.
 */
// PMD.ImplicitFunctionalInterface: bewusst KEIN @FunctionalInterface — Exceptions tragen das
// Interface, ein Lambda-Ziel ist es nicht.
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface UebersetzbareMeldung {

  /** Der Schlüssel des Texts in den Meldungsdateien. */
  String schluessel();

  /**
   * Die Argumente für die Platzhalter {@code {0}}, {@code {1}}, … des Texts; ohne Platzhalter leer.
   */
  default Object[] argumente() {
    return new Object[0];
  }
}
