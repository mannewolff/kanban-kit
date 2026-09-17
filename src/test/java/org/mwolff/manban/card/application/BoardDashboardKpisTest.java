package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Prüft die Invariante zwischen Durchschnitt und Stichprobengröße im Kompaktkonstruktor. */
class BoardDashboardKpisTest {

  private static BoardDashboardKpis kpis(
      Long avgLead, int leadSamples, Long avgImplementation, int implementationSamples) {
    return new BoardDashboardKpis(
        List.of(),
        List.of(),
        avgLead,
        leadSamples,
        avgImplementation,
        implementationSamples,
        List.of());
  }

  @Test
  void accepts_averagesWithSamples_andEmptyMetricsWithoutSamples() {
    assertThatCode(() -> kpis(100L, 4, 200L, 3)).doesNotThrowAnyException();

    BoardDashboardKpis empty = kpis(null, 0, null, 0);

    assertThat(empty.avgLeadTimeSeconds()).isNull();
    assertThat(empty.leadTimeSampleCount()).isZero();
    assertThat(empty.avgImplementationSeconds()).isNull();
    assertThat(empty.implementationSampleCount()).isZero();
  }

  @Test
  void rejects_leadTimeAverageWithoutSamples() {
    assertThatThrownBy(() -> kpis(100L, 0, null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("leadTime");
  }

  @Test
  void rejects_missingLeadTimeAverageDespiteSamples() {
    assertThatThrownBy(() -> kpis(null, 3, null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("leadTime");
  }

  @Test
  void rejects_negativeLeadTimeSampleCount() {
    assertThatThrownBy(() -> kpis(100L, -1, null, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("leadTimeSampleCount");
  }

  @Test
  void rejects_implementationAverageWithoutSamples() {
    assertThatThrownBy(() -> kpis(null, 0, 200L, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("implementation");
  }

  @Test
  void rejects_missingImplementationAverageDespiteSamples() {
    assertThatThrownBy(() -> kpis(null, 0, null, 2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("implementation");
  }

  @Test
  void rejects_negativeImplementationSampleCount() {
    assertThatThrownBy(() -> kpis(null, 0, 200L, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("implementationSampleCount");
  }
}
