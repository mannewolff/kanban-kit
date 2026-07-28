package org.mwolff.manban.project.application;

/**
 * Schreibender Port für die projektweite Startnummer {@code project.next_card_number} (Issue #463).
 * Damit setzt das card-Modul — dem die Kartennummerierung fachlich gehört — den Wert am
 * Projekt-Aggregat, ohne den Projekt-Persistenz-Port zu kennen.
 *
 * <p><strong>Sicherheitshinweis:</strong> Dieser Port prüft <strong>keine</strong> Rechte und
 * <strong>keine</strong> Plausibilität. Der Aufrufer <em>muss</em> vorher autorisiert haben —
 * verbindlich ist {@code Permission.PROJECT_EDIT}, wie in {@code
 * card.application.ProjectStartNumberService#setNextCardNumber} als erste Anweisung geprüft. Wer
 * diesen Port ohne vorgelagerte Prüfung aufruft, umgeht die Projekt-Rechtelogik vollständig.
 *
 * <p>Der Aufruferkreis ist deshalb nicht nur dokumentiert, sondern maschinell begrenzt: {@code
 * ArchitectureTest.NEXT_CARD_NUMBER_WRITER_HAT_AUFRUFER_WHITELIST} lässt ausschließlich {@code
 * project.application} (Port und Implementierung) sowie {@code card.application} (autorisierender
 * Aufrufer) zu. Ein weiterer Aufrufer ist damit eine bewusste Regeländerung, kein Versehen.
 */
@FunctionalInterface
public interface NextCardNumberWriter {

  /**
   * Setzt die nächste Kartennummer des Projekts. Ein unbekanntes Projekt ist ein No-Op (das
   * zugrundeliegende UPDATE trifft dann keine Zeile).
   */
  void setNextCardNumber(long projectId, int value);
}
