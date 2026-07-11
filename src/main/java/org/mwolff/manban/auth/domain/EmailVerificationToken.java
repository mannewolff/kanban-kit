package org.mwolff.manban.auth.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.Identifiable;

/**
 * E-Mail-Verifikations-Token. Persistiert wird nur der Hash ({@code tokenHash}); das Klartext-Token
 * erhält der Nutzer einmalig per E-Mail.
 *
 * @param id technische ID; {@code null} vor der Persistierung
 * @param userId zugehöriger Benutzer
 * @param tokenHash SHA-256-Hash des Klartext-Tokens
 * @param expiresAt Ablaufzeitpunkt
 * @param usedAt Zeitpunkt der Einlösung; {@code null} solange ungenutzt
 */
public record EmailVerificationToken(
    @Nullable Long id, Long userId, String tokenHash, Instant expiresAt, @Nullable Instant usedAt)
    implements Identifiable {

  public boolean isUsed() {
    return usedAt != null;
  }

  public boolean isExpired(Instant now) {
    return expiresAt.isBefore(now);
  }

  /** Kopie mit gesetztem Einlöse-Zeitpunkt. */
  public EmailVerificationToken markUsed(Instant when) {
    return new EmailVerificationToken(id, userId, tokenHash, expiresAt, when);
  }
}
