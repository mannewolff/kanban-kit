package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Verhaltenstests der Registrierung (Mockito an den Ports). */
class RegisterUserServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AppUserRepository users;
  private RequestLoginCodeService loginCodes;
  private RegistrationApprovalPolicy approvalPolicy;
  private RegisterUserService service;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    loginCodes = mock(RequestLoginCodeService.class);
    approvalPolicy = mock(RegistrationApprovalPolicy.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service = new RegisterUserService(users, loginCodes, clock, approvalPolicy);
    when(users.save(any(AppUser.class)))
        .thenAnswer(
            inv -> {
              AppUser u = inv.getArgument(0);
              return new AppUser(
                  2L, u.email(), u.displayName(), u.emailVerified(), u.platformRole());
            });
  }

  private AppUser savedUser() {
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    verify(users).save(captor.capture());
    return captor.getValue();
  }

  @Test
  void register_returnsPersistedUser() {
    // When
    AppUser registered = service.register("a@x.de", "Ada");

    // Then
    assertThat(registered.id()).isEqualTo(2L);
  }

  @Test
  void register_normalizesEmail() {
    // When
    service.register("  A@X.de ", "Ada");

    // Then
    assertThat(savedUser().email()).isEqualTo("a@x.de");
  }

  @Test
  void register_persistsUnverifiedUser() {
    // When
    service.register("a@x.de", "Ada");

    // Then — die erste Anmeldung per Code bestätigt die Adresse.
    assertThat(savedUser())
        .extracting(AppUser::emailVerified, AppUser::platformRole)
        .containsExactly(false, PlatformRole.USER);
  }

  @Test
  void register_trimsDisplayName() {
    // When
    service.register("a@x.de", "  Ada  ");

    // Then
    assertThat(savedUser().displayName()).isEqualTo("Ada");
  }

  @Test
  void register_requestsExactlyOneLoginCode_afterSavingTheAccount() {
    // When
    service.register("  A@X.de ", "Ada");

    // Then — der Code geht an das eben angelegte Konto und zählt auf die Grenze je Adresse.
    InOrder order = inOrder(users, loginCodes);
    order.verify(users).save(any(AppUser.class));
    order.verify(loginCodes).requestCode("a@x.de");
    order.verifyNoMoreInteractions();
  }

  @Test
  void register_propagatesLimitExceeded_fromCodeRequest() {
    // Given — die Grenze je Adresse ist durch frühere Anforderungen schon erreicht.
    doThrow(new LoginCodeLimitExceededException()).when(loginCodes).requestCode("a@x.de");

    // When / Then — die Ausnahme rollt die Registrierung zurück (429 statt Konto ohne Code).
    assertThatThrownBy(() -> service.register("a@x.de", "Ada"))
        .isInstanceOf(LoginCodeLimitExceededException.class);
  }

  @Test
  void register_autoApprovesUser_whenPolicyApproves() {
    // Given: für die E-Mail liegt eine offene Einladung vor (Policy = true).
    when(approvalPolicy.shouldAutoApprove("a@x.de")).thenReturn(true);

    // When
    service.register("a@x.de", "Ada");

    // Then: sofort freigegeben mit dem Uhr-Zeitpunkt.
    assertThat(savedUser().approvedAt()).isEqualTo(FIXED);
  }

  @Test
  void register_leavesUserPending_whenPolicyDeclines() {
    // Given: keine offene Einladung (Policy = false, Mockito-Default).
    // When
    service.register("a@x.de", "Ada");

    // Then: wartet auf Admin-Freigabe.
    assertThat(savedUser().approvedAt()).isNull();
  }

  @Test
  void register_throwsEmailAlreadyRegistered_whenEmailTaken() {
    // Given
    when(users.existsByEmail("a@x.de")).thenReturn(true);

    // When / Then — weder Konto noch Code.
    assertThatThrownBy(() -> service.register(" A@x.de", "Ada"))
        .isInstanceOf(EmailAlreadyRegisteredException.class);
    verify(users, never()).save(any(AppUser.class));
    verifyNoInteractions(loginCodes);
  }
}
