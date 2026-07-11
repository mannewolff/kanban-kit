package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Zeit-Test: der Einlöse-Zeitpunkt des Verifikations-Tokens stammt aus der injizierten Clock. */
class VerifyEmailServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void verify_marksTokenUsedWithInjectedClock() {
        // Given
        AppUserRepository users = mock(AppUserRepository.class);
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        EmailVerificationToken valid = new EmailVerificationToken(
                1L, 2L, "hash", FIXED.plusSeconds(3600), null);
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(valid));
        when(users.findById(2L)).thenReturn(Optional.of(
                new AppUser(2L, "a@x.de", "hash", "Ada", false, PlatformRole.USER)));
        VerifyEmailService service = new VerifyEmailService(users, tokens, clock);

        // When
        service.verify("plaintext");

        // Then
        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokens).save(captor.capture());
        assertThat(captor.getValue().usedAt()).isEqualTo(FIXED);
    }
}
