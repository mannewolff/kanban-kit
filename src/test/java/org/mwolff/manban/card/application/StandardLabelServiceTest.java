package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.card.application.StandardLabelService.Ergebnis;
import org.mwolff.manban.card.application.StandardLabelService.StandardLabel;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;

/** Der Standardsatz der Labels und sein Anlegen auf allen Boards (Issue #1485), Ports gemockt. */
class StandardLabelServiceTest {

  private static final long ADMIN = 1L;
  private static final long NUTZER = 2L;
  private static final int SATZ = 22;

  private LabelRepository labels;
  private BoardService boardService;
  private StandardLabelService service;

  @BeforeEach
  void setUp() {
    labels = mock(LabelRepository.class);
    boardService = mock(BoardService.class);
    PlatformAdminChecker admins = mock(PlatformAdminChecker.class);
    when(admins.isPlatformAdmin(ADMIN)).thenReturn(true);
    service = new StandardLabelService(labels, boardService, admins);
  }

  @Test
  void derStandardsatzNenntJeSacheIhrenEnglischenNamenMitGruppeUndFarbeInFesterReihenfolge() {
    List<StandardLabel> satz = service.standardsatz(ADMIN);

    assertThat(satz)
        .extracting(StandardLabel::name)
        .containsExactly(
            "kit:carry-through",
            "kit:protected",
            "kit:clarify",
            "kit:clarified",
            "kit:night",
            "kit:nightrun",
            "kit:check",
            "kit:nightplan",
            "kit:nightreview",
            "kit:nightissues",
            "kit:on-hold",
            "run:aborted",
            "run:running",
            "run:waiting",
            "review:offen",
            "review:done",
            "goal:plan",
            "goal:packages",
            "goal:implementation",
            "goal:push-prepared",
            "planreview:1",
            "planreview:2");
    assertThat(satz).hasSize(SATZ);
    assertThat(satz.get(0)).isEqualTo(new StandardLabel("kit:carry-through", "Kit", "#6a1b9a"));
    assertThat(satz.get(10)).isEqualTo(new StandardLabel("kit:on-hold", "Kit", "#6a1b9a"));
    assertThat(satz.get(11)).isEqualTo(new StandardLabel("run:aborted", "Lauf", "#1565c0"));
    assertThat(satz.get(15)).isEqualTo(new StandardLabel("review:done", "Review", "#2e7d32"));
    assertThat(satz.get(21))
        .isEqualTo(new StandardLabel("planreview:2", "Stufenleiste", "#ef6c00"));
  }

  @Test
  void jedeSacheStehtGenauEinmalMitIhremLetztenNamenDa() {
    List<StandardLabel> satz = service.standardsatz(ADMIN);

    for (Sache sache : Sache.values()) {
      List<String> namen = KitNamen.STANDARD.namen(sache);
      assertThat(satz)
          .extracting(StandardLabel::name)
          .filteredOn(namen::contains)
          .containsExactly(namen.get(namen.size() - 1));
    }
  }

  @Test
  void keinerDerVierzehnDeutschenNamenStehtImSatz() {
    List<String> namen = service.standardsatz(ADMIN).stream().map(StandardLabel::name).toList();

    assertThat(namen)
        .doesNotContainAnyElementsOf(
            List.of(
                "kit:klaeren",
                "kit:geschuetzt",
                "kit:durchziehen",
                "kit:pruefen",
                "kit:geklaert",
                "kit:wartet",
                "lauf:laeuft",
                "lauf:abgebrochen",
                "lauf:wartet",
                "review:fertig",
                "ziel:plan",
                "ziel:pakete",
                "ziel:umsetzung",
                "ziel:push-vorbereitet"));
  }

  @Test
  void einDeutscherNameWirdWeiterAlsSeineSacheErkannt() {
    assertThat(KitNamen.STANDARD.sache("kit:klaeren")).contains(Sache.KLAEREN);
  }

