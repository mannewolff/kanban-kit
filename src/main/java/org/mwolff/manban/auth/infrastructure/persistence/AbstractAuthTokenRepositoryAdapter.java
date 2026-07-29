package org.mwolff.manban.auth.infrastructure.persistence;

import java.time.Instant;
import java.util.Optional;
import org.mwolff.manban.auth.application.SingleUseTokenRepository;

/**
 * Gemeinsame Adapter-Basis der beiden Einmal-Token-Ports (Passwort-Reset, E-Mail-Verifikation). Sie
 * hält die Persistenz-Logik, die für beide Tokenarten identisch ist; die erbenden Adapter steuern
 * nur die Umwandlung zwischen Domänentyp und Entity bei.
 *
 * @param <D> Domänentyp des Tokens
 * @param <E> zugehörige JPA-Entity
 */
abstract class AbstractAuthTokenRepositoryAdapter<D, E extends AbstractAuthTokenEntity>
    implements SingleUseTokenRepository {

  private final AuthTokenJpaRepository<E> jpa;

  protected AbstractAuthTokenRepositoryAdapter(AuthTokenJpaRepository<E> jpa) {
    this.jpa = jpa;
  }

  protected abstract E toEntity(D token);

  protected abstract D toDomain(E entity);

  // PMD.PublicMemberInNonPublicType: muss public sein — die Methode erfuellt save() der
  // oeffentlichen Ports PasswordResetTokenRepository/EmailVerificationTokenRepository, die die
  // erbenden Adapter implementieren. Die Klasse selbst bleibt bewusst package-private.
  @SuppressWarnings("PMD.PublicMemberInNonPublicType")
  public D save(D token) {
    return toDomain(jpa.save(toEntity(token)));
  }

  /**
   * {@inheritDoc}
   *
   * <p>Der vorgelagerte Lesezugriff dient allein dem Ermitteln der Benutzer-ID; die
   * Einmalverwendung entscheidet ausschließlich {@link AuthTokenJpaRepository#markUsedIfUnused} —
   * dessen Zeilenzahl ist die einzige Quelle der Wahrheit darüber, wer das Token bekommen hat.
   */
  @Override
  public Optional<Long> consume(String tokenHash, Instant now) {
    Optional<Long> userId = jpa.findByTokenHash(tokenHash).map(AbstractAuthTokenEntity::getUserId);
    if (userId.isEmpty()) {
      return Optional.empty();
    }
    return jpa.markUsedIfUnused(tokenHash, now) == 1 ? userId : Optional.empty();
  }
}
