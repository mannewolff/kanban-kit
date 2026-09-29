package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** Die kanonischen Prozessnamen des {@link CardStatus} (Plan #1294, E24). */
class CardStatusTest {

  /**
   * Wortgleich zu {@code .claude/workflow.config.json} → {@code columns}: Nur dann findet {@code
   * wirksamkeit.mjs} über {@code spaltenZuordnung} einen Schlüssel für den Statusnamen.
   */
  @Test
  void anzeigename_liefertDieFuenfKanonischenProzessnamen() {
    assertThat(Arrays.stream(CardStatus.values()).map(CardStatus::anzeigename))
        .containsExactly("Backlog", "Ready", "In progress", "In review", "Done");
  }

  /** Der Konstantenname ist der gespeicherte Wert — die Check-Bedingung aus V46 hängt daran. */
  @Test
  void konstantennamen_sindDieGespeichertenWerte() {
    assertThat(Arrays.stream(CardStatus.values()).map(CardStatus::name))
        .containsExactly("BACKLOG", "READY", "IN_PROGRESS", "IN_REVIEW", "DONE");
  }
}
