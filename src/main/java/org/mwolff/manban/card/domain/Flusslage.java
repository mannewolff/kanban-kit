package org.mwolff.manban.card.domain;

import java.util.Optional;

/**
 * Wo eine Karte im Fluss der Arbeit steht: auf welchem Board, in welchem Zustand (Plan #1765, E3).
 *
 * @param boardId Board, dessen Fluss die Karte zählt
 * @param status Zustand des Arbeitspakets — das Band, in dem es zählt
 */
public record Flusslage(long boardId, CardStatus status) {

  /**
   * Die Flusslage der Karte, leer, wenn sie nicht im Fluss ist.
   *
   * <p>Im Fluss ist genau eine Karte mit Zustand — {@code status} ist im Bestand nur an
   * Arbeitspaketen gesetzt (V46, {@link Arbeitspaket#istArbeitspaket}), Dokumente und Vorhaben
   * fallen damit ohne zweite Regel heraus. Der Papierkorb nimmt jede Karte heraus, auch eine
   * fertige (E5); das Archiv nur eine nicht fertige, denn ein Abschluss bleibt Geschichte (AK 16).
   *
   * @param imPapierkorb ob die Karte im Papierkorb liegt — {@link Card} trägt {@code deleted_at}
   *     nicht
   */
  public static Optional<Flusslage> von(Card card, boolean imPapierkorb) {
    CardStatus status = card.status();
    if (status == null || imPapierkorb || (card.archived() && status != CardStatus.DONE)) {
      return Optional.empty();
    }
    return Optional.of(new Flusslage(card.boardId(), status));
  }
}
