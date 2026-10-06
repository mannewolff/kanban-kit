package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;

/**
 * Der Status einer Karte, die in einer Spalte ankommt (Plan #1294, Issue #1474): Eine Prozessspalte
 * gibt ihren Status vor, eine eigene Spalte den der nächsten Prozessspalte links davon, ohne eine
 * solche {@code BACKLOG} — bei jedem Verschieben, Anlegen und Übertragen, auch über einen von Hand
 * gesetzten Status hinweg. Vorhaben und Dokumentarten tragen keinen.
 *
 * <p>Eigene Klasse statt in {@link KartenGrundlage}: Die Regel braucht die Lage der Zielspalte auf
 * ihrem Board, und mit diesem Eingang wüchse {@code KartenGrundlage} über die Kopplungsgrenze von
 * PMD ({@code CouplingBetweenObjects}).
 */
final class KartenStatus {

  private KartenStatus() {}

  /**
   * Die Karte, wie sie in der letzten der genannten Spalten ankommt: Status nach {@link #statusIn},
   * Done-Zeitstempel nach {@link KartenGrundlage#doneStempel} für die Zielspalte. Gemeinsamer Weg
   * von Anlegen und Übertragen, damit beide dieselbe Regel sprechen.
   *
   * @param spaltenBisZiel die Spaltennamen des Boards von links bis einschließlich der Zielspalte,
   *     siehe {@link #bis}; nie leer — enthält mindestens die Zielspalte
   */
  static Card inSpalte(Card card, List<String> spaltenBisZiel, Instant now) {
    Card mitStatus = card.withStatus(statusIn(card, spaltenBisZiel));
    String ziel = spaltenBisZiel.getLast();
    return mitStatus.withMovedToDoneAt(KartenGrundlage.doneStempel(mitStatus, ziel, now));
  }

  /**
   * Der Status nach der Lage der Zielspalte; {@code null} für Vorhaben und Dokumentarten.
   *
   * @param spaltenBisZiel die Spaltennamen des Boards von links bis einschließlich der Zielspalte
   */
  static @Nullable CardStatus statusIn(Card card, List<String> spaltenBisZiel) {
    if (!Arbeitspaket.istArbeitspaket(card.type(), card.title())) {
      return null;
    }
    return Arbeitspaket.statusNachLage(spaltenBisZiel);
  }

  /**
   * Die Spaltennamen bis einschließlich der Zielspalte, in der Reihenfolge des Boards. Steht die
   * Zielspalte nicht in der Liste, zählt sie allein. Ein fehlender Spaltenname (null) bleibt
   * stehen; {@code Arbeitspaket.statusVonSpalte} fängt ihn ab.
   *
   * @param spalten die Spalten des Boards, aufsteigend nach Position
   */
  static List<String> bis(List<ColumnView> spalten, ColumnView ziel) {
    for (int i = 0; i < spalten.size(); i++) {
      if (spalten.get(i).id().equals(ziel.id())) {
        return namen(spalten.subList(0, i + 1));
      }
    }
    return namen(List.of(ziel));
  }

  private static List<String> namen(List<ColumnView> spalten) {
    return spalten.stream().map(ColumnView::name).toList();
  }
}
