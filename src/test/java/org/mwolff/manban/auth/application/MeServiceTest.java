package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.ProjectMembershipReader.Membership;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Verhaltenstests der Selbstauskunft {@code /api/me} (Mockito an den Ports). */
class MeServiceTest {

  private AppUserRepository users;
  private ProjectMembershipReader memberships;
  private MeService service;

  @BeforeEach
  void setUp() {
    users = mock(AppUserRepository.class);
    memberships = mock(ProjectMembershipReader.class);
    service = new MeService(users, memberships);
  }

  @Test
  void load_returnsUserIdentity_whenUserExists() {
    // Given
    when(users.findById(2L))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", true, PlatformRole.USER)));
    when(memberships.findByUserId(2L)).thenReturn(List.of());

    // When
    MeService.MeView view = service.load(2L);

    // Then
    assertThat(view.email()).isEqualTo("a@x.de");
  }

  @Test
  void load_includesMemberships_fromReader() {
    // Given
    when(users.findById(2L))
        .thenReturn(Optional.of(new AppUser(2L, "a@x.de", "hash", "Ada", true, PlatformRole.USER)));
    when(memberships.findByUserId(2L)).thenReturn(List.of(new Membership(7L, "OWNER")));

    // When
    MeService.MeView view = service.load(2L);

    // Then
    assertThat(view.memberships()).containsExactly(new Membership(7L, "OWNER"));
  }

  @Test
  void load_throwsInvalidCredentials_whenUserUnknown() {
    // Given
    when(users.findById(2L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.load(2L)).isInstanceOf(InvalidCredentialsException.class);
  }
}
