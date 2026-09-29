package org.mwolff.manban.card.domain;

/**
 * Eigener Prozesszustand eines Arbeitspakets (Plan #1294, E1) — unabhängig von der Spalte, in der
 * die Karte liegt. Nur Arbeitspakete (siehe {@link Arbeitspaket#istArbeitspaket}) tragen einen
 * Status; bei Vorhaben und den drei Dokumentarten ist er {@code null}, und es zählt die Spalte
 * (E2).
 *
 * <p>Wie bei {@link CardType} ist der Konstantenname der gespeicherte Wert: {@code
 * CardRepositoryAdapter} liest ihn über {@code CardStatus.valueOf} zurück, und die Check-Bedingung
 * {@code chk_card_status} aus Migration {@code V46} zählt genau diese Namen auf.
 */
public enum CardStatus {
  BACKLOG("Backlog"),
  READY("Ready"),
  IN_PROGRESS("In progress"),
  IN_REVIEW("In review"),
  DONE("Done");

  private final String prozessname;

  CardStatus(String prozessname) {
    this.prozessname = prozessname;
  }

  /**
   * Der kanonische Prozessname — wortgleich zu {@code .claude/workflow.config.json} → {@code
   * columns} (E24). Nur so findet die Wirksamkeitsmessung über {@code spaltenZuordnung} einen
   * Schlüssel für ihn.
   */
  public String anzeigename() {
    return prozessname;
  }
}
