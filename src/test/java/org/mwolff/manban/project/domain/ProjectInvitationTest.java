package org.mwolff.manban.project.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verhaltenstests der Ablauf-/Annahme-Logik einer {@link ProjectInvitation}. */
class ProjectInvitationTest {

    private static final Instant EXPIRES = Instant.parse("2026-01-02T03:04:05Z");

    private static ProjectInvitation invitation(Instant acceptedAt) {
        return new ProjectInvitation(
                1L, 3L, "invitee@example.org", ProjectRole.MEMBER, "hash", EXPIRES, acceptedAt, 7L);
    }

    @Test
    void isAccepted_returnsTrue_whenAcceptedAtSet() {
        // Given / When / Then
        assertThat(invitation(EXPIRES).isAccepted()).isTrue();
    }

    @Test
    void isAccepted_returnsFalse_whenAcceptedAtNull() {
        // Given / When / Then
        assertThat(invitation(null).isAccepted()).isFalse();
    }

    @Test
    void isExpired_returnsTrue_whenExpiresBeforeNow() {
        // Given / When / Then
        assertThat(invitation(null).isExpired(EXPIRES.plusSeconds(1))).isTrue();
    }

    @Test
    void isExpired_returnsFalse_whenExpiresAfterNow() {
        // Given / When / Then
        assertThat(invitation(null).isExpired(EXPIRES.minusSeconds(1))).isFalse();
    }

    @Test
    void isExpired_returnsFalse_atExactExpiry() {
        // Given / When / Then — isBefore ist strikt, der Ablaufmoment gilt noch nicht als abgelaufen.
        assertThat(invitation(null).isExpired(EXPIRES)).isFalse();
    }

    @Test
    void markAccepted_setsAcceptedAt() {
        // Given / When / Then
        assertThat(invitation(null).markAccepted(EXPIRES).acceptedAt()).isEqualTo(EXPIRES);
    }
}
