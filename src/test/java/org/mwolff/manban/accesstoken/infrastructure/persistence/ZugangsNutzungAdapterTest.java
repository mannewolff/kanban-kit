package org.mwolff.manban.accesstoken.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.accesstoken.domain.AccessTokenArt;
import org.mwolff.manban.project.application.PermissionChecker;

/** Die Nutzung der Zugänge des Rechenzentrums je Board (Issue #2216, Plan #2206 E12). */
class ZugangsNutzungAdapterTest {

  private static final long PROJEKT = 1L;
  private static final long BOARD = 3L;
  private static final long ANDERES_BOARD = 4L;
  private static final long OWNER = 10L;
  private static final long ADMIN = 11L;
  private static final long MITGLIED = 12L;
  private static final Instant FRUEH = Instant.parse("2026-11-22T10:00:00Z");
  private static final Instant SPAET = Instant.parse("2026-11-22T10:05:00Z");

  private KanbanAccessTokenJpaRepository jpa;
  private ZugangsNutzungAdapter adapter;

  @BeforeEach
  void setUp() {
    jpa = mock(KanbanAccessTokenJpaRepository.class);
    PermissionChecker permissions = mock(PermissionChecker.class);
    when(permissions.istEchterOwnerOderAdmin(OWNER, PROJEKT)).thenReturn(true);
    when(permissions.istEchterOwnerOderAdmin(ADMIN, PROJEKT)).thenReturn(true);
    adapter = new ZugangsNutzungAdapter(jpa, permissions);
  }

  private static KanbanAccessTokenEntity zugang(
      long inhaber, @Nullable Long projekt, long board, @Nullable Instant genutzt) {
    return new KanbanAccessTokenEntity(
        new AccessToken(
            null,
            inhaber,
            projekt,
            board,
            "Rechenzentrum",
            "hash",
            "RZ",
            FRUEH,
            genutzt,
            false,
            AccessTokenArt.STANDARD));
  }

  @Test
  void jeBoardDerJuengsteZeitpunktDerZugaengeVonOwnerUndAdmin() {
    // Given
    List<Long> boards = List.of(BOARD, ANDERES_BOARD);
    when(jpa.findByBoardIdInAndArtAndRevokedFalse(boards, AccessTokenArt.STANDARD))
        .thenReturn(
            List.of(
                zugang(OWNER, PROJEKT, BOARD, FRUEH),
                zugang(ADMIN, PROJEKT, BOARD, SPAET),
                zugang(OWNER, PROJEKT, BOARD, FRUEH),
                zugang(ADMIN, PROJEKT, ANDERES_BOARD, FRUEH)));

    // When / Then
    assertThat(adapter.juengsteNutzungDesRechenzentrums(boards))
        .containsExactlyInAnyOrderEntriesOf(Map.of(BOARD, SPAET, ANDERES_BOARD, FRUEH));
  }

  @Test
  void zugangEinesMitgliedsZaehltNicht() {
    // Given
    when(jpa.findByBoardIdInAndArtAndRevokedFalse(List.of(BOARD), AccessTokenArt.STANDARD))
        .thenReturn(List.of(zugang(MITGLIED, PROJEKT, BOARD, SPAET)));

    // When / Then
    assertThat(adapter.juengsteNutzungDesRechenzentrums(List.of(BOARD))).isEmpty();
  }

  @Test
  void ungenutzterZugangUndZugangOhneProjektZaehlenNicht() {
    // Given
    when(jpa.findByBoardIdInAndArtAndRevokedFalse(List.of(BOARD), AccessTokenArt.STANDARD))
        .thenReturn(
            List.of(zugang(OWNER, PROJEKT, BOARD, null), zugang(OWNER, null, BOARD, SPAET)));

    // When / Then
    assertThat(adapter.juengsteNutzungDesRechenzentrums(List.of(BOARD))).isEmpty();
  }

  @Test
  void ohneBoardsKeineAbfrage() {
    // When / Then
    assertThat(adapter.juengsteNutzungDesRechenzentrums(List.of())).isEmpty();
    verifyNoInteractions(jpa);
  }
}
