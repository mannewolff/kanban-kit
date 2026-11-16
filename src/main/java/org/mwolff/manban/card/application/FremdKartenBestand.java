package org.mwolff.manban.card.application;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.Card;
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
   * Die interne ID der Karte mit dieser Nummer im Projekt (Issue #2167); leer, wenn der Spiegel sie
   * noch nicht angelegt hat oder sie im Papierkorb liegt.
   */
  @Transactional(readOnly = true)
  public Optional<Long> kartenId(long projectId, int nummer) {
    return cards.findByProjectIdAndNumber(projectId, nummer).map(Card::requireId);
  }

  /**
   * Die Nummern der gespiegelten Karten des Projekts außerhalb des Papierkorbs (Issue #2168, Plan
   * #2152 A8) — der Vollabgleich vergleicht sie mit den Nummern im fremden System.
   */
  @Transactional(readOnly = true)
  public List<Integer> nummern(long projectId) {
    return cards.findByProjectId(projectId).stream()
        .map(Card::number)
        .filter(Objects::nonNull)
        .toList();
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
