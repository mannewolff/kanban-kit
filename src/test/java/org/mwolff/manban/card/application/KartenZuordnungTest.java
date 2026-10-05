package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mwolff.manban.card.domain.CardActivityOrigin.SESSION;
import static org.mwolff.manban.card.domain.CardActivityOrigin.TOKEN;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;

/**
 * Verhaltenstests der Karten-Zuordnung (Zuständige und Labels) an ihren Ports (Issue #1051).
 *
 * <p>Eigene Unit-Suite und nicht nur über {@code CardService}: Die Klasse trägt seit dem Auszug aus
 * dem Service die Prüfregeln für Zuständige und Labels selbst. Als Unit-Test, weil PIT
 * ausschliesslich Unit-Tests misst.
 */
class KartenZuordnungTest {

  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final long KARTE = 7L;

  private CardAssigneeRepository assignees;
  private LabelRepository labels;
  private CardLabelRepository cardLabels;
  private PermissionChecker permissions;
  private KartenZuordnung zuordnung;

  private static Label label(long id, String name, String color, boolean countOnEpicTile) {
    return new Label(id, BOARD, name, color, countOnEpicTile);
  }

  @BeforeEach
  void setUp() {
    assignees = mock(CardAssigneeRepository.class);
    labels = mock(LabelRepository.class);
    cardLabels = mock(CardLabelRepository.class);
    permissions = mock(PermissionChecker.class);
    zuordnung = new KartenZuordnung(assignees, labels, cardLabels, permissions);
  }

  @Test
  void zustaendigeEinerKarteKommenVomPort() {
    when(assignees.findByCardId(KARTE)).thenReturn(List.of(3L, 4L));

    assertThat(zuordnung.zustaendigeVon(KARTE)).containsExactly(3L, 4L);
  }

  @Test
  void zustaendigeMehrererKartenKommenAusEinemSammelzugriff() {
    Set<Long> ids = Set.of(1L, 2L);
    when(assignees.findByCardIds(ids)).thenReturn(Map.of(1L, List.of(3L)));

    assertThat(zuordnung.zustaendigeJeKarte(ids)).containsExactly(Map.entry(1L, List.of(3L)));
    verify(assignees, never()).findByCardId(1L);
  }

  @Test
  void labelsEinerKarteKommenVomPort() {
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(20L, 21L));

