package org.mwolff.manban.comment.infrastructure.vorschau;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Die Standard-Umsetzung meldet keine Vorschau (Issue #2210). */
class KeineVorschauTest {

  @Test
  void meldetKeineVorschau() {
    assertThat(new KeineVorschau().juengste(1L)).isEmpty();
  }
}
