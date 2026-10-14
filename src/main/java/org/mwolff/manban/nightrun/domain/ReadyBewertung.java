package org.mwolff.manban.nightrun.domain;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Die Bewertung der Ready-Karten eines Projekts, wie der Runner sie meldet (Issue #1726, Plan #1724
 * E7, Vertrag V2).
 *
 * <p>Das Board bildet die Regel nicht nach, ob ein Paket drankommt — sie steht allein im Runner
 * ({@code pruefeIssueGates}). Er meldet stattdessen sein Ergebnis, und je Projekt gilt die jüngste
 * Meldung. So gibt es eine Wahrheit über das Dran-Sein (#191).
 *
 * @param assessedAt wann der Runner bewertet hat
 * @param runStartedAt der Lauf, der bewertet hat; {@code null} bei einem Probelauf ({@code
 *     --dry-run})
 * @param label das Label, nach dem der Runner Ready-Karten auswählt, etwa {@code kit:nightrun}
 * @param entries je bewerteter Karte ein Eintrag, in der Reihenfolge der Meldung
 */
public record ReadyBewertung(
    Instant assessedAt, @Nullable Instant runStartedAt, String label, List<Eintrag> entries) {

  public ReadyBewertung {
    entries = List.copyOf(entries);
  }

  /**
   * Eine bewertete Karte.
   *
   * @param cardNumber projektweite Nummer der Karte
   * @param position Platz unter den startbaren Karten in Board-Reihenfolge, ab 1; {@code null} bei
   *     einer Karte mit Grund (Plan #1724 E11)
   * @param reasons die Gründe, aus denen der Runner die Karte zurückstellt oder nicht zieht; leer
   *     heißt „kein Hinderungsgrund“
   */
  public record Eintrag(int cardNumber, @Nullable Integer position, List<Grund> reasons) {

    public Eintrag {
      reasons = List.copyOf(reasons);
    }
  }

  /**
   * Ein Grund des Runners. {@code code} ist eine Zeichenkette und kein Enum: Ein neueres Kit mit
   * einem weiteren Grund soll seine Bewertung nicht verlieren; die Anzeige zeigt dann {@code text}.
   *
   * @param code heute {@code NO_LABEL}, {@code NOT_A_WORK_PACKAGE}, {@code CLARIFY}, {@code
   *     PROTECTED_LABEL}, {@code PROTECTED_FILE}, {@code REVIEW_MISSING} oder {@code DEPENDENCY}
   * @param kind bei {@code NOT_A_WORK_PACKAGE} die Art der Karte, etwa {@code PLAN}; sonst {@code
   *     null}
   * @param cardNumbers bei {@code DEPENDENCY} die Karten, auf die gewartet wird; sonst leer
   * @param paths bei {@code PROTECTED_FILE} die geschützten Pfade; sonst leer
   * @param text der Wortlaut des Runners
   */
  public record Grund(
      String code,
      @Nullable String kind,
      List<Integer> cardNumbers,
      List<String> paths,
      String text) {

    public Grund {
      cardNumbers = List.copyOf(cardNumbers);
      paths = List.copyOf(paths);
    }
  }
}
