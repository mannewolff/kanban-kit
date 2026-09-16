package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.OptionalLong;
import org.mwolff.manban.auth.application.SessionGenerations;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Adapter, der den {@link SessionGenerations}-Port auf Spring Data JPA abbildet (Issue #884). */
@Component
class SessionGenerationsAdapter implements SessionGenerations {

  private final AppUserJpaRepository jpa;

  SessionGenerationsAdapter(AppUserJpaRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public OptionalLong current(long userId) {
    return jpa.findSessionGeneration(userId).map(OptionalLong::of).orElseGet(OptionalLong::empty);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Eigene Transaktionsgrenze, weil eine schreibende Abfrage eine Transaktion braucht und die
   * Zusage des Ports sonst davon abhinge, ob der Aufrufer zufällig eine geöffnet hat. {@code
   * Propagation.REQUIRED} tritt einer vorhandenen bei — der Passwort-Reset bleibt damit ein
   * einziger, gemeinsam festgeschriebener Vorgang.
   */
  @Override
  @Transactional
  public void invalidateSessions(long userId) {
    jpa.bumpSessionGeneration(userId);
  }
}
