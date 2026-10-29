package org.mwolff.manban.push.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.SitzungsEnde;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;
import org.mwolff.manban.push.application.PushAboService.BoardSchalter;
import org.mwolff.manban.push.domain.PushAbo;

/**
 * Abo-Verwaltung je Gerät (Issue #1997, Plan #1989 E13, E14): anlegen, auffrischen, beenden und
 * Boards schalten — nur am eigenen Abo, nur für Boards, deren Läufe das Konto sehen darf.
 */
class PushAboServiceTest {

  private static final Instant JETZT = Instant.parse("2026-10-29T10:00:00Z");
  private static final SitzungsEnde SITZUNG = new SitzungsEnde(JETZT.plus(Duration.ofDays(30)));
  private static final String ENDPUNKT = "https://push.example/geraet-1";
  private static final long ICH = 42L;
  private static final long ANDERER = 43L;
  private static final long PROJEKT = 7L;
  private static final long FREMDES_PROJEKT = 8L;
  private static final long BOARD = 40L;
  private static final long ZWEITES_BOARD = 41L;
  private static final long FREMDES_BOARD = 50L;

  private final Map<String, PushAbo> gespeichert = new HashMap<>();
  private PermissionChecker permissions;
  private ProjectService projects;
  private BoardService boards;
  private PushAboService service;

