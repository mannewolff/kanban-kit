package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Die Karte, wie das fremde System sie liefert (Issue #2158, Plan #2152 A15). */
class FremdKarteTest {

  private static final Instant ZEIT = Instant.parse("2026-11-15T10:00:00Z");

  @Test
  void labels_sindEineUnveraenderlicheKopie() {
    List<String> labels = new ArrayList<>(List.of("Ready"));
    FremdKarte karte = new FremdKarte(1, "Paket", "Text", labels, false, ZEIT, ZEIT);

    labels.add("In review");

    assertThat(karte.labels()).containsExactly("Ready");
    assertThatThrownBy(() -> karte.labels().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
