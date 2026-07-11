package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Unit-Tests für {@link Identifiable#requireId()} (Issue #0080). */
class IdentifiableTest {

  private record Fixture(@Nullable Long id) implements Identifiable {}

  @Test
  void requireId_liefertIdEinerPersistiertenInstanz() {
    // Given
    Identifiable persistiert = new Fixture(42L);

    // When / Then
    assertThat(persistiert.requireId()).isEqualTo(42L);
  }

  @Test
  void requireId_wirftFuerNichtPersistierteInstanz() {
    // Given
    Identifiable neu = new Fixture(null);

    // When / Then
    assertThatIllegalStateException()
        .isThrownBy(neu::requireId)
        .withMessage("Instanz ist noch nicht persistiert (keine ID)");
  }
}
