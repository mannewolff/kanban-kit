package org.mwolff.manban.auth.application;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Verhaltenstests der Plattform-Administration (Mockito am AppUserRepository-Port). */
class AdminServiceTest {

  private static final Instant NOW = Instant.parse("2026-07-13T10:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private AppUserRepository users;
  private PlatformAdminChecker platformAdminChecker;
  private AdminService service;

  private static AppUser user(long id, PlatformRole role) {
    return new AppUser(id, "u" + id + "@x.de", "hash", "U" + id, true, role);
  }

  /** Noch nicht freigegebener Benutzer (kanonischer Konstruktor, {@code approvedAt=null}). */
  private static AppUser pendingUser(long id) {
    return new AppUser(
        id, "u" + id + "@x.de", "hash", "U" + id, true, PlatformRole.USER, null, null);
  }

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    platformAdminChecker = new PlatformAdminChecker(users);
    service = new AdminService(users, CLOCK, platformAdminChecker);
  }

  @Test
  void isPlatformAdmin_returnsTrue_whenUserHasAdminRole() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));

    // When / Then
    assertThat(service.isPlatformAdmin(1L)).isTrue();
  }

  @Test
  void isPlatformAdmin_returnsFalse_whenUserIsPlainUser() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.USER)));

    // When / Then
    assertThat(service.isPlatformAdmin(1L)).isFalse();
  }

  @Test
  void isPlatformAdmin_returnsFalse_whenUserUnknown() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(service.isPlatformAdmin(1L)).isFalse();
  }

  @Test
  void listUsers_returnsAllUsers_whenActorIsAdmin() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findAll())
        .thenReturn(List.of(user(1, PlatformRole.ADMIN), user(2, PlatformRole.USER)));

    // When
    List<AdminService.UserView> result = service.listUsers(1L);

    // Then
    assertThat(result).hasSize(2);
  }

  @Test
  void listUsers_mapsUserFieldsIntoView() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findAll()).thenReturn(List.of(user(2, PlatformRole.USER)));

    // When
    List<AdminService.UserView> result = service.listUsers(1L);

    // Then
    assertThat(result)
        .singleElement()
        .extracting(AdminService.UserView::email)
        .isEqualTo("u2@x.de");
  }

  @Test
  void listUsers_throwsAdminAccessDenied_whenActorIsNotAdmin() {
    // Given
    when(users.findById(9L)).thenReturn(Optional.of(user(9, PlatformRole.USER)));

    // When / Then
    assertThatThrownBy(() -> service.listUsers(9L)).isInstanceOf(AdminAccessDeniedException.class);
  }

  @Test
  void listUsers_mapsApprovalTimestampIntoView() {
    // Given: freigegebener Nutzer (Bequem-Konstruktor => approvedAt gesetzt).
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findAll()).thenReturn(List.of(user(2, PlatformRole.USER)));

    // When
    List<AdminService.UserView> result = service.listUsers(1L);

    // Then
    assertThat(result).singleElement().extracting(AdminService.UserView::approvedAt).isNotNull();
  }

  @Test
  void approve_setsApprovedAtAndBy_whenTargetPending() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.of(pendingUser(2)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    AdminService.UserView view = service.approve(1L, 2L);

    // Then
    verify(users).save(captor.capture());
    assertThat(captor.getValue().approvedAt()).isEqualTo(NOW);
    assertThat(captor.getValue().approvedBy()).isEqualTo(1L);
    assertThat(view.approvedAt()).isEqualTo(NOW);
  }

  @Test
  void approve_isIdempotent_whenTargetAlreadyApproved() {
    // Given: Ziel ist bereits freigegeben (Bequem-Konstruktor).
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));

    // When
    AdminService.UserView view = service.approve(1L, 2L);

    // Then: kein erneutes Speichern, Freigabe unverändert.
    verify(users, never()).save(any(AppUser.class));
    assertThat(view.approvedAt()).isNotNull();
  }

  @Test
  void approve_throwsAdminAccessDenied_whenActorIsNotAdmin() {
    // Given
    when(users.findById(9L)).thenReturn(Optional.of(user(9, PlatformRole.USER)));

    // When / Then
    assertThatThrownBy(() -> service.approve(9L, 2L))
        .isInstanceOf(AdminAccessDeniedException.class);
  }

  @Test
  void approve_throwsUserNotFound_whenTargetUnknown() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.approve(1L, 2L)).isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void changePlatformRole_promotesUser_whenActorIsAdmin() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    AdminService.UserView view = service.changePlatformRole(1L, 2L, PlatformRole.ADMIN);

    // Then
    assertThat(view.platformRole()).isEqualTo(PlatformRole.ADMIN);
  }

  @Test
  void changePlatformRole_demotesAdmin_whenAnotherAdminRemains() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.ADMIN)));
    when(users.findAll())
        .thenReturn(List.of(user(1, PlatformRole.ADMIN), user(2, PlatformRole.ADMIN)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<AppUser> captor = ArgumentCaptor.forClass(AppUser.class);
    service.changePlatformRole(1L, 2L, PlatformRole.USER);

    // Then
    verify(users).save(captor.capture());
    assertThat(captor.getValue().platformRole()).isEqualTo(PlatformRole.USER);
  }

  @Test
  void changePlatformRole_throwsAdminAccessDenied_whenActorIsNotAdmin() {
    // Given
    when(users.findById(9L)).thenReturn(Optional.of(user(9, PlatformRole.USER)));

    // When / Then
    assertThatThrownBy(() -> service.changePlatformRole(9L, 2L, PlatformRole.ADMIN))
        .isInstanceOf(AdminAccessDeniedException.class);
  }

  @Test
  void changePlatformRole_throwsUserNotFound_whenTargetUnknown() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.changePlatformRole(1L, 2L, PlatformRole.ADMIN))
        .isInstanceOf(UserNotFoundException.class);
  }

  @Test
  void changePlatformRole_keepsAdmin_whenNewRoleAlsoAdmin() {
    // Given: Ziel ist Admin, neue Rolle ist ebenfalls Admin -> kein Aussperr-Schutz nötig
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.ADMIN)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    AdminService.UserView view = service.changePlatformRole(1L, 2L, PlatformRole.ADMIN);

    // Then
    assertThat(view.platformRole()).isEqualTo(PlatformRole.ADMIN);
  }

  @Test
  void changePlatformRole_demotesAdmin_whenAnotherAdminAmongNonAdmins() {
    // Given: mehrere Admins (neben Nicht-Admins) -> Degradierung erlaubt
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.ADMIN)));
    when(users.findAll())
        .thenReturn(
            List.of(
                user(1, PlatformRole.ADMIN),
                user(2, PlatformRole.ADMIN),
                user(3, PlatformRole.USER)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    AdminService.UserView view = service.changePlatformRole(1L, 2L, PlatformRole.USER);

    // Then
    assertThat(view.platformRole()).isEqualTo(PlatformRole.USER);
  }

  @Test
  void changePlatformRole_throwsLastAdmin_whenDemotingSoleAdmin() {
    // Given
    when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.ADMIN)));
    when(users.findAll()).thenReturn(List.of(user(2, PlatformRole.ADMIN)));

    // When / Then
    assertThatThrownBy(() -> service.changePlatformRole(2L, 2L, PlatformRole.USER))
        .isInstanceOf(LastAdminException.class);
  }

  @Test
  void changePlatformRole_succeeds_whenTargetIsNotAdmin_evenIfSoleAdminGuardWouldTrigger() {
    // Given: Ziel ist KEIN Admin (erste Guard-Bedingung false); neue Rolle nicht Admin und
    // nur ein Admin im System (die beiden anderen Bedingungen true). Der Aussperr-Schutz darf
    // NICHT greifen — ein Umgehen der „target ist Admin"-Bedingung (Mutant) würde hier fälschlich
    // LastAdmin werfen.
    when(users.findById(1L)).thenReturn(Optional.of(user(1, PlatformRole.ADMIN)));
    when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.USER)));
    when(users.findAll()).thenReturn(List.of(user(1, PlatformRole.ADMIN)));
    when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    AdminService.UserView view = service.changePlatformRole(1L, 2L, PlatformRole.USER);

    // Then
    assertThat(view.platformRole()).isEqualTo(PlatformRole.USER);
  }

  @Test
  void changePlatformRole_throwsLastAdmin_whenSoleAdminAmongManyUsers() {
    // Given: nur EIN Admin, aber mehrere Nutzer. adminCount() muss genau die Admins zählen —
    // eine Zählung aller Nutzer (Mutant am Filter-Prädikat) ergäbe >1 und würde den
    // Aussperr-Schutz fälschlich aushebeln.
    when(users.findById(2L)).thenReturn(Optional.of(user(2, PlatformRole.ADMIN)));
    when(users.findAll())
        .thenReturn(
            List.of(
                user(2, PlatformRole.ADMIN),
                user(3, PlatformRole.USER),
                user(4, PlatformRole.USER)));

    // When / Then
    assertThatThrownBy(() -> service.changePlatformRole(2L, 2L, PlatformRole.USER))
        .isInstanceOf(LastAdminException.class);
  }
}
