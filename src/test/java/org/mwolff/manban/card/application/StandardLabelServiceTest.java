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
  private static final int SATZ = 34;

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
  void derStandardsatzNenntJeSacheAlleNamenMitGruppeUndFarbeInFesterReihenfolge() {
    List<StandardLabel> satz = service.standardsatz(ADMIN);

    assertThat(satz)
        .extracting(StandardLabel::name)
        .containsExactly(
            "kit:durchziehen",
            "kit:carry-through",
            "kit:geschuetzt",
            "kit:protected",
            "kit:klaeren",
            "kit:clarify",
            "kit:geklaert",
            "kit:clarified",
            "kit:night",
            "kit:nightrun",
            "kit:pruefen",
            "kit:check",
            "kit:nightplan",
            "kit:nightreview",
            "kit:nightissues",
            "lauf:abgebrochen",
            "run:aborted",
            "lauf:laeuft",
            "run:running",
            "lauf:wartet",
            "run:waiting",
            "review:offen",
            "review:fertig",
            "review:done",
            "ziel:plan",
            "goal:plan",
            "ziel:pakete",
            "goal:packages",
            "ziel:umsetzung",
            "goal:implementation",
            "ziel:push-vorbereitet",
            "goal:push-prepared",
            "planreview:1",
            "planreview:2");
    assertThat(satz).hasSize(SATZ);
    assertThat(satz.get(0)).isEqualTo(new StandardLabel("kit:durchziehen", "Kit", "#6a1b9a"));
    assertThat(satz.get(16)).isEqualTo(new StandardLabel("run:aborted", "Lauf", "#1565c0"));
    assertThat(satz.get(23)).isEqualTo(new StandardLabel("review:done", "Review", "#2e7d32"));
    assertThat(satz.get(33))
        .isEqualTo(new StandardLabel("planreview:2", "Stufenleiste", "#ef6c00"));
  }

  @Test
  void jederNameEinerSacheTraegtGruppeUndFarbeDerSache() {
    List<StandardLabel> satz = service.standardsatz(ADMIN);

    for (Sache sache : Sache.values()) {
      List<StandardLabel> derSache =
          satz.stream().filter(l -> KitNamen.STANDARD.namen(sache).contains(l.name())).toList();
      assertThat(derSache)
          .extracting(StandardLabel::name)
          .isEqualTo(KitNamen.STANDARD.namen(sache));
      assertThat(derSache).extracting(StandardLabel::gruppe).containsOnly(derSache.get(0).gruppe());
      assertThat(derSache).extracting(StandardLabel::farbe).containsOnly(derSache.get(0).farbe());
    }
  }

  @Test
  void dasEnglischeGegenstueckStehtDirektHinterDemDeutschenNamen() {
    List<String> namen = service.standardsatz(ADMIN).stream().map(StandardLabel::name).toList();

    assertThat(namen.indexOf("kit:clarify")).isEqualTo(namen.indexOf("kit:klaeren") + 1);
    assertThat(namen.indexOf("run:waiting")).isEqualTo(namen.indexOf("lauf:wartet") + 1);
    assertThat(namen.indexOf("goal:push-prepared"))
        .isEqualTo(namen.indexOf("ziel:push-vorbereitet") + 1);
  }

  @Test
  void dasPaarGeklaertStehtInDerGruppeKitDirektHinterKitClarify() {
    List<StandardLabel> satz = service.standardsatz(ADMIN);
    List<String> namen = satz.stream().map(StandardLabel::name).toList();

    int geklaert = namen.indexOf("kit:geklaert");
    assertThat(geklaert).isEqualTo(namen.indexOf("kit:clarify") + 1);
    assertThat(namen.indexOf("kit:clarified")).isEqualTo(geklaert + 1);
    assertThat(satz.get(geklaert)).isEqualTo(new StandardLabel("kit:geklaert", "Kit", "#6a1b9a"));
    assertThat(satz.get(geklaert + 1))
        .isEqualTo(new StandardLabel("kit:clarified", "Kit", "#6a1b9a"));
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
    verify(labels).insertIfAbsent(11L, "ziel:umsetzung", "#ef6c00");
    verify(labels, times(2 * SATZ)).insertIfAbsent(anyLong(), anyString(), anyString());
  }

  @Test
  void einVorhandenesLabelZaehltAlsUebersprungen() {
    when(boardService.listAllActiveBoards()).thenReturn(List.of(board(10L)));
    when(labels.insertIfAbsent(anyLong(), anyString(), anyString())).thenReturn(true);
    when(labels.insertIfAbsent(10L, "kit:night", "#6a1b9a")).thenReturn(false);
    when(labels.insertIfAbsent(10L, "review:fertig", "#2e7d32")).thenReturn(false);

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
