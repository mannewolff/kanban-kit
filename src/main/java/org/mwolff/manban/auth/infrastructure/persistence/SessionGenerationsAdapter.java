package org.mwolff.manban.auth.infrastructure.persistence;

import java.util.OptionalLong;
import org.mwolff.manban.auth.application.SessionGenerations;
import org.springframework.stereotype.Component;

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
}
