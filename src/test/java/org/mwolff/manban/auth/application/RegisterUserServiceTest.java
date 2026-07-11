package org.mwolff.manban.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.EmailVerificationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Zeit-Test: das Ablaufdatum des Verifikations-Tokens ist Clock-Zeitpunkt plus TTL. */
class RegisterUserServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void register_setsTokenExpiryFromInjectedClockPlusTtl() {
        // Given
        AppUserRepository users = mock(AppUserRepository.class);
        EmailVerificationTokenRepository tokens = mock(EmailVerificationTokenRepository.class);
        VerificationMailer mailer = mock(VerificationMailer.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthProperties properties = new AuthProperties(null, Duration.ofHours(24), null, null, null, null);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(users.existsByEmail("a@x.de")).thenReturn(false);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));
        RegisterUserService service = new RegisterUserService(users, tokens, mailer, encoder, properties, clock);

        // When
        service.register("a@x.de", "pw", "Ada");

        // Then
        ArgumentCaptor<EmailVerificationToken> captor = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokens).save(captor.capture());
        assertThat(captor.getValue().expiresAt()).isEqualTo(FIXED.plus(Duration.ofHours(24)));
    }
}
