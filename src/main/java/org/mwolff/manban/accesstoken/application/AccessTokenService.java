package org.mwolff.manban.accesstoken.application;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.mwolff.manban.accesstoken.domain.AccessToken;
import org.mwolff.manban.common.token.TokenCryptoPort;
import org.mwolff.manban.common.token.TokenCryptoPort.GeneratedToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltung persönlicher API-Zugriffstokens: anlegen (Klartext einmalig),
 * auflisten, widerrufen und Auflösung eingehender {@code X-Kanban-Token}-Header.
 */
@Service
public class AccessTokenService {

    private final AccessTokenRepository tokens;
    private final TokenCryptoPort crypto;

    public AccessTokenService(AccessTokenRepository tokens, TokenCryptoPort crypto) {
        this.tokens = tokens;
        this.crypto = crypto;
    }

    /** Legt ein Token an und gibt den Klartext GENAU EINMAL zurück. */
    @Transactional
    public CreatedAccessToken create(long userId, String name) {
        GeneratedToken generated = crypto.generate();
        AccessToken saved = tokens.save(new AccessToken(
                null, userId, name, generated.hash(), name, Instant.now(), null, false));
        return new CreatedAccessToken(saved.id(), saved.name(), generated.plaintext());
    }

    @Transactional(readOnly = true)
    public List<AccessTokenView> list(long userId) {
        return tokens.findByUserId(userId).stream()
                .map(t -> new AccessTokenView(
                        t.id(), t.name(), t.createdAt(), t.lastUsedAt(), t.revoked()))
                .toList();
    }

    @Transactional
    public void revoke(long userId, long tokenId) {
        AccessToken token = tokens.findById(tokenId)
                .filter(t -> t.userId() == userId)
                .orElseThrow(AccessTokenNotFoundException::new);
        if (!token.revoked()) {
            tokens.save(token.asRevoked());
        }
    }

    /** Löst einen eingehenden Klartext-Header auf; leer bei unbekannt/widerrufen. */
    @Transactional
    public OptionalLong resolve(String plaintext) {
        return tokens.findByTokenHash(crypto.hash(plaintext))
                .filter(t -> !t.revoked())
                .map(t -> {
                    tokens.save(t.withLastUsedAt(Instant.now()));
                    return OptionalLong.of(t.userId());
                })
                .orElseGet(OptionalLong::empty);
    }

    /** Ergebnis der Erstellung — enthält den einmalig sichtbaren Klartext. */
    public record CreatedAccessToken(Long id, String name, String plaintext) {
    }

    /** Listen-/Detaildarstellung ohne Hash und ohne Klartext. */
    public record AccessTokenView(Long id, String name, Instant createdAt, Instant lastUsedAt, boolean revoked) {
    }
}
