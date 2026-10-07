package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit-Tests der Regel „Kanban-Schlüssel einer Karte“ (Issue #1495): eigener Status, sonst der
 * Schlüssel der Spalte, sonst {@code BACKLOG}.
 */
class CardIngestServiceKanbanSchluesselTest {

  @Test
  void derEigeneStatusGehtDerSpalteVor() {
    assertThat(CardIngestService.kanbanSchluessel("READY", "Done")).isEqualTo("READY");
  }

  @Test
  void ohneStatusZaehltDieProzessspalte() {
    assertThat(CardIngestService.kanbanSchluessel(null, "In Review")).isEqualTo("IN_REVIEW");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"Wartet", "Zurückgestellt"})
  void ohneStatusInEinerEigenenSpalteGiltBacklog(String spaltenname) {
    assertThat(CardIngestService.kanbanSchluessel(null, spaltenname)).isEqualTo("BACKLOG");
  }

  @ParameterizedTest
  @CsvSource({"Backlog, BACKLOG", "In-Progress, IN_PROGRESS", "'  done  ', DONE"})
  void eineProzessspalteTraegtIhrenSchluessel(String spaltenname, String schluessel) {
    assertThat(CardIngestService.spaltenSchluessel(spaltenname)).contains(schluessel);
  }

  @Test
  void eineEigeneSpalteTraegtKeinenSchluessel() {
    assertThat(CardIngestService.spaltenSchluessel("Anstehend")).isEmpty();
  }
}