  @BeforeEach
  void setUp() {
    permissions = mock(PermissionChecker.class);
    projects = mock(ProjectService.class);
    boards = mock(BoardService.class);
    when(permissions.hatNachtlaufZugriff(ICH, PROJEKT)).thenReturn(true);
    when(permissions.hatNachtlaufZugriff(ICH, FREMDES_PROJEKT)).thenReturn(false);
    when(boards.requireProjectId(anyLong()))
        .thenAnswer(
            inv -> {
              long id = inv.getArgument(0);
              if (id == BOARD || id == ZWEITES_BOARD) {
                return PROJEKT;
              }
              if (id == FREMDES_BOARD) {
                return FREMDES_PROJEKT;
              }
              throw new BoardNotFoundException();
            });
    service =
        new PushAboService(
            new SpeicherRepository(),
            permissions,
            projects,
            boards,
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  /** Ein Repository im Speicher: vergibt Kennungen und hält das Abo je Endpunkt. */
  private final class SpeicherRepository implements PushAboRepository {

    private long naechsteId = 1;

    @Override
    public Optional<PushAbo> findByEndpoint(String endpoint) {
      return Optional.ofNullable(gespeichert.get(endpoint));
    }

    @Override
    public PushAbo save(PushAbo abo) {
      PushAbo mitId = abo;
      if (abo.id() == null) {
        mitId =
            new PushAbo(
                naechsteId,
                abo.userId(),
                abo.endpoint(),
                abo.p256dh(),
                abo.auth(),
                abo.angelegtAt(),
                abo.giltBis(),
                abo.boardsAus());
        naechsteId++;
      }
      gespeichert.put(mitId.endpoint(), mitId);
      return mitId;
    }

    @Override
    public void deleteById(long id) {
      gespeichert.values().removeIf(abo -> abo.requireId() == id);
    }
  }

  private void vorhanden(PushAbo abo) {
    gespeichert.put(abo.endpoint(), abo);
  }

  private static PushAbo abo(long userId, Instant angelegt, Instant giltBis, Set<Long> aus) {
    return new PushAbo(9L, userId, ENDPUNKT, "p-alt", "a-alt", angelegt, giltBis, aus);
  }

  private static BoardView boardView(long id, String name) {
    return new BoardView(id, PROJEKT, name, JETZT, List.of());
  }

  // --- anlegen und auffrischen --------------------------------------------------------------

  @Test
  void abonniere_neu_legtAboMitSitzungsendeAn() {
    // When
    service.abonniere(ICH, ENDPUNKT, "p", "a", SITZUNG);

    // Then
    assertThat(gespeichert.get(ENDPUNKT))
        .isEqualTo(new PushAbo(1L, ICH, ENDPUNKT, "p", "a", JETZT, SITZUNG.expiresAt(), Set.of()));
  }

  @Test
  void abonniere_eigenesLaufendes_frischtAufUndBehaeltBeginnUndSchalter() {
    // Given
    Instant angelegt = JETZT.minus(Duration.ofDays(2));
    vorhanden(abo(ICH, angelegt, JETZT.plus(Duration.ofDays(1)), Set.of(BOARD)));

    // When
    service.abonniere(ICH, ENDPUNKT, "p", "a", SITZUNG);

    // Then
    assertThat(gespeichert.get(ENDPUNKT))
        .isEqualTo(
            new PushAbo(9L, ICH, ENDPUNKT, "p", "a", angelegt, SITZUNG.expiresAt(), Set.of(BOARD)));
  }

  @Test
  void abonniere_eigenesAbgelaufenes_beginntNeu() {
    // Given
    vorhanden(abo(ICH, JETZT.minus(Duration.ofDays(40)), JETZT, Set.of(BOARD)));

    // When
    service.abonniere(ICH, ENDPUNKT, "p", "a", SITZUNG);

    // Then
    assertThat(gespeichert.get(ENDPUNKT))
        .isEqualTo(new PushAbo(9L, ICH, ENDPUNKT, "p", "a", JETZT, SITZUNG.expiresAt(), Set.of()));
  }

  /** Ein laufendes fremdes Abo wird nicht übernommen und bleibt, wie es ist. */
  @Test
  void abonniere_fremdesLaufendes_wirdAbgewiesen() {
    // Given
    PushAbo fremd = abo(ANDERER, JETZT.minus(Duration.ofDays(1)), JETZT.plusMillis(1), Set.of());
    vorhanden(fremd);

    // When / Then
    assertThatThrownBy(() -> service.abonniere(ICH, ENDPUNKT, "p", "a", SITZUNG))
        .isInstanceOf(PushAboVergebenException.class);
    assertThat(gespeichert.get(ENDPUNKT)).isEqualTo(fremd);
  }

  /** Das abgelaufene Abo eines anderen Kontos ist beendet; das Gerät beginnt für mich neu. */
  @Test
  void abonniere_fremdesAbgelaufenes_beginntFuerMichNeu() {
    // Given
    vorhanden(abo(ANDERER, JETZT.minus(Duration.ofDays(40)), JETZT, Set.of(BOARD)));

    // When
    service.abonniere(ICH, ENDPUNKT, "p", "a", SITZUNG);

    // Then
    assertThat(gespeichert.get(ENDPUNKT))
        .isEqualTo(new PushAbo(9L, ICH, ENDPUNKT, "p", "a", JETZT, SITZUNG.expiresAt(), Set.of()));
  }

  // --- beenden ------------------------------------------------------------------------------

  @Test
  void beende_eigenes_loescht() {
    // Given
    vorhanden(abo(ICH, JETZT, SITZUNG.expiresAt(), Set.of()));

    // When
    service.beende(ICH, ENDPUNKT);

    // Then
    assertThat(gespeichert).isEmpty();
  }

  @Test
  void beende_fremdes_wirdAbgewiesenUndBleibt() {
    // Given
    vorhanden(abo(ANDERER, JETZT, SITZUNG.expiresAt(), Set.of()));

    // When / Then
    assertThatThrownBy(() -> service.beende(ICH, ENDPUNKT))
        .isInstanceOf(PushAboNichtGefundenException.class);
    assertThat(gespeichert).containsKey(ENDPUNKT);
  }

  @Test
  void beende_unbekannt_wirdAbgewiesen() {
    assertThatThrownBy(() -> service.beende(ICH, ENDPUNKT))
        .isInstanceOf(PushAboNichtGefundenException.class);
  }

  @Test
  void beende_abgelaufenes_giltWieUnbekannt() {
    // Given
    vorhanden(abo(ICH, JETZT.minus(Duration.ofDays(40)), JETZT, Set.of()));

    // When / Then
    assertThatThrownBy(() -> service.beende(ICH, ENDPUNKT))
        .isInstanceOf(PushAboNichtGefundenException.class);
  }

  // --- Boards lesen -------------------------------------------------------------------------

  @Test
  void boards_nurProjekteMitSichtAufLaeufe_mitZustandDesGeraets() {
    // Given
    vorhanden(abo(ICH, JETZT, SITZUNG.expiresAt(), Set.of(ZWEITES_BOARD)));
    when(projects.listAccessible(ICH))
        .thenReturn(
            List.of(
                new AccessibleProject(PROJEKT, "Leitstand"),
                new AccessibleProject(FREMDES_PROJEKT, "Fremd")));
    when(boards.listBoards(ICH, PROJEKT))
        .thenReturn(List.of(boardView(BOARD, "Produkt"), boardView(ZWEITES_BOARD, "Betrieb")));

    // When
    List<BoardSchalter> schalter = service.schaltbareBoards(ICH, ENDPUNKT);

    // Then
    assertThat(schalter)
        .containsExactly(
            new BoardSchalter(BOARD, "Produkt", PROJEKT, "Leitstand", true),
            new BoardSchalter(ZWEITES_BOARD, "Betrieb", PROJEKT, "Leitstand", false));
  }

  @Test
  void boards_fremdesAbo_wirdAbgewiesen() {
    // Given
    vorhanden(abo(ANDERER, JETZT, SITZUNG.expiresAt(), Set.of()));

    // When / Then
    assertThatThrownBy(() -> service.schaltbareBoards(ICH, ENDPUNKT))
        .isInstanceOf(PushAboNichtGefundenException.class);
  }

  // --- Boards schalten ----------------------------------------------------------------------

  @Test
  void schalte_ausUndWiederAn() {
    // Given
    vorhanden(abo(ICH, JETZT, SITZUNG.expiresAt(), Set.of()));

    // When
    service.schalte(ICH, ENDPUNKT, BOARD, false);

    // Then
    assertThat(gespeichert.get(ENDPUNKT).boardsAus()).containsExactly(BOARD);

    // When
    service.schalte(ICH, ENDPUNKT, BOARD, true);

    // Then
    assertThat(gespeichert.get(ENDPUNKT).boardsAus()).isEmpty();
  }

  @Test
  void schalte_fremdesAbo_wirdAbgewiesenUndBleibt() {
    // Given
    PushAbo fremd = abo(ANDERER, JETZT, SITZUNG.expiresAt(), Set.of());
    vorhanden(fremd);

    // When / Then
    assertThatThrownBy(() -> service.schalte(ICH, ENDPUNKT, BOARD, false))
        .isInstanceOf(PushAboNichtGefundenException.class);
    assertThat(gespeichert.get(ENDPUNKT)).isEqualTo(fremd);
  }

  /** Ein Board, dessen Läufe das Konto nicht sehen darf, gilt wie unbekannt. */
  @Test
  void schalte_boardOhneSicht_wirdAbgewiesen() {
    // Given
    vorhanden(abo(ICH, JETZT, SITZUNG.expiresAt(), Set.of()));

    // When / Then
    assertThatThrownBy(() -> service.schalte(ICH, ENDPUNKT, FREMDES_BOARD, false))
        .isInstanceOf(BoardNotFoundException.class);
    assertThat(gespeichert.get(ENDPUNKT).boardsAus()).isEmpty();
  }

  /** Ein archiviertes Board löst {@code requireProjectId} nicht auf — es ist nicht schaltbar. */
  @Test
  void schalte_archiviertesBoard_wirdAbgewiesen() {
    // Given
    vorhanden(abo(ICH, JETZT, SITZUNG.expiresAt(), Set.of()));

    // When / Then
    assertThatThrownBy(() -> service.schalte(ICH, ENDPUNKT, 99L, false))
        .isInstanceOf(BoardNotFoundException.class);
    assertThat(gespeichert.get(ENDPUNKT).boardsAus()).isEmpty();
  }
}
