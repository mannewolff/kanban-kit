package org.mwolff.manban.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Domänentests für die {@code with…}-Kopiermethoden von {@link AppUser}. */
class AppUserTest {

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
