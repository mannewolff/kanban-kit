package org.mwolff.manban.accesstoken.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.beans.factory.ObjectProvider;

/** Verhaltenstests der API-Token-Verwaltung (Mockito an den Ports). */
class AccessTokenServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AccessTokenRepository tokens;
  private TokenCryptoPort crypto;
  private BoardRepository boards;
  private PermissionChecker permissions;
  private AccessTokenService service;

  private static AccessToken token(long id, long userId, boolean revoked) {
    return new AccessToken(id, userId, null, null, "CI", "hash", "CI", FIXED, null, revoked);
  }

  @BeforeEach
  void setUp() {
    tokens = mock(AccessTokenRepository.class);
    crypto = mock(TokenCryptoPort.class);
    boards = mock(BoardRepository.class);
    permissions = mock(PermissionChecker.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    ObjectProvider<AccessTokenService> self =
        new ObjectProvider<>() {
          @Override
          public AccessTokenService getObject() {
            return service;
          }
        };
    service = new AccessTokenService(tokens, crypto, boards, permissions, clock, self);
  }

  @Test
  void create_setsCreatedAtFromInjectedClock() {
    // Given
    when(crypto.generate()).thenReturn(new GeneratedToken("tk_plain", "hash"));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    service.create(1L, "CI", null, null);

    // Then
    ArgumentCaptor<AccessToken> captor = ArgumentCaptor.forClass(AccessToken.class);
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
  }

  @Test
  void create_returnsPlaintextOnce_forUnboundToken() {
    // Given
    when(crypto.generate()).thenReturn(new GeneratedToken("tk_plain", "hash"));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    AccessTokenService.CreatedAccessToken created = service.create(1L, "CI", null, null);

    // Then
    assertThat(created.plaintext()).isEqualTo("tk_plain");
  }

  @Test
  void create_bindsTokenToBoard_whenBindingValid() {
    // Given
    when(boards.findById(20L)).thenReturn(Optional.of(new Board(20L, 5L, "B", FIXED)));
    when(permissions.hasPermission(1L, 5L, Permission.TICKET_CREATE)).thenReturn(true);
    when(crypto.generate()).thenReturn(new GeneratedToken("tk_plain", "hash"));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<AccessToken> captor = ArgumentCaptor.forClass(AccessToken.class);
    service.create(1L, "CI", 5L, 20L);

    // Then
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().boardId()).isEqualTo(20L);
  }

  @Test
  void create_throwsInvalidBinding_whenOnlyBoardIdSet() {
    // Given: das Board existiert sogar — trotzdem muss die fehlende projectId (erste
    // Bedingung des Guards) abgewiesen werden. Ohne den Board-Stub würde ein Umgehen des
    // projectId==null-Zweigs (Mutant) über „Board unbekannt" dieselbe Ausnahme werfen und
    // unentdeckt bleiben.
    when(boards.findById(20L)).thenReturn(Optional.of(new Board(20L, 5L, "B", FIXED)));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "CI", null, 20L))
        .isInstanceOf(InvalidTokenBindingException.class);
  }

  @Test
  void create_throwsInvalidBinding_whenOnlyProjectIdSet() {
    // When / Then: projectId gesetzt, boardId fehlt -> unschlüssige Bindung
    assertThatThrownBy(() -> service.create(1L, "CI", 5L, null))
        .isInstanceOf(InvalidTokenBindingException.class);
  }

  @Test
  void create_throwsInvalidBinding_whenBoardUnknown() {
    // Given
    when(boards.findById(20L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "CI", 5L, 20L))
        .isInstanceOf(InvalidTokenBindingException.class);
  }

  @Test
  void create_throwsInvalidBinding_whenBoardNotInProject() {
    // Given
    when(boards.findById(20L)).thenReturn(Optional.of(new Board(20L, 99L, "B", FIXED)));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "CI", 5L, 20L))
        .isInstanceOf(InvalidTokenBindingException.class);
  }

  @Test
  void create_throwsAccessDenied_whenUserMayNotWorkOnBoard() {
    // Given
    when(boards.findById(20L)).thenReturn(Optional.of(new Board(20L, 5L, "B", FIXED)));
    when(permissions.hasPermission(1L, 5L, Permission.TICKET_CREATE)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "CI", 5L, 20L))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  @Test
  void list_mapsTokensToViews() {
    // Given
    when(tokens.findByUserId(1L)).thenReturn(List.of(token(3L, 1L, false)));

    // When
    List<AccessTokenService.AccessTokenView> views = service.list(1L);

    // Then
    assertThat(views)
        .singleElement()
        .extracting(AccessTokenService.AccessTokenView::id)
        .isEqualTo(3L);
  }

  @Test
  void revoke_savesRevokedToken_whenActive() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(token(3L, 1L, false)));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<AccessToken> captor = ArgumentCaptor.forClass(AccessToken.class);
    service.revoke(1L, 3L);

    // Then
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().revoked()).isTrue();
  }

  @Test
  void revoke_isNoOp_whenAlreadyRevoked() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(token(3L, 1L, true)));

    // When
    service.revoke(1L, 3L);

    // Then
    verify(tokens, never()).save(any(AccessToken.class));
  }

  @Test
  void revoke_throwsNotFound_whenTokenBelongsToOtherUser() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(token(3L, 99L, false)));

    // When / Then
    assertThatThrownBy(() -> service.revoke(1L, 3L))
        .isInstanceOf(AccessTokenNotFoundException.class);
  }

  @Test
  void revoke_throwsNotFound_whenTokenUnknown() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.revoke(1L, 3L))
        .isInstanceOf(AccessTokenNotFoundException.class);
  }

  @Test
  void resolveBinding_returnsPrincipal_forActiveToken() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    Optional<KanbanPrincipal> principal = service.resolveBinding("plain");

    // Then
    assertThat(principal).map(KanbanPrincipal::userId).contains(1L);
  }

  @Test
  void resolveBinding_updatesLastUsedAt_fromClock() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    ArgumentCaptor<AccessToken> captor = ArgumentCaptor.forClass(AccessToken.class);
    service.resolveBinding("plain");

    // Then
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().lastUsedAt()).isEqualTo(FIXED);
  }

  @Test
  void resolveBinding_returnsEmpty_forRevokedToken() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, true)));

    // When / Then
    assertThat(service.resolveBinding("plain")).isEmpty();
  }

  @Test
  void resolveBinding_returnsEmpty_forUnknownToken() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.resolveBinding("plain")).isEmpty();
  }

  @Test
  void resolve_returnsUserId_forActiveToken() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When / Then
    assertThat(service.resolve("plain")).isEqualTo(OptionalLong.of(1L));
  }

  @Test
  void resolve_returnsEmpty_forUnknownToken() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.resolve("plain")).isEmpty();
  }

  /** Simuliert die DB: vergibt beim ersten Speichern eine ID (Issue #0080). */
  private static AccessToken saved(AccessToken t) {
    if (t.id() != null) {
      return t;
    }
    return new AccessToken(
        7L,
        t.userId(),
        t.projectId(),
        t.boardId(),
        t.name(),
        t.tokenHash(),
        t.displayName(),
        t.createdAt(),
        t.lastUsedAt(),
        t.revoked());
  }
}
