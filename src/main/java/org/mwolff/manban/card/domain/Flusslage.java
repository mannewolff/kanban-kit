package org.mwolff.manban.card.domain;

import java.util.Optional;
import org.jspecify.annotations.Nullable;

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
    return von(card.boardId(), card.status(), card.archived(), imPapierkorb);
  }

  /**
   * Dieselbe Regel über die einzelnen Spalten der Karte — für die Erfassung, die den Stand per JDBC
   * liest (Issue #1768, E2) und keine {@link Card} baut.
   */
  public static Optional<Flusslage> von(
      long boardId, @Nullable CardStatus status, boolean archived, boolean imPapierkorb) {
    if (status == null || imPapierkorb || (archived && status != CardStatus.DONE)) {
      return Optional.empty();
    }
    return Optional.of(new Flusslage(boardId, status));
  }
}
