package org.mwolff.manban.accesstoken.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.accesstoken.infrastructure.InMemoryLastUsedStampThrottle;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Verhaltenstests der API-Token-Verwaltung (Mockito an den Ports).
 *
 * <p>Die Uhr ist stellbar statt fixiert (Issue #997): Ob der Nutzungsstempel <em>innerhalb</em>
 * einer Minute unterbleibt und <em>nach</em> ihr wieder geschrieben wird, ist mit einer festen Uhr
 * nicht prüfbar. Der Zwischenspeicher ist bewusst der echte und kein Mock — er trägt die
 * Entscheidung „schreiben oder nicht", und ein Mock würde genau sie wegstubben.
 */
class AccessTokenServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AccessTokenRepository tokens;
  private TokenCryptoPort crypto;
  private BoardService boardService;
  private PermissionChecker permissions;
  private MutableClock clock;
  private AccessTokenService service;

  private static AccessToken token(long id, long userId, boolean revoked) {
    return new AccessToken(id, userId, null, null, "CI", "hash", "CI", FIXED, null, revoked);
  }

  @BeforeEach
  void setUp() {
    tokens = mock(AccessTokenRepository.class);
    crypto = mock(TokenCryptoPort.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    clock = new MutableClock(FIXED);
    ObjectProvider<AccessTokenService> self =
        new ObjectProvider<>() {
          @Override
          public AccessTokenService getObject() {
            return service;
          }
        };
    service =
        new AccessTokenService(
            tokens,
            crypto,
            boardService,
            permissions,
            clock,
            new InMemoryLastUsedStampThrottle(),
            self);
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
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(5L));
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
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(5L));

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
    when(boardService.findProjectId(20L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "CI", 5L, 20L))
        .isInstanceOf(InvalidTokenBindingException.class);
  }

  @Test
  void create_throwsInvalidBinding_whenBoardNotInProject() {
    // Given
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(99L));

    // When / Then
    assertThatThrownBy(() -> service.create(1L, "CI", 5L, 20L))
        .isInstanceOf(InvalidTokenBindingException.class);
  }

  @Test
  void create_throwsAccessDenied_whenUserMayNotWorkOnBoard() {
    // Given
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(5L));
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
  void revoke_marksRevoked_withoutWritingTheWholeToken() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(token(3L, 1L, false)));

    // When
    service.revoke(1L, 3L);

    // Then: spaltenscharf — ein volles save würde einen parallelen Widerruf überschreiben (#878).
    verify(tokens).markRevoked(3L);
    verify(tokens, never()).save(any(AccessToken.class));
  }

  @Test
  void revoke_marksRevokedAgain_whenAlreadyRevoked() {
    // Given: der Widerruf ist idempotent — der Zielzustand steht fest und hängt nicht am Lesen.
    when(tokens.findById(3L)).thenReturn(Optional.of(token(3L, 1L, true)));

    // When
    service.revoke(1L, 3L);

    // Then
    verify(tokens).markRevoked(3L);
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
  void revoke_writesNothing_whenTokenBelongsToOtherUser() {
    // Given: ein fremdes Token darf nicht einmal berührt werden — sonst wäre die 404 eine Fassade.
    when(tokens.findById(3L)).thenReturn(Optional.of(token(3L, 99L, false)));

    // When
    assertThatThrownBy(() -> service.revoke(1L, 3L))
        .isInstanceOf(AccessTokenNotFoundException.class);

    // Then
    verify(tokens, never()).markRevoked(anyLong());
    verify(tokens, never()).save(any(AccessToken.class));
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

    // When
    Optional<KanbanPrincipal> principal = service.resolveBinding("plain");

    // Then
    assertThat(principal).map(KanbanPrincipal::userId).contains(1L);
  }

  @Test
  void resolveBinding_touchesLastUsedAt_fromClock() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));

    // When
    service.resolveBinding("plain");

    // Then
    verify(tokens).touchLastUsedAtInOwnTransaction(3L, FIXED);
  }

  @Test
  void resolveBinding_touchesLastUsedAtOnlyOnce_withinTheSameMinute() {
    // Given: der Stempel ist minutengenau (Issue #997). Jeder Aufruf schriebe sonst eine
    // Zeilensperre auf genau die Zeile, die sich die gleichzeitigen Befehle einer Person teilen.
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));

    // When: drei Auflösungen innerhalb derselben Minute.
    service.resolveBinding("plain");
    clock.advance(Duration.ofSeconds(30));
    service.resolveBinding("plain");
    clock.advance(Duration.ofSeconds(29));
    service.resolveBinding("plain");

    // Then
    verify(tokens, times(1)).touchLastUsedAtInOwnTransaction(anyLong(), any(Instant.class));
  }

  @Test
  void resolveBinding_touchesLastUsedAtAgain_afterTheMinuteElapsed() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));

    // When
    service.resolveBinding("plain");
    clock.advance(Duration.ofSeconds(61));
    service.resolveBinding("plain");

    // Then: der zweite Stempel trägt den neuen Zeitpunkt — „zuletzt benutzt" bleibt aktuell.
    verify(tokens).touchLastUsedAtInOwnTransaction(3L, FIXED);
    verify(tokens).touchLastUsedAtInOwnTransaction(3L, FIXED.plusSeconds(61));
  }

  @Test
  void resolveBinding_returnsThePrincipalEvenWhenTheStampIsThrottled() {
    // Given: die Drosselung betrifft den Stempel, nicht die Auflösung.
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));
    service.resolveBinding("plain");

    // When
    Optional<KanbanPrincipal> principal = service.resolveBinding("plain");

    // Then
    assertThat(principal).map(KanbanPrincipal::userId).contains(1L);
  }

  @Test
  void resolveBinding_throttlesPerToken_notGlobally() {
    // Given: zwei Token desselben Nutzers — der Stempel des einen darf den des anderen nicht
    // verschlucken.
    when(crypto.hash("a")).thenReturn("hash-a");
    when(crypto.hash("b")).thenReturn("hash-b");
    when(tokens.findByTokenHash("hash-a")).thenReturn(Optional.of(token(3L, 1L, false)));
    when(tokens.findByTokenHash("hash-b")).thenReturn(Optional.of(token(4L, 1L, false)));

    // When
    service.resolveBinding("a");
    service.resolveBinding("b");

    // Then
    verify(tokens).touchLastUsedAtInOwnTransaction(3L, FIXED);
    verify(tokens).touchLastUsedAtInOwnTransaction(4L, FIXED);
  }

  @Test
  void resolveBinding_neverWritesTheWholeToken() {
    // Given: ein volles save aus dem gelesenen Zustand nähme einen parallelen Widerruf
    // zurück — genau der Fehler, den #878 behebt.
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));

    // When
    service.resolveBinding("plain");

    // Then
    verify(tokens, never()).save(any(AccessToken.class));
  }

  @Test
  void resolveBinding_returnsEmpty_forRevokedToken() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, true)));

    // When / Then
    assertThat(service.resolveBinding("plain")).isEmpty();
    verify(tokens, never()).touchLastUsedAtInOwnTransaction(anyLong(), any(Instant.class));
  }

  @Test
  void resolveBinding_returnsEmpty_forUnknownToken() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.resolveBinding("plain")).isEmpty();
    verify(tokens, never()).touchLastUsedAtInOwnTransaction(anyLong(), any(Instant.class));
  }

  @Test
  void resolve_returnsUserId_forActiveToken() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));

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
