package org.mwolff.manban.accesstoken.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
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
import org.mwolff.manban.accesstoken.domain.AccessTokenArt;
import org.mwolff.manban.accesstoken.infrastructure.InMemoryLastUsedStampThrottle;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.auth.application.UserSummary;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardView;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.AccessibleProject;
import org.mwolff.manban.project.domain.Permission;
import org.mwolff.manban.ratelimit.MutableClock;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Verhaltenstests der API-Token-Verwaltung (Mockito an den Ports).
 *
 * <p>Die Uhr ist stellbar statt fixiert (Issue #997): Ob der Nutzungsstempel <em>innerhalb</em>
 * einer Minute unterbleibt und <em>nach</em> ihr wieder geschrieben wird, ist mit einer festen Uhr
 * nicht prüfbar. Der Zwischenspeicher ist bewusst der echte und kein Mock — er trägt die
 * Entscheidung „schreiben oder nicht", und ein Mock würde genau sie wegstubben.
 */
// PMD.TooManyMethods: Mit der Art des Tokens (Issue #2102) kommen Fälle für Auflösung und Liste
// dazu; je Fall eine kleine Testmethode bleibt lesbarer als ein Zerschneiden nach Methodenzahl.
@SuppressWarnings("PMD.TooManyMethods")
class AccessTokenServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AccessTokenRepository tokens;
  private TokenCryptoPort crypto;
  private BoardService boardService;
  private PermissionChecker permissions;
  private ProjectService projects;
  private UserLookup users;
  private MutableClock clock;
  private AccessTokenService service;

  private static AccessToken token(long id, long userId, boolean revoked) {
    return new AccessToken(id, userId, null, null, "CI", "hash", "CI", FIXED, null, revoked);
  }

  private static AccessToken appZugang(long id) {
    return new AccessToken(
        id, 1L, 5L, 20L, "App", "hash", "App", FIXED, null, false, AccessTokenArt.APP);
  }

  @BeforeEach
  void setUp() {
    tokens = mock(AccessTokenRepository.class);
    crypto = mock(TokenCryptoPort.class);
    boardService = mock(BoardService.class);
    permissions = mock(PermissionChecker.class);
    projects = mock(ProjectService.class);
    users = mock(UserLookup.class);
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
            projects,
            users,
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
  void list_omitsTokensOfKindApp() {
    // Given
    when(tokens.findByUserId(1L)).thenReturn(List.of(appZugang(4L), token(3L, 1L, false)));

    // When
    List<AccessTokenService.AccessTokenView> views = service.list(1L);

    // Then
    assertThat(views)
        .singleElement()
        .extracting(AccessTokenService.AccessTokenView::id)
        .isEqualTo(3L);
  }

  @Test
  void create_savesTokenOfKindStandard() {
    // Given
    when(crypto.generate()).thenReturn(new GeneratedToken("tk_plain", "hash"));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    service.create(1L, "CI", null, null);

    // Then
    ArgumentCaptor<AccessToken> captor = ArgumentCaptor.forClass(AccessToken.class);
    verify(tokens).save(captor.capture());
    assertThat(captor.getValue().art()).isEqualTo(AccessTokenArt.STANDARD);
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
  void resolveBinding_setsAppZugang_forTokenOfKindApp() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(appZugang(4L)));

    // When
    Optional<KanbanPrincipal> principal = service.resolveBinding("plain");

    // Then
    assertThat(principal).map(KanbanPrincipal::appZugang).contains(true);
  }

  @Test
  void resolveBinding_leavesAppZugangUnset_forTokenOfKindStandard() {
    // Given
    when(crypto.hash("plain")).thenReturn("hash");
    when(tokens.findByTokenHash("hash")).thenReturn(Optional.of(token(3L, 1L, false)));

    // When
    Optional<KanbanPrincipal> principal = service.resolveBinding("plain");

    // Then
    assertThat(principal).map(KanbanPrincipal::appZugang).contains(false);
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

  // --- Zugang der App (Issue #2103) -----------------------------------------

  private void angelegtWird() {
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(5L));
    when(tokens.findWirksamenAppZugang(20L)).thenReturn(Optional.empty());
    when(crypto.generate()).thenReturn(new GeneratedToken("tk_app", "hash-app"));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));
  }

  private void inhaberHeisst(long userId, String name) {
    when(users.findById(userId))
        .thenReturn(Optional.of(new UserSummary(userId, "i@example.com", name, true)));
  }

  private AccessToken gespeicherterZugang() {
    ArgumentCaptor<AccessToken> captor = ArgumentCaptor.forClass(AccessToken.class);
    verify(tokens).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void appZugangAnlegen_legtZugangMitFestemNamenFuerDenAufruferAn() {
    // Given
    angelegtWird();

    // When
    service.appZugangAnlegen(1L, 5L, 20L);

    // Then
    AccessToken gespeichert = gespeicherterZugang();
    assertThat(gespeichert)
        .extracting(
            AccessToken::userId,
            AccessToken::projectId,
            AccessToken::boardId,
            AccessToken::name,
            AccessToken::displayName,
            AccessToken::art,
            AccessToken::createdAt)
        .containsExactly(1L, 5L, 20L, "Desktop-App", "Desktop-App", AccessTokenArt.APP, FIXED);
  }

  @Test
  void appZugangAnlegen_liefertDenKlartextGenauEinmal() {
    // Given
    angelegtWird();

    // When
    AccessTokenService.CreatedAccessToken created = service.appZugangAnlegen(1L, 5L, 20L);

    // Then
    assertThat(created)
        .extracting(
            AccessTokenService.CreatedAccessToken::id,
            AccessTokenService.CreatedAccessToken::name,
            AccessTokenService.CreatedAccessToken::plaintext)
        .containsExactly(7L, "Desktop-App", "tk_app");
  }

  @Test
  void appZugangAnlegen_prueftDieEchteRolleUndKeinRecht() {
    // Given
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireEchterOwnerOderAdmin(1L, 5L);

    // When / Then
    assertThatThrownBy(() -> service.appZugangAnlegen(1L, 5L, 20L))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(tokens, never()).save(any(AccessToken.class));
  }

  @Test
  void appZugangAnlegen_weistBoardEinesAnderenProjektsAb() {
    // Given
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(6L));

    // When / Then
    assertThatThrownBy(() -> service.appZugangAnlegen(1L, 5L, 20L))
        .isInstanceOf(InvalidTokenBindingException.class);
    verify(tokens, never()).save(any(AccessToken.class));
  }

  @Test
  void appZugangAnlegen_weistUnbekanntesBoardAb() {
    // Given
    when(boardService.findProjectId(20L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.appZugangAnlegen(1L, 5L, 20L))
        .isInstanceOf(InvalidTokenBindingException.class);
  }

  @Test
  void appZugangAnlegen_nenntDenInhaber_wennSchonEinZugangBesteht() {
    // Given
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(5L));
    when(tokens.findWirksamenAppZugang(20L)).thenReturn(Optional.of(appZugang(3L)));
    inhaberHeisst(1L, "Erika Inhaber");

    // When / Then
    assertThatThrownBy(() -> service.appZugangAnlegen(2L, 5L, 20L))
        .isInstanceOfSatisfying(
            AppZugangBestehtException.class,
            e -> {
              assertThat(e.getMessage()).contains("Erika Inhaber");
              assertThat(e.schluessel()).isEqualTo("accesstoken.app-zugang.besteht");
              assertThat(e.argumente()).containsExactly("Erika Inhaber");
            });
    verify(tokens, never()).save(any(AccessToken.class));
  }

  @Test
  void appZugangAnlegen_nenntDieKennung_wennDerInhaberUnbekanntIst() {
    // Given
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(5L));
    when(tokens.findWirksamenAppZugang(20L)).thenReturn(Optional.of(appZugang(3L)));
    when(users.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.appZugangAnlegen(2L, 5L, 20L))
        .isInstanceOf(AppZugangBestehtException.class)
        .hasMessageContaining("#1");
  }

  @Test
  void appZugangAnlegen_machtAusDerIndexverletzungDenKonflikt() {
    // Given: Die Prüfung sah noch keinen Zugang, der Index lässt nur den schnelleren durch.
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(5L));
    when(tokens.findWirksamenAppZugang(20L))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(appZugang(3L)));
    when(crypto.generate()).thenReturn(new GeneratedToken("tk_app", "hash-app"));
    when(tokens.save(any(AccessToken.class)))
        .thenThrow(new DataIntegrityViolationException("uq_kanban_access_token_app_board"));
    inhaberHeisst(1L, "Erika Inhaber");

    // When / Then
    assertThatThrownBy(() -> service.appZugangAnlegen(2L, 5L, 20L))
        .isInstanceOf(AppZugangBestehtException.class)
        .hasMessageContaining("Erika Inhaber");
  }

  @Test
  void appZugangAnlegen_reichtEineAndereVerletzungDurch() {
    // Given: Es gibt danach keinen wirksamen Zugang — die Verletzung hat eine andere Ursache.
    DataIntegrityViolationException verletzung =
        new DataIntegrityViolationException("fk_kanban_access_token_board");
    when(boardService.findProjectId(20L)).thenReturn(Optional.of(5L));
    when(tokens.findWirksamenAppZugang(20L)).thenReturn(Optional.empty());
    when(crypto.generate()).thenReturn(new GeneratedToken("tk_app", "hash-app"));
    when(tokens.save(any(AccessToken.class))).thenThrow(verletzung);

    // When / Then
    assertThatThrownBy(() -> service.appZugangAnlegen(2L, 5L, 20L)).isSameAs(verletzung);
  }

  @Test
  void appZugangErneuern_widerruftDenAltenUndLegtEinenNeuenAn() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(appZugang(3L)));
    when(tokens.markRevokedIfActive(3L)).thenReturn(true);
    when(crypto.generate()).thenReturn(new GeneratedToken("tk_neu", "hash-neu"));
    when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    AccessTokenService.CreatedAccessToken created = service.appZugangErneuern(1L, 3L);

    // Then
    assertThat(created.plaintext()).isEqualTo("tk_neu");
    assertThat(gespeicherterZugang())
        .extracting(
            AccessToken::userId,
            AccessToken::projectId,
            AccessToken::boardId,
            AccessToken::tokenHash,
            AccessToken::art)
        .containsExactly(1L, 5L, 20L, "hash-neu", AccessTokenArt.APP);
  }

  @Test
  void appZugangErneuern_meldetEinenZugangOhneBindungAlsZustandsfehler() {
    // Given: Die Datenbank schließt das aus; die Prüfung bleibt die Antwort auf den Fall.
    when(tokens.findById(3L))
        .thenReturn(
            Optional.of(
                new AccessToken(
                    3L, 1L, null, 20L, "App", "h", "App", FIXED, null, false, AccessTokenArt.APP)));

    // When / Then
    assertThatThrownBy(() -> service.appZugangErneuern(1L, 3L))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void appZugangErneuern_weistEinenAnderenAdminAb() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(appZugang(3L)));

    // When / Then
    assertThatThrownBy(() -> service.appZugangErneuern(2L, 3L))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(tokens, never()).markRevokedIfActive(anyLong());
  }

  @Test
  void appZugangErneuern_verraetEinemNichtmitgliedNichts() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(appZugang(3L)));
    doThrow(new ProjectNotFoundException()).when(permissions).requireEchterOwnerOderAdmin(2L, 5L);

    // When / Then
    assertThatThrownBy(() -> service.appZugangErneuern(2L, 3L))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void appZugangErneuern_legtNichtsAn_wennDerZugangSchonWiderrufenIst() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(appZugang(3L)));
    when(tokens.markRevokedIfActive(3L)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> service.appZugangErneuern(1L, 3L))
        .isInstanceOf(AccessTokenNotFoundException.class);
    verify(tokens, never()).save(any(AccessToken.class));
  }

  @Test
  void appZugangErneuern_kenntKeinGewoehnlichesToken() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(token(3L, 1L, false)));

    // When / Then
    assertThatThrownBy(() -> service.appZugangErneuern(1L, 3L))
        .isInstanceOf(AccessTokenNotFoundException.class);
  }

  @Test
  void appZugangWiderrufen_erlaubtDemInhaberOhneRollenpruefung() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(appZugang(3L)));

    // When
    service.appZugangWiderrufen(1L, 3L);

    // Then
    verify(tokens).markRevoked(3L);
    verify(permissions, never()).requireEchterOwnerOderAdmin(anyLong(), anyLong());
  }

  @Test
  void appZugangWiderrufen_erlaubtEinemAnderenAdmin() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(appZugang(3L)));

    // When
    service.appZugangWiderrufen(2L, 3L);

    // Then
    verify(permissions).requireEchterOwnerOderAdmin(2L, 5L);
    verify(tokens).markRevoked(3L);
  }

  @Test
  void appZugangWiderrufen_weistEinFremdesMitgliedAb() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(appZugang(3L)));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireEchterOwnerOderAdmin(2L, 5L);

    // When / Then
    assertThatThrownBy(() -> service.appZugangWiderrufen(2L, 3L))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(tokens, never()).markRevoked(anyLong());
  }

  @Test
  void appZugangWiderrufen_kenntKeinGewoehnlichesToken() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.of(token(3L, 1L, false)));

    // When / Then
    assertThatThrownBy(() -> service.appZugangWiderrufen(1L, 3L))
        .isInstanceOf(AccessTokenNotFoundException.class);
  }

  @Test
  void appZugangWiderrufen_meldetUnbekanntenZugang() {
    // Given
    when(tokens.findById(3L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.appZugangWiderrufen(1L, 3L))
        .isInstanceOf(AccessTokenNotFoundException.class);
  }

  private void projektMitZweiBoards(long userId) {
    when(projects.listAccessible(userId))
        .thenReturn(List.of(new AccessibleProject(5L, "Alpha"), new AccessibleProject(6L, "Beta")));
    when(permissions.istEchterOwnerOderAdmin(userId, 5L)).thenReturn(true);
    when(permissions.istEchterOwnerOderAdmin(userId, 6L)).thenReturn(false);
    when(boardService.listBoards(userId, 5L))
        .thenReturn(
            List.of(
                new BoardView(20L, 5L, "Mit Zugang", FIXED, List.of()),
                new BoardView(21L, 5L, "Ohne Zugang", FIXED, List.of())));
    when(tokens.findWirksameAppZugaenge(List.of(20L, 21L))).thenReturn(List.of(appZugang(3L)));
    inhaberHeisst(1L, "Erika Inhaber");
  }

  @Test
  void appZugaengeAuflisten_liefertJeBoardEineZeileMitZugang() {
    // Given
    projektMitZweiBoards(1L);

    // When
    List<AccessTokenService.AppZugangZeile> zeilen = service.appZugaengeAuflisten(1L);

    // Then
    assertThat(zeilen.get(0))
        .isEqualTo(
            new AccessTokenService.AppZugangZeile(
                3L, 5L, "Alpha", 20L, "Mit Zugang", "Erika Inhaber", true, FIXED, null));
  }

  @Test
  void appZugaengeAuflisten_liefertEineLeereZeileFuerBoardOhneZugang() {
    // Given
    projektMitZweiBoards(1L);

    // When
    List<AccessTokenService.AppZugangZeile> zeilen = service.appZugaengeAuflisten(1L);

    // Then
    assertThat(zeilen)
        .hasSize(2)
        .last()
        .isEqualTo(
            new AccessTokenService.AppZugangZeile(
                null, 5L, "Alpha", 21L, "Ohne Zugang", null, false, null, null));
  }

  @Test
  void appZugaengeAuflisten_kennzeichnetEinenFremdenZugang() {
    // Given
    projektMitZweiBoards(2L);

    // When
    List<AccessTokenService.AppZugangZeile> zeilen = service.appZugaengeAuflisten(2L);

    // Then
    assertThat(zeilen.get(0).eigener()).isFalse();
  }

  @Test
  void appZugaengeAuflisten_istLeerOhneAdminOderOwnerRolle() {
    // Given
    when(projects.listAccessible(1L)).thenReturn(List.of(new AccessibleProject(5L, "Alpha")));
    when(permissions.istEchterOwnerOderAdmin(1L, 5L)).thenReturn(false);

    // When / Then
    assertThat(service.appZugaengeAuflisten(1L)).isEmpty();
    verify(boardService, never()).listBoards(anyLong(), anyLong());
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
