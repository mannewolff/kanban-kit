package org.mwolff.manban.auth.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;

/**
 * Umsetzung des Plattform-Admin-Verzeichnisses (Issue #827): Sie gibt ausschließlich
 * E-Mail-Adressen heraus, und zwar stabil sortiert — ein Aufrufer, der daraus einen
 * Idempotenzschlüssel bildet, bekommt bei unveränderter Admin-Menge denselben Schlüssel.
 */
class PlatformAdminDirectoryAdapterTest {

  private final AppUserRepository users = mock(AppUserRepository.class);

  private final PlatformAdminDirectoryAdapter directory = new PlatformAdminDirectoryAdapter(users);

  @Test
  void withoutAdmins_returnsEmptyList() {
    // Given
    when(users.findByPlatformRole(PlatformRole.ADMIN)).thenReturn(List.of());

    // When / Then
    assertThat(directory.adminEmails()).isEmpty();
  }

  @Test
  void withOneAdmin_returnsItsEmail() {
    // Given
    when(users.findByPlatformRole(PlatformRole.ADMIN))
        .thenReturn(List.of(admin(1L, "admin@example.org")));

    // When / Then
    assertThat(directory.adminEmails()).containsExactly("admin@example.org");
  }

  @Test
  void withSeveralAdmins_returnsStablySortedEmails() {
    // Given — bewusst unsortiert, damit ein Wegfall der Sortierung auffällt
    when(users.findByPlatformRole(PlatformRole.ADMIN))
        .thenReturn(
            List.of(
                admin(3L, "zora@example.org"),
                admin(1L, "anna@example.org"),
                admin(2L, "malte@example.org")));

    // When / Then
    assertThat(directory.adminEmails())
        .containsExactly("anna@example.org", "malte@example.org", "zora@example.org");
  }

  @Test
  void asksTheRepositoryForTheAdminRoleOnly() {
    // Given
    when(users.findByPlatformRole(PlatformRole.ADMIN)).thenReturn(List.of());

    // When
    directory.adminEmails();

    // Then — eine andere Rolle hier hieße: der Alarm ginge an die Falschen
    verify(users).findByPlatformRole(PlatformRole.ADMIN);
  }

  private static AppUser admin(long id, String email) {
    return new AppUser(id, email, "argon2-hash", "Admin " + id, true, PlatformRole.ADMIN);
  }
}
