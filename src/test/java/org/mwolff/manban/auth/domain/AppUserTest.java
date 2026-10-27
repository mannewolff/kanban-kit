package org.mwolff.manban.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Domänentests für die {@code with…}-Kopiermethoden und Prädikate von {@link AppUser}. */
class AppUserTest {

  private static AppUser user(PlatformRole role, @Nullable Instant approvedAt) {
    return new AppUser(1L, "a@x.de", "Ada", true, role, approvedAt, null, null);
  }

  @Test
  void effectivelyApproved_isTrue_forAdminWithoutApproval() {
    // Der Kern der Regel (Issue #556): Ein Plattform-Admin braucht keine Fremdfreigabe.
    assertThat(user(PlatformRole.ADMIN, null).effectivelyApproved()).isTrue();
  }

  @Test
  void effectivelyApproved_isTrue_forAdminWithApproval() {
    assertThat(user(PlatformRole.ADMIN, Instant.EPOCH).effectivelyApproved()).isTrue();
  }

  @Test
  void effectivelyApproved_isTrue_forApprovedUser() {
    assertThat(user(PlatformRole.USER, Instant.EPOCH).effectivelyApproved()).isTrue();
  }

  @Test
  void effectivelyApproved_isFalse_forPendingUser() {
    // Die einzige Kombination, die wartet — hier darf das Prädikat nicht durchlassen.
    assertThat(user(PlatformRole.USER, null).effectivelyApproved()).isFalse();
  }

  @Test
  void effectivelyApproved_doesNotChangeApproved() {
    // approved() bleibt die Aussage über den echten Freigabe-Zeitstempel: Die Admin-Übersicht
    // muss einen Admin ohne Zeitstempel weiterhin als solchen erkennen können.
    assertThat(user(PlatformRole.ADMIN, null).approved()).isFalse();
  }

  @Test
  void withDisplayName_changesOnlyDisplayName() {
    AppUser user =
        new AppUser(1L, "a@x.de", "Alt", true, PlatformRole.ADMIN, Instant.EPOCH, 9L, Instant.MAX);

    AppUser updated = user.withDisplayName("Neu");

    assertThat(updated.displayName()).isEqualTo("Neu");
    assertThat(updated.id()).isEqualTo(1L);
    assertThat(updated.email()).isEqualTo("a@x.de");
    assertThat(updated.emailVerified()).isTrue();
    assertThat(updated.platformRole()).isEqualTo(PlatformRole.ADMIN);
    assertThat(updated.approvedAt()).isEqualTo(Instant.EPOCH);
    assertThat(updated.approvedBy()).isEqualTo(9L);
    assertThat(updated.disabledAt()).isEqualTo(Instant.MAX);
  }

  @Test
  void convenienceConstructor_defaultsColorSchemeToSystem() {
    assertThat(user(PlatformRole.USER, null).colorScheme()).isEqualTo(ColorScheme.SYSTEM);
  }

  @Test
  void withColorScheme_changesOnlyColorScheme() {
    AppUser user =
        new AppUser(1L, "a@x.de", "Ada", true, PlatformRole.ADMIN, Instant.EPOCH, 9L, Instant.MAX);

    AppUser updated = user.withColorScheme(ColorScheme.DARK);

    assertThat(updated.colorScheme()).isEqualTo(ColorScheme.DARK);
    assertThat(updated).usingRecursiveComparison().ignoringFields("colorScheme").isEqualTo(user);
  }

  /** Jede Kopiermethode trägt die Farbwahl weiter (Issue #1911) — sonst ginge sie verloren. */
  @Test
  void withMethods_keepColorScheme() {
    AppUser user =
        new AppUser(
            1L,
            "a@x.de",
            "Ada",
            true,
            PlatformRole.USER,
            Instant.EPOCH,
            9L,
            null,
            ColorScheme.LIGHT);

    assertThat(user.withEmailVerified(false).colorScheme()).isEqualTo(ColorScheme.LIGHT);
    assertThat(user.withDisplayName("Neu").colorScheme()).isEqualTo(ColorScheme.LIGHT);
    assertThat(user.withPlatformRole(PlatformRole.ADMIN).colorScheme())
        .isEqualTo(ColorScheme.LIGHT);
    assertThat(user.withApproved(Instant.MAX, 2L).colorScheme()).isEqualTo(ColorScheme.LIGHT);
    assertThat(user.withDisabledAt(Instant.MAX).colorScheme()).isEqualTo(ColorScheme.LIGHT);
  }
}
