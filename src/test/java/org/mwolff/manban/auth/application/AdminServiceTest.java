package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Verhaltenstests der Plattform-Administration (Mockito am AppUserRepository-Port). */
class AdminServiceTest {

  private AppUserRepository users;
  private AdminService service;

  private static AppUser user(long id, PlatformRole role) {
    return new AppUser(id, "u" + id + "@x.de", "hash", "U" + id, true, role);
  }

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    service = new AdminService(users);
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
}