    assertThat(zuordnung.labelsVon(KARTE)).containsExactly(20L, 21L);
  }

  @Test
  void labelsMehrererKartenKommenAusEinemSammelzugriff() {
    Set<Long> ids = Set.of(1L, 2L);
    when(cardLabels.findByCardIds(ids)).thenReturn(Map.of(2L, List.of(20L)));

    assertThat(zuordnung.labelsJeKarte(ids)).containsExactly(Map.entry(2L, List.of(20L)));
    verify(cardLabels, never()).findByCardId(2L);
  }

  @Test
  void ersetzeZustaendigeEntferntDuplikateUndSchreibt() {
    when(permissions.isRealProjectMember(3L, PROJECT)).thenReturn(true);

    zuordnung.ersetzeZustaendige(KARTE, PROJECT, List.of(3L, 3L));

    verify(assignees).replaceAssignees(KARTE, List.of(3L));
  }

  @Test
  void ersetzeZustaendigeLehntNichtMitgliedAb() {
    when(permissions.isRealProjectMember(3L, PROJECT)).thenReturn(false);

    assertThatThrownBy(() -> zuordnung.ersetzeZustaendige(KARTE, PROJECT, List.of(3L)))
        .isInstanceOf(InvalidAssigneeException.class)
        .hasMessageContaining("3");
    verify(assignees, never()).replaceAssignees(anyLong(), anyList());
  }

  @Test
  void ersetzeLabelsEntferntDuplikateUndSchreibt() {
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(label(20L, "Bug", "#f00", false)));

    zuordnung.ersetzeLabels(KARTE, BOARD, List.of(20L, 20L), SESSION);

    verify(cardLabels).replaceLabels(KARTE, List.of(20L));
  }

  @Test
  void ersetzeLabelsLehntFremdesLabelAb() {
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(label(20L, "Bug", "#f00", false)));

    assertThatThrownBy(() -> zuordnung.ersetzeLabels(KARTE, BOARD, List.of(99L), SESSION))
        .isInstanceOf(InvalidLabelException.class)
        .hasMessageContaining("99");
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void aendereLabelFuegtHinzuUndBleibtBeiVorhandenemUnveraendert() {
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(label(20L, "Bug", "#f00", false)));
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(20L));

    zuordnung.aendereLabel(KARTE, BOARD, 20L, LabelAction.ADD, SESSION);

    verify(cardLabels).replaceLabels(KARTE, List.of(20L));
  }

  @Test
  void aendereLabelFuegtZuVorhandenenHinzu() {
    when(labels.findByBoardId(BOARD))
        .thenReturn(List.of(label(20L, "Bug", "#f00", false), label(21L, "Doku", "#0f0", false)));
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(20L));

    zuordnung.aendereLabel(KARTE, BOARD, 21L, LabelAction.ADD, SESSION);

    verify(cardLabels).replaceLabels(KARTE, List.of(20L, 21L));
  }

  @Test
  void aendereLabelNimmtAbUndLaesstDieUebrigenStehen() {
    when(labels.findByBoardId(BOARD))
        .thenReturn(List.of(label(20L, "Bug", "#f00", false), label(21L, "Doku", "#0f0", false)));
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(20L, 21L));

    zuordnung.aendereLabel(KARTE, BOARD, 20L, LabelAction.REMOVE, SESSION);

    verify(cardLabels).replaceLabels(KARTE, List.of(21L));
  }

  @Test
  void aendereLabelLehntFremdesLabelAb() {
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(label(20L, "Bug", "#f00", false)));

    assertThatThrownBy(() -> zuordnung.aendereLabel(KARTE, BOARD, 99L, LabelAction.ADD, SESSION))
        .isInstanceOf(InvalidLabelException.class)
        .hasMessageContaining("99");
    verify(cardLabels, never()).findByCardId(KARTE);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void ersetzeLabelsPerTokenLehntNeuesNurMenschenLabelAb() {
    givenFreigabeLabels();
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(31L));

    assertThatThrownBy(() -> zuordnung.ersetzeLabels(KARTE, BOARD, List.of(31L, 30L), TOKEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:night setzt nur ein Mensch im Board");
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void ersetzeLabelsPerTokenLehntWeggelassenesMaschinenLabelAb() {
    givenFreigabeLabels();
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(31L, 20L));

    assertThatThrownBy(() -> zuordnung.ersetzeLabels(KARTE, BOARD, List.of(20L), TOKEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label kit:klaeren nimmt nur ein Mensch im Board ab");
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void ersetzeLabelsPerTokenLaesstUnveraenderteFreigabeLabelsDurch() {
    givenFreigabeLabels();
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(30L, 31L));

    zuordnung.ersetzeLabels(KARTE, BOARD, List.of(30L, 31L, 20L), TOKEN);

    verify(cardLabels).replaceLabels(KARTE, List.of(30L, 31L, 20L));
  }

  @Test
  void ersetzeLabelsPerSessionDarfFreigabeLabelsInBeideRichtungen() {
    givenFreigabeLabels();
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(31L));

    zuordnung.ersetzeLabels(KARTE, BOARD, List.of(30L), SESSION);

    verify(cardLabels).replaceLabels(KARTE, List.of(30L));
  }

  @Test
  void aendereLabelPerTokenLehntSetzenEinesNurMenschenLabelsAb() {
    givenFreigabeLabels();

    assertThatThrownBy(() -> zuordnung.aendereLabel(KARTE, BOARD, 30L, LabelAction.ADD, TOKEN))
        .isInstanceOf(FreigabeLabelException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void aendereLabelPerTokenLehntAbnehmenEinesMaschinenLabelsAb() {
    givenFreigabeLabels();
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(31L));

    assertThatThrownBy(() -> zuordnung.aendereLabel(KARTE, BOARD, 31L, LabelAction.REMOVE, TOKEN))
        .isInstanceOf(FreigabeLabelException.class);
    verify(cardLabels, never()).replaceLabels(anyLong(), anyList());
  }

  @Test
  void aendereLabelPerTokenDarfInErlaubterRichtung() {
    givenFreigabeLabels();
    when(cardLabels.findByCardId(KARTE)).thenReturn(List.of(30L));

    zuordnung.aendereLabel(KARTE, BOARD, 30L, LabelAction.REMOVE, TOKEN);

    verify(cardLabels).replaceLabels(KARTE, List.of());
  }

  @Test
  void aendereLabelPerSessionDarfNurMenschenLabelSetzen() {
    givenFreigabeLabels();

    zuordnung.aendereLabel(KARTE, BOARD, 30L, LabelAction.ADD, SESSION);

    verify(cardLabels).replaceLabels(KARTE, List.of(30L));
  }

  /** Board mit {@code kit:night} (30), {@code kit:klaeren} (31) und einem gewöhnlichen Label. */
  private void givenFreigabeLabels() {
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                label(20L, "Bug", "#f00", false),
                label(30L, "kit:night", "#00f", false),
                label(31L, "kit:klaeren", "#0ff", false)));
  }

  @Test
  void entferneZustaendigeLoeschtAlle() {
    zuordnung.entferneZustaendige(KARTE);

    verify(assignees).deleteByCardId(KARTE);
  }

  @Test
  void gezaehlteMarkenLiefertNurGezaehlteLabelsInLabelReihenfolge() {
    when(labels.findByBoardId(BOARD))
        .thenReturn(
            List.of(
                label(20L, "Bug", "#f00", true),
                label(21L, "Doku", "#0f0", false),
                label(22L, "Risiko", "#00f", true)));
    when(cardLabels.findByCardIds(Set.of(1L, 2L)))
        .thenReturn(Map.of(1L, List.of(20L, 21L, 22L), 2L, List.of(21L)));

    Map<Long, List<LabelMarkView>> marken = zuordnung.gezaehlteMarken(BOARD, Set.of(1L, 2L));

    assertThat(marken.get(1L))
        .containsExactly(new LabelMarkView("Bug", "#f00"), new LabelMarkView("Risiko", "#00f"));
    assertThat(marken.get(2L)).isEmpty();
  }

  @Test
  void gezaehlteMarkenLaesstKartenOhneEintragWeg() {
    when(labels.findByBoardId(BOARD)).thenReturn(List.of(label(20L, "Bug", "#f00", true)));
    when(cardLabels.findByCardIds(Set.of(1L))).thenReturn(Map.of());

    assertThat(zuordnung.gezaehlteMarken(BOARD, Set.of(1L))).isEmpty();
  }
}
