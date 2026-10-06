package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;

/**
 * Der Status einer Karte, die in einer Spalte ankommt (Issue #1474): Eine eigene Spalte übernimmt
 * den Status der nächsten Prozessspalte links davon — bei jeder Verschiebung, auch über einen von
 * Hand gesetzten Status hinweg. Vorhaben und Dokumentarten tragen keinen Status.
 */
class KartenStatusTest {

  private static final Instant FIXED = Instant.parse("2026-10-06T08:00:00Z");

  /** Board `Backlog | X | Y | Ready | In progress`, Ziel ist Y. */
  private static final List<String> BIS_Y = List.of("Backlog", "X", "Y");

  @Test
  void readyNachEigenerSpalteZwischenBacklogUndReady_wirdBacklog() {
    assertThat(KartenStatus.statusIn(paket("Paket", CardStatus.READY), BIS_Y))
        .isEqualTo(CardStatus.BACKLOG);
  }

  @Test
  void backlogNachEigenerSpalte_bleibtBacklog() {
    assertThat(KartenStatus.statusIn(paket("Paket", CardStatus.BACKLOG), List.of("Backlog", "X")))
        .isEqualTo(CardStatus.BACKLOG);
  }

  @Test
  void vonHandGesetzterStatus_wirdUeberschrieben() {
    assertThat(KartenStatus.statusIn(paket("Paket", CardStatus.IN_PROGRESS), BIS_Y))
        .isEqualTo(CardStatus.BACKLOG);
  }

  @Test
  void prozessspalte_gibtIhrenStatusVor() {
    assertThat(
            KartenStatus.statusIn(
                paket("Paket", CardStatus.BACKLOG), List.of("Backlog", "X", "Ready")))
        .isEqualTo(CardStatus.READY);
  }

  @Test
  void dokumentarten_bleibenOhneStatus() {
    assertThat(KartenStatus.statusIn(paket("[Fachlich] Anforderung", null), BIS_Y)).isNull();
    assertThat(KartenStatus.statusIn(paket("[Plan] Plan", null), BIS_Y)).isNull();
    assertThat(KartenStatus.statusIn(paket("[Idee] Idee", null), BIS_Y)).isNull();
  }

  @Test
  void vorhaben_bleibtOhneStatus() {
    assertThat(KartenStatus.statusIn(karte("Vorhaben", CardType.EPIC, null), BIS_Y)).isNull();
  }

  @Test
  void inSpalte_setztStatusNachLageUndDoneStempelNachZielspalte() {
    Card angekommen =
        KartenStatus.inSpalte(
            paket("Paket", CardStatus.READY),
            List.of("Backlog", "Ready", "Done", "Abgelegt"),
            FIXED);

    assertThat(angekommen.status()).isEqualTo(CardStatus.DONE);
    assertThat(angekommen.movedToDoneAt()).isEqualTo(FIXED);
  }

  @Test
  void bis_nenntDieSpaltenVonLinksBisEinschliesslichZiel() {
    List<ColumnView> board =
        List.of(spalte(1L, "Backlog"), spalte(2L, "X"), spalte(3L, "Y"), spalte(4L, "Ready"));

    assertThat(KartenStatus.bis(board, spalte(3L, "Y"))).containsExactly("Backlog", "X", "Y");
  }

  @Test
  void bis_ohneZielspalteInDerListe_zaehltNurDieZielspalte() {
    assertThat(KartenStatus.bis(List.of(spalte(1L, "Backlog")), spalte(9L, "Anstehend")))
        .containsExactly("Anstehend");
  }

  @Test
  void bis_laesstEinenFehlendenSpaltennamenStehen() {
    assertThat(KartenStatus.bis(List.of(), spalte(9L, null))).containsExactly((String) null);
  }

  private static ColumnView spalte(long id, @Nullable String name) {
    return new ColumnView(id, name, 0, null);
  }

  private static Card paket(String titel, @Nullable CardStatus status) {
    return karte(titel, CardType.CARD, status);
  }

  private static Card karte(String titel, CardType type, @Nullable CardStatus status) {
    return new Card(
        1L, 10L, 20L, 5, titel, null, 0, false, null, 1L, FIXED, FIXED, type, null, null, null, 1L,
        null, null, null, status);
  }
}
