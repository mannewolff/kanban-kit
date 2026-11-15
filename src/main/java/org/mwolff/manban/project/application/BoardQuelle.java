package org.mwolff.manban.project.application;

/**
 * Lesender Port: Liegt das Board eines Projekts in einem fremden System (Issue #2156, Plan #2152
 * A10)? Dann spiegelt kanban-kit die Karten nur, und jeder Schreibweg außer dem Spiegel lehnt ab.
 *
 * <p>Der Port liegt in {@code project}, umgesetzt wird er in {@code fremdboard.infrastructure} über
 * {@code fremdboard_verbindung}: {@code fremdboard} hängt am Modul {@code card}, ein Port dort oder
 * in {@code fremdboard} schlösse einen Modulzyklus, und eine Spalte an {@code project} wäre eine
 * zweite Quelle neben der Verbindung.
 *
 * <p>Prüft keine Rechte: Er gibt nur eine Eigenschaft des Projekts zurück, die jeder Schreibweg
 * nach seiner eigenen Rechteprüfung fragt.
 */
@FunctionalInterface
public interface BoardQuelle {

  /** {@code true} genau dann, wenn das Projekt eine Verbindung zu einem fremden Board hat. */
  boolean istFremd(long projectId);
}
