package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Unit-Tests der Plattform-Admin-Prüfung (Repository gemockt). */
class PlatformAdminCheckerTest {

  private AppUserRepository users;
  private PlatformAdminChecker checker;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    checker = new PlatformAdminChecker(users);
  }

  private static AppUser user(long id, PlatformRole role) {
    return new AppUser(id, "u" + id + "@x.de", "hash", "U" + id, true, role);
  }

  @Test
  void isActivePlatformAdmin_activeAdmin_returnsTrue() {
    // Given
    when(users.findById(1L)).thenReturn(Optional.of(user(1L, PlatformRole.ADMIN)));

    // When / Then
    assertThat(checker.isActivePlatformAdmin(1L)).isTrue();
  }

  @Test
  void isActivePlatformAdmin_disabledAdmin_returnsFalse() {
    // Given
    AppUser disabled = user(1L, PlatformRole.ADMIN).withDisabledAt(Instant.EPOCH);
    when(users.findById(1L)).thenReturn(Optional.of(disabled));

    // When / Then
    assertThat(checker.isActivePlatformAdmin(1L)).isFalse();
  }

  @Test
  void isActivePlatformAdmin_user_returnsFalse() {
    // Given
    when(users.findById(2L)).thenReturn(Optional.of(user(2L, PlatformRole.USER)));

    // When / Then
    assertThat(checker.isActivePlatformAdmin(2L)).isFalse();
  }

  @Test
  void isActivePlatformAdmin_unknownId_returnsFalse() {
    // Given
    when(users.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(checker.isActivePlatformAdmin(99L)).isFalse();
  }

  @Test
  void isPlatformAdmin_disabledAdmin_staysTrue() {
    // Given — die bestehende Prüfung fragt nur die Rolle, nicht die Sperre
    AppUser disabled = user(1L, PlatformRole.ADMIN).withDisabledAt(Instant.EPOCH);
    when(users.findById(1L)).thenReturn(Optional.of(disabled));

    // When / Then
    assertThat(checker.isPlatformAdmin(1L)).isTrue();
  }

  @Test
  void isPlatformAdmin_userOrUnknown_returnsFalse() {
    // Given
    when(users.findById(2L)).thenReturn(Optional.of(user(2L, PlatformRole.USER)));
    when(users.findById(99L)).thenReturn(Optional.empty());

    // When / Then
    assertThat(checker.isPlatformAdmin(2L)).isFalse();
    assertThat(checker.isPlatformAdmin(99L)).isFalse();
  }
}
