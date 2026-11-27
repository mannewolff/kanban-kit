package org.mwolff.manban.codeanalyse.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.codeanalyse.domain.Messung;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte;
import org.mwolff.manban.codeanalyse.domain.Zustand;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/**
 * Dienst der Platte „Codeanalyse“ (Issue #2276, Plan #2272 E7, E9): Recht wie der übrige
 * Projekt-Leitstand, Daten des ganzen Projekts, Kalenderwochen bis zur Uhr. Ports gemockt, feste
 * Uhr.
 */
class BefunddichteServiceTest {

  /** Ein Donnerstag; die laufende Kalenderwoche beginnt Montag, 2026-11-23. */
  private static final Instant NOW = Instant.parse("2026-11-26T12:00:00Z");

  /** Montag elf Wochen vor der laufenden Woche. */
  private static final Instant FENSTER_BEGINN = Instant.parse("2026-09-07T00:00:00Z");

  private static final long BOARD = 10L;
  private static final long ANDERES_BOARD = 11L;
  private static final long PROJECT = 1L;
  private static final long USER = 3L;

  private MessungStore store;
  private BoardService boardService;
  private PermissionChecker permissions;
  private BefunddichteService service;

  @BeforeEach
  void setUp() {
    store = mock(MessungStore.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    service =
        new BefunddichteService(store, boardService, permissions, Clock.fixed(NOW, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(boardService.requireProjectId(ANDERES_BOARD)).thenReturn(PROJECT);
  }

  @Test
  void einNichtmitgliedBekommtDieAusnahmeUndDerSpeicherWirdNichtGelesen() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(USER, PROJECT);

    assertThatThrownBy(() -> service.wochenwerte(USER, BOARD))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(store);
  }

  @Test
  void einUnbekanntesOderArchiviertesBoardIstNichtGefunden() {
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.wochenwerte(USER, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
    verifyNoInteractions(store, permissions);
  }

  /** E9: Geladen wird ab dem Fensterbeginn der Kalenderwochen zur Zeit der Uhr. */
  @Test
  void einMitgliedLiestDieMessungenDesProjektsAbDemFensterbeginn() {
    Instant veroeffentlicht = Instant.parse("2026-11-24T09:00:00Z");
    when(store.ladeAb(PROJECT, FENSTER_BEGINN))
        .thenReturn(List.of(zaehlend(veroeffentlicht, new Zustand(2, 0, 2, 1_000))));
    when(store.hatMessungen(PROJECT)).thenReturn(true);
    when(store.erstesPublishedAt(PROJECT)).thenReturn(Optional.of(veroeffentlicht));

    Wochenwerte werte = service.wochenwerte(USER, BOARD);

    verify(permissions).requireMembership(USER, PROJECT);
    assertThat(werte.hatMessungen()).isTrue();
    assertThat(werte.dataSince()).isEqualTo(veroeffentlicht);
    assertThat(werte.wochen()).hasSize(12);
    assertThat(werte.wochen().get(0).wochenbeginn()).isEqualTo(FENSTER_BEGINN);
    Wochenwerte.Woche laufend = werte.wochen().get(11);
    assertThat(laufend.wochenbeginn()).isEqualTo(Instant.parse("2026-11-23T00:00:00Z"));
    assertThat(laufend.zaehlendeMessungen()).isEqualTo(1);
    assertThat(laufend.karten()).isEqualTo(3L);
    assertThat(laufend.vorher()).isNotNull();
    assertThat(laufend.vorher().dichte()).isEqualTo(4.0);
    assertThat(laufend.rueckgangProzent()).isEqualTo(75.0);
  }

  /** Fachplan #2271 AK 13: dieselben Werte, gleich von welchem Board des Projekts aus. */
  @Test
  void dieMessungenKommenAusDemProjektGleichVonWelchemBoardAus() {
    Instant veroeffentlicht = Instant.parse("2026-11-24T09:00:00Z");
    when(store.ladeAb(PROJECT, FENSTER_BEGINN))
        .thenReturn(List.of(zaehlend(veroeffentlicht, new Zustand(1, 1, 1, 500))));
    when(store.hatMessungen(PROJECT)).thenReturn(true);
    when(store.erstesPublishedAt(PROJECT)).thenReturn(Optional.of(veroeffentlicht));

    assertThat(service.wochenwerte(USER, ANDERES_BOARD))
        .isEqualTo(service.wochenwerte(USER, BOARD));
    verify(permissions, times(2)).requireMembership(USER, PROJECT);
  }

  /**
   * E9: „hat Messungen“ und „seit“ gelten für das ganze Projekt, nicht nur für das Fenster — eine
   * Messung vor dem Fensterbeginn zählt in keiner Woche, aber die Platte zeigt nicht „noch keine
   * Messung“ und kein „seit“.
   */
  @Test
  void eineMessungVorDemFensterHaeltHatMessungenUndLaesstSeitLeer() {
    when(store.ladeAb(PROJECT, FENSTER_BEGINN)).thenReturn(List.of());
    when(store.hatMessungen(PROJECT)).thenReturn(true);
    when(store.erstesPublishedAt(PROJECT))
        .thenReturn(Optional.of(Instant.parse("2026-06-01T10:00:00Z")));

    Wochenwerte werte = service.wochenwerte(USER, BOARD);

    assertThat(werte.hatMessungen()).isTrue();
    assertThat(werte.dataSince()).isNull();
    assertThat(werte.wochen()).allSatisfy(w -> assertThat(w.vorher()).isNull());
  }

  /** E9: Liegt die erste Messung genau am Fensterbeginn, steht kein „seit“. */
  @Test
  void eineErsteMessungGenauAmFensterbeginnLaesstSeitLeer() {
    when(store.ladeAb(PROJECT, FENSTER_BEGINN))
        .thenReturn(List.of(zaehlend(FENSTER_BEGINN, new Zustand(1, 0, 0, 100))));
    when(store.hatMessungen(PROJECT)).thenReturn(true);
    when(store.erstesPublishedAt(PROJECT)).thenReturn(Optional.of(FENSTER_BEGINN));

    Wochenwerte werte = service.wochenwerte(USER, BOARD);

    assertThat(werte.dataSince()).isNull();
    assertThat(werte.wochen().get(0).zaehlendeMessungen()).isEqualTo(1);
  }

  @Test
  void ohneMessungStehtHatMessungenAufFalschUndKeinSeit() {
    when(store.ladeAb(PROJECT, FENSTER_BEGINN)).thenReturn(List.of());
    when(store.hatMessungen(PROJECT)).thenReturn(false);
    when(store.erstesPublishedAt(PROJECT)).thenReturn(Optional.empty());

    Wochenwerte werte = service.wochenwerte(USER, BOARD);

    assertThat(werte.hatMessungen()).isFalse();
    assertThat(werte.dataSince()).isNull();
    assertThat(werte.wochen()).hasSize(12);
  }

  private static Messung zaehlend(Instant publishedAt, Zustand vorher) {
    return new Messung(
        "c-" + publishedAt,
        publishedAt,
        "base",
        "1.0",
        true,
        null,
        3,
        vorher,
        new Zustand(1, 0, 0, 1_000));
  }
}
