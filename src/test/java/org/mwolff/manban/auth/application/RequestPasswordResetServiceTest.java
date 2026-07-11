package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Zeit-Test: das Ablaufdatum des Reset-Tokens ist Clock-Zeitpunkt plus TTL. */
class RequestPasswordResetServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void requestReset_setsTokenExpiryFromInjectedClockPlusTtl() {
        // Given
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        PasswordResetMailer mailer = mock(PasswordResetMailer.class);
        AuthProperties properties = new AuthProperties(null, null, null, null, null, Duration.ofHours(1));
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(users.findByEmail("a@x.de")).thenReturn(Optional.of(
                new AppUser(2L, "a@x.de", "hash", "Ada", true, PlatformRole.USER)));
        RequestPasswordResetService service =
                new RequestPasswordResetService(users, tokens, mailer, properties, clock);

        // When
        service.requestReset("a@x.de");

        // Then
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(captor.capture());
        assertThat(captor.getValue().expiresAt()).isEqualTo(FIXED.plus(Duration.ofHours(1)));
    }
}
