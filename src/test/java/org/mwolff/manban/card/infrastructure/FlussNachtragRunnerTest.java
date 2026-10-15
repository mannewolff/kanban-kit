package org.mwolff.manban.card.infrastructure;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.FlussNachtrag;
import org.springframework.boot.DefaultApplicationArguments;

/** Der Runner startet den Nachtrag einmal und lässt den Start an einem Fehler nicht scheitern. */
class FlussNachtragRunnerTest {

  private final FlussNachtrag nachtrag = mock(FlussNachtrag.class);
  private final FlussNachtragRunner runner = new FlussNachtragRunner(nachtrag);

  @Test
  void startetDenNachtrag() {
    runner.run(new DefaultApplicationArguments());

    verify(nachtrag).nachtragen();
  }

  @Test
  void einFehlerImNachtragHaeltDenStartNichtAuf() {
    when(nachtrag.nachtragen()).thenThrow(new IllegalStateException("kaputt"));

    assertThatCode(() -> runner.run(new DefaultApplicationArguments())).doesNotThrowAnyException();
    verify(nachtrag).nachtragen();
  }
}