  @Test
  void kitNightplanErscheintGenauEinmalUndOhneGegenstueck() {
    List<String> namen = service.standardsatz(ADMIN).stream().map(StandardLabel::name).toList();

    assertThat(namen).containsOnlyOnce("kit:nightplan");
    assertThat(namen.get(namen.indexOf("kit:nightplan") + 1)).isEqualTo("kit:nightreview");
    assertThat(KitNamen.STANDARD.namen(Sache.NIGHTPLAN)).containsExactly("kit:nightplan");
  }

  @Test
  void legtAufJedemAktivenBoardJedesLabelDesSatzesAn() {
    when(boardService.listAllActiveBoards()).thenReturn(List.of(board(10L), board(11L)));
    when(labels.insertIfAbsent(anyLong(), anyString(), anyString())).thenReturn(true);

    Ergebnis ergebnis = service.aufAlleBoardsAnlegen(ADMIN);

    assertThat(ergebnis).isEqualTo(new Ergebnis(2, 2 * SATZ, 0));
    verify(labels).insertIfAbsent(10L, "kit:night", "#6a1b9a");
    verify(labels).insertIfAbsent(11L, "goal:implementation", "#ef6c00");
    verify(labels, times(2 * SATZ)).insertIfAbsent(anyLong(), anyString(), anyString());
  }

  @Test
  void einVorhandenesLabelZaehltAlsUebersprungen() {
    when(boardService.listAllActiveBoards()).thenReturn(List.of(board(10L)));
    when(labels.insertIfAbsent(anyLong(), anyString(), anyString())).thenReturn(true);
    when(labels.insertIfAbsent(10L, "kit:night", "#6a1b9a")).thenReturn(false);
    when(labels.insertIfAbsent(10L, "review:done", "#2e7d32")).thenReturn(false);

    Ergebnis ergebnis = service.aufAlleBoardsAnlegen(ADMIN);

    assertThat(ergebnis).isEqualTo(new Ergebnis(1, SATZ - 2, 2));
  }

  /**
   * Ein gleichzeitiger zweiter Aufruf stößt auf die Eindeutigkeitsbedingung; {@code insertIfAbsent}
   * meldet das als {@code false}, und der Lauf zählt das Label als übersprungen, statt abzubrechen.
   */
  @Test
  void einZusammenstossMitDerEindeutigkeitsbedingungZaehltAlsUebersprungenOhneFehler() {
    when(boardService.listAllActiveBoards()).thenReturn(List.of(board(10L), board(11L)));
    when(labels.insertIfAbsent(anyLong(), anyString(), anyString())).thenReturn(false);

    Ergebnis ergebnis = service.aufAlleBoardsAnlegen(ADMIN);

    assertThat(ergebnis).isEqualTo(new Ergebnis(2, 0, 2 * SATZ));
  }

  @Test
  void ohneAktiveBoardsWirdNichtsAngelegt() {
    when(boardService.listAllActiveBoards()).thenReturn(List.of());

    assertThat(service.aufAlleBoardsAnlegen(ADMIN)).isEqualTo(new Ergebnis(0, 0, 0));
    verify(labels, never()).insertIfAbsent(anyLong(), anyString(), anyString());
  }

  @Test
  void einNichtAdminDarfDenSatzNichtLesen() {
    assertThatThrownBy(() -> service.standardsatz(NUTZER))
        .isInstanceOf(AdminAccessDeniedException.class);
  }

  @Test
  void einNichtAdminLegtNichtsAn() {
    assertThatThrownBy(() -> service.aufAlleBoardsAnlegen(NUTZER))
        .isInstanceOf(AdminAccessDeniedException.class);
    verify(boardService, never()).listAllActiveBoards();
    verify(labels, never()).insertIfAbsent(anyLong(), anyString(), anyString());
  }

  private static BoardSummary board(long id) {
    return new BoardSummary(id, "Board " + id, false);
  }
}
