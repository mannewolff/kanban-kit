package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verhaltenstests der Karten-Abhängigkeiten an ihren Ports (Issue #1435).
 *
 * <p>Als Unit-Test, weil PIT ausschliesslich Unit-Tests misst: Ohne ihn überlebte der Mutant, der
 * {@link KartenAbhaengigkeiten#abhaengigkeitenVon(long)} eine leere Liste liefern lässt.
 */
class KartenAbhaengigkeitenTest {

  private static final long KARTE = 7L;

  @Test
  void abhaengigkeitenVonReichtDieNummernDesRepositoriesDurch() {
    CardDependencyRepository dependencies = mock(CardDependencyRepository.class);
    when(dependencies.findByCardId(KARTE)).thenReturn(List.of(3, 7));
    KartenAbhaengigkeiten abhaengigkeiten =
        new KartenAbhaengigkeiten(dependencies, mock(CardRepository.class));

    assertThat(abhaengigkeiten.abhaengigkeitenVon(KARTE)).containsExactly(3, 7);
  }
}
