package org.mwolff.manban.anbieter.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.accesstoken.application.ZugangsNutzung;
import org.mwolff.manban.anbieter.application.AnbieterLageService.AnbieterLage;
import org.mwolff.manban.anbieter.application.AnbieterLageService.BoardLage;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.card.application.LabelService.LabelNameColor;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Erreichbarkeit des Rechenzentrums (Issue #2216, Plan #2206 E12). */
class AnbieterLageServiceTest {

  private static final long KUNDE = 7L;
  private static final long PROJEKT = 1L;
  private static final long BOARD = 3L;
  private static final long OHNE_ANBIETER = 4L;
  private static final Instant JETZT = Instant.parse("2026-11-22T10:00:00Z");

  private PermissionChecker permissions;
  private BoardService boards;
  private LabelService labels;
  private ZugangsNutzung nutzung;
  private AnbieterLageService service;

  @BeforeEach
  void setUp() {
    permissions = mock(PermissionChecker.class);
    boards = mock(BoardService.class);
    labels = mock(LabelService.class);
    nutzung = mock(ZugangsNutzung.class);
    service =
        new AnbieterLageService(
            permissions,
            boards,
            labels,
            nutzung,
            Clock.fixed(JETZT, ZoneOffset.UTC),
            new AnbieterProperties(null));
    when(permissions.istEchterKunde(KUNDE, PROJEKT)).thenReturn(true);
    when(boards.listActiveBoardIds(PROJEKT)).thenReturn(List.of(BOARD, OHNE_ANBIETER));
    when(labels.listNamesAndColors(KUNDE, BOARD))
        .thenReturn(
            List.of(new LabelNameColor("bug", "#000"), new LabelNameColor("kit:provider", "#111")));
    when(labels.listNamesAndColors(KUNDE, OHNE_ANBIETER))
        .thenReturn(List.of(new LabelNameColor("kit:approval", "#222")));
  }

  private void gelesenVor(Duration vorher) {
    when(nutzung.juengsteNutzungDesRechenzentrums(List.of(BOARD)))
        .thenReturn(Map.of(BOARD, JETZT.minus(vorher)));
  }

  @Test
  void aelterAlsDieFristGiltAlsNichtErreichbar() {
    // Given
    gelesenVor(Duration.ofMinutes(10));

    // When
    AnbieterLage lage = service.lage(KUNDE, PROJEKT);

    // Then
    assertThat(lage.nichtErreichbar()).isTrue();
    assertThat(lage.boards())
        .containsExactly(new BoardLage(BOARD, true, JETZT.minus(Duration.ofMinutes(10))));
  }

  @Test
  void neuesLesenNimmtDenHinweisWeg() {
    // Given
    gelesenVor(Duration.ofMinutes(1));

    // When
    AnbieterLage lage = service.lage(KUNDE, PROJEKT);

    // Then
    assertThat(lage.nichtErreichbar()).isFalse();
    assertThat(lage.boards()).extracting(BoardLage::nichtErreichbar).containsExactly(false);
  }

  @Test
  void genauAnDerFristIstNochErreichbar() {
    // Given
    gelesenVor(AnbieterProperties.STANDARD_ABHOLFRIST);

    // When / Then
    assertThat(service.lage(KUNDE, PROJEKT).nichtErreichbar()).isFalse();
  }

  @Test
  void keinZeitpunktKeinHinweis() {
    // Given: Vor dem ersten Lesen — oder nur Zugänge eines Mitglieds, die der Port nicht zählt.
    when(nutzung.juengsteNutzungDesRechenzentrums(List.of(BOARD))).thenReturn(Map.of());

    // When
    AnbieterLage lage = service.lage(KUNDE, PROJEKT);

    // Then
    assertThat(lage.nichtErreichbar()).isFalse();
    assertThat(lage.boards()).isEmpty();
  }

  @Test
  void boardOhneLabelDerSacheAnbieterKeinHinweis() {
    // Given
    when(labels.listNamesAndColors(KUNDE, BOARD)).thenReturn(List.of());
    when(nutzung.juengsteNutzungDesRechenzentrums(List.of())).thenReturn(Map.of());

    // When / Then
    assertThat(service.lage(KUNDE, PROJEKT)).isEqualTo(new AnbieterLage(false, List.of()));
  }

  @Test
  void rolleMitgliedKeinHinweis() {
    // Given
    when(permissions.istEchterKunde(KUNDE, PROJEKT)).thenReturn(false);
    gelesenVor(Duration.ofMinutes(10));

    // When / Then
    assertThat(service.lage(KUNDE, PROJEKT)).isEqualTo(new AnbieterLage(false, List.of()));
    verifyNoInteractions(nutzung, labels);
  }

  @Test
  void nichtmitgliedEndetMit404() {
    // Given
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(KUNDE, PROJEKT);

    // When / Then
    assertThatThrownBy(() -> service.lage(KUNDE, PROJEKT))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(nutzung);
  }

  @Test
  void eigeneAbholfristGilt() {
    // Given
    service =
        new AnbieterLageService(
            permissions,
            boards,
            labels,
            nutzung,
            Clock.fixed(JETZT, ZoneOffset.UTC),
            new AnbieterProperties(Duration.ofMinutes(15)));
    gelesenVor(Duration.ofMinutes(10));

    // When / Then
    assertThat(service.lage(KUNDE, PROJEKT).nichtErreichbar()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"PT0S", "-PT1M"})
  void nichtPositiveAbholfristFaelltAufDieVorgabe(String wert) {
    // When / Then
    assertThat(new AnbieterProperties(Duration.parse(wert)).abholfrist())
        .isEqualTo(Duration.ofMinutes(6));
  }

  @Test
  void fehlendeAbholfristIstDieVorgabe() {
    // When / Then
    assertThat(new AnbieterProperties(null).abholfrist()).isEqualTo(Duration.ofMinutes(6));
  }
}
