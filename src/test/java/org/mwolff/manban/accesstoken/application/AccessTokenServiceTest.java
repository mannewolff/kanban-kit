package org.mwolff.manban.accesstoken.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.mwolff.manban.project.application.PermissionChecker;

/** Zeit-Test: der Anlege-Zeitstempel eines Tokens stammt aus der injizierten Clock. */
class AccessTokenServiceTest {

    private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    void create_setsCreatedAtFromInjectedClock() {
        // Given
        AccessTokenRepository tokens = mock(AccessTokenRepository.class);
        TokenCryptoPort crypto = mock(TokenCryptoPort.class);
        BoardRepository boards = mock(BoardRepository.class);
        PermissionChecker permissions = mock(PermissionChecker.class);
        Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
        when(crypto.generate()).thenReturn(new GeneratedToken("tk_plain", "hash"));
        when(tokens.save(any(AccessToken.class))).thenAnswer(inv -> inv.getArgument(0));
        AccessTokenService service = new AccessTokenService(tokens, crypto, boards, permissions, clock);

        // When
        service.create(1L, "CI", null, null);

        // Then
        ArgumentCaptor<AccessToken> captor = ArgumentCaptor.forClass(AccessToken.class);
        verify(tokens).save(captor.capture());
        assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
    }
}
