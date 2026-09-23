package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.PlatformAdminDirectory;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integrationstest des Plattform-Admin-Verzeichnisses gegen ein echtes Postgres (Issue #827): Der
 * Port ist verdrahtet, die Abfrage trifft die Rolle, und die Reihenfolge kommt aus der Umsetzung —
 * nicht aus der zufälligen Reihenfolge der Datenbank.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PlatformAdminDirectoryIT extends AbstractIntegrationTest {

  @Autowired private PlatformAdminDirectory directory;

  @Autowired private AppUserRepository users;

  @Test
  void withoutAdmins_returnsEmptyList() {
    // Given
    save("nur-nutzerin@example.org", PlatformRole.USER);

    // When / Then
    assertThat(directory.adminEmails()).isEmpty();
  }

  @Test
  void returnsOnlyAdminEmails_stablySorted() {
    // Given — Einfügereihenfolge bewusst gegen die erwartete Sortierung
    save("zora@example.org", PlatformRole.ADMIN);
    save("nutzerin@example.org", PlatformRole.USER);
    save("anna@example.org", PlatformRole.ADMIN);
    save("malte@example.org", PlatformRole.ADMIN);

    // When / Then
    assertThat(directory.adminEmails())
        .containsExactly("anna@example.org", "malte@example.org", "zora@example.org");
  }

  private void save(String email, PlatformRole role) {
    users.save(new AppUser(null, email, "argon2-hash", email, true, role));
  }
}
