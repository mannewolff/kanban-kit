package org.mwolff.manban.accesstoken.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Verhaltenstests der Token-Bindung eines {@link KanbanPrincipal}. */
class KanbanPrincipalTest {

  @Test
  void isBound_returnsTrue_whenProjectAndBoardSet() {
    // Given / When / Then
    assertThat(new KanbanPrincipal(1L, 2L, 5L, 10L, "Token").isBound()).isTrue();
  }

  @Test
  void isBound_returnsFalse_whenBoardMissing() {
    // Given / When / Then
    assertThat(new KanbanPrincipal(1L, 2L, 5L, null, "Token").isBound()).isFalse();
  }

  @Test
  void isBound_returnsFalse_whenProjectMissing() {
    // Given / When / Then
    assertThat(new KanbanPrincipal(1L, 2L, null, 10L, "Token").isBound()).isFalse();
  }
}
