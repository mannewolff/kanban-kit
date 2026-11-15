package org.mwolff.manban.card.application;

import java.util.List;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.CardStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Was das Anbinden eines fremden Boards über die Karten eines Projekts wissen muss (Issue #2166,
 * Plan #2152 A2, A18): ob es schon Karten hat und wo, und ob ein Board für jeden Status eine Spalte
 * hat. Liest nur; geschrieben wird über {@link FremdKartenSchreiber}.
 */
@Service
public class FremdKartenBestand {

  private final CardRepository cards;
  private final CardBestandRepository bestandsAuskunft;
  private final BoardService boardService;

  public FremdKartenBestand(
      CardRepository cards, CardBestandRepository bestand, BoardService boardService) {
    this.cards = cards;
    this.bestandsAuskunft = bestand;
    this.boardService = boardService;
  }

  /**
   * Die Karten des Projekts nach ihrem Ort (A2): Anbinden geht nur, wenn das Projekt keine einzige
   * hat. Sperrt die Kartennummern des Projekts bis zum Ende der Transaktion, damit zwischen Prüfung
   * und Anbinden keine Karte entsteht.
   */
  @Transactional
  public KartenBestand bestand(long projectId) {
    cards.lockCardNumbers(projectId);
    return bestandsAuskunft.bestand(projectId);
  }

  /**
   * Die Status, für die das Board keine Spalte hat, in der Reihenfolge des Prozesses (A18) — ein
   * Board, das Spiegel-Board werden soll, braucht für jeden eine.
   */
  @Transactional(readOnly = true)
  public List<String> fehlendeStatus(long boardId) {
    List<ColumnView> spalten = boardService.listColumns(boardId);
    return List.of(CardStatus.values()).stream()
        .filter(
            status ->
                spalten.stream()
                    .noneMatch(
                        spalte ->
                            Arbeitspaket.statusVonSpalte(spalte.name())
                                .filter(status::equals)
                                .isPresent()))
        .map(CardStatus::anzeigename)
        .toList();
  }

  /**
   * Die Karten eines Projekts nach ihrem Ort.
   *
   * @param aufBoards aktive Karten, auch Ideen mit dem Präfix des Kits
   * @param archiviert archivierte Karten
   * @param papierkorb Karten und Vorhaben im Papierkorb
   * @param vorhaben Vorhaben außerhalb des Papierkorbs
   */
  public record KartenBestand(int aufBoards, int archiviert, int papierkorb, int vorhaben) {

    /** Ob das Projekt keine einzige Karte hat. */
    public boolean leer() {
      return aufBoards + archiviert + papierkorb + vorhaben == 0;
    }
  }
}
