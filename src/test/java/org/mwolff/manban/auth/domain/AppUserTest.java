package org.mwolff.manban.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Domänentests für die {@code with…}-Kopiermethoden und Prädikate von {@link AppUser}. */
class AppUserTest {

  private static AppUser user(PlatformRole role, @Nullable Instant approvedAt) {
    return new AppUser(1L, "a@x.de", "hash", "Ada", true, role, approvedAt, null, null);
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
        new AppUser(
            1L, "a@x.de", "hash", "Alt", true, PlatformRole.ADMIN, Instant.EPOCH, 9L, Instant.MAX);

    AppUser updated = user.withDisplayName("Neu");

    assertThat(updated.displayName()).isEqualTo("Neu");
    assertThat(updated.id()).isEqualTo(1L);
    assertThat(updated.email()).isEqualTo("a@x.de");
    assertThat(updated.passwordHash()).isEqualTo("hash");
    assertThat(updated.emailVerified()).isTrue();
    assertThat(updated.platformRole()).isEqualTo(PlatformRole.ADMIN);
    assertThat(updated.approvedAt()).isEqualTo(Instant.EPOCH);
    assertThat(updated.approvedBy()).isEqualTo(9L);
    assertThat(updated.disabledAt()).isEqualTo(Instant.MAX);
  }
}
