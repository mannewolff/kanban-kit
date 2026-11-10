package org.mwolff.manban.accesstoken.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Verhaltenstests der optionalen Projekt-/Board-Bindung eines {@link AccessToken}. */
class AccessTokenTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private static AccessToken token(Long projectId, Long boardId) {
    return new AccessToken(1L, 7L, projectId, boardId, "CI", "hash", "CI", FIXED, null, false);
  }

  @Test
  void isBound_returnsTrue_whenProjectAndBoardSet() {
    // Given / When / Then
    assertThat(token(5L, 10L).isBound()).isTrue();
  }

  @Test
  void isBound_returnsFalse_whenBoardMissing() {
    // Given / When / Then
    assertThat(token(5L, null).isBound()).isFalse();
  }

  @Test
  void isBound_returnsFalse_whenProjectMissing() {
    // Given / When / Then
    assertThat(token(null, 10L).isBound()).isFalse();
  }

  @Test
  void isBound_returnsFalse_whenUnbound() {
    // Given / When / Then
    assertThat(token(null, null).isBound()).isFalse();
  }

  @Test
  void art_isStandard_whenConstructedWithThePreviousParameters() {
    // Given / When / Then: bestehende Aufrufer bleiben unverändert (Plan #2100, E3).
    assertThat(token(5L, 10L).art()).isEqualTo(AccessTokenArt.STANDARD);
  }

  @Test
  void art_isKept_whenGivenExplicitly() {
    // Given
    AccessToken zugang =
        new AccessToken(
            1L, 7L, 5L, 10L, "App", "hash", "App", FIXED, null, false, AccessTokenArt.APP);

    // When / Then
    assertThat(zugang.art()).isEqualTo(AccessTokenArt.APP);
  }
}
