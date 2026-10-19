package org.mwolff.manban.deployment.application;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.Auslieferungskennzahlen;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;
import org.mwolff.manban.deployment.domain.GemeldeterCommit;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/**
 * Dienst der Platte „Auslieferung“ (Issue #1835, Plan #1831 E1, E5, E8): Recht wie der übrige
 * Projekt-Leitstand, Daten des ganzen Projekts, Fenster bis zur Uhr. Ports gemockt, feste Uhr.
 */
class DeploymentKennzahlenServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-17T12:00:00Z");
  private static final Instant FENSTER_BEGINN = NOW.minus(Duration.ofDays(84));
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;
  private static final long USER = 3L;

  private DeploymentStore store;
  private BoardService boardService;
  private PermissionChecker permissions;
  private DeploymentKennzahlenService service;

  @BeforeEach
  void setUp() {
    store = mock(DeploymentStore.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    service =
        new DeploymentKennzahlenService(
            store, boardService, permissions, Clock.fixed(NOW, ZoneOffset.UTC));
    when(boardService.requireProjectId(BOARD)).thenReturn(PROJECT);
  }

  @Test
  void einNichtmitgliedBekommtNichtGefundenUndKeineDaten() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireMembership(USER, PROJECT);

    assertThatThrownBy(() -> service.kennzahlen(USER, BOARD))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(store);
  }

  @Test
  void einUnbekanntesOderArchiviertesBoardIstNichtGefunden() {
    when(boardService.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.kennzahlen(USER, BOARD))
        .isInstanceOf(BoardNotFoundException.class);
    verifyNoInteractions(store, permissions);
  }

  /** E1, E8: Die Daten des Projekts zum Board, geladen ab dem Beginn des 84-Tage-Fensters. */
  @Test
  void einMitgliedLiestDieKennzahlenDesProjektsImFensterBisZurUhr() {
    Instant ausgeliefert = NOW.minus(Duration.ofHours(1));
    when(store.ladeAuswertung(PROJECT, FENSTER_BEGINN))
        .thenReturn(
            new Auslieferungsdaten(
                List.of(new Auslieferungsversuch(1L, "lauf-1", Ausgang.SUCCESS, ausgeliefert, "r")),
                List.of(new GemeldeterCommit(1L, "aaa", ausgeliefert.minus(Duration.ofHours(3))))));

    Auslieferungskennzahlen kennzahlen = service.kennzahlen(USER, BOARD);

    assertThat(kennzahlen.hasReports()).isTrue();
    assertThat(kennzahlen.deploymentCount()).isEqualTo(1);
    assertThat(kennzahlen.leadTimeMedianSeconds()).isEqualTo(Duration.ofHours(3).toSeconds());
    assertThat(kennzahlen.dataSince()).isEqualTo(ausgeliefert);
    assertThat(kennzahlen.weeks()).hasSize(12);
    assertThat(kennzahlen.weeks().get(11).weekStart()).isEqualTo(NOW.minus(Duration.ofDays(7)));
    assertThat(kennzahlen.weeks().get(11).successCount()).isEqualTo(1);
  }

  @Test
  void ohneMeldungLiefertDerDienstKeineNullwerte() {
    when(store.ladeAuswertung(PROJECT, FENSTER_BEGINN))
        .thenReturn(new Auslieferungsdaten(List.of(), List.of()));

    Auslieferungskennzahlen kennzahlen = service.kennzahlen(USER, BOARD);

    assertThat(kennzahlen.hasReports()).isFalse();
    assertThat(kennzahlen.changeFailurePercent()).isNull();
    assertThat(kennzahlen.leadTimeMedianSeconds()).isNull();
  }
}
