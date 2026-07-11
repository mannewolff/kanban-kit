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
import org.mwolff.manban.auth.domain.PasswordResetToken;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Zeit-Test: der Einlöse-Zeitpunkt des Reset-Tokens stammt aus der injizierten Clock. */
class ResetPasswordServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void reset_marksTokenUsedWithInjectedClock() {
        // Given
        AppUserRepository users = mock(AppUserRepository.class);
        PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        PasswordResetToken valid = new PasswordResetToken(1L, 2L, "hash", FIXED.plusSeconds(3600), null);
        when(tokens.findByTokenHash(anyString())).thenReturn(Optional.of(valid));
        when(users.findById(2L)).thenReturn(Optional.of(
                new AppUser(2L, "a@x.de", "hash", "Ada", true, PlatformRole.USER)));
        when(encoder.encode(anyString())).thenReturn("newHash");
        ResetPasswordService service = new ResetPasswordService(users, tokens, encoder, clock);

        // When
        service.reset("plaintext", "newPw");

        // Then
        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(captor.capture());
        assertThat(captor.getValue().usedAt()).isEqualTo(FIXED);
    }
}
