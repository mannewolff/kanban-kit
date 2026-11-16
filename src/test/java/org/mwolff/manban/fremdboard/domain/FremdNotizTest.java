package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Ein Kommentar an einer Karte im fremden System (Issue #2158, Plan #2152 A15). */
class FremdNotizTest {

  @Test
  void traegtAutorSowieAnlageUndAenderungszeit() {
    Instant angelegt = Instant.parse("2026-11-15T10:00:00Z");
    Instant geaendert = Instant.parse("2026-11-15T11:00:00Z");

    FremdNotiz notiz = new FremdNotiz("note-1", 7, "kit-bot", "## Laufstand", angelegt, geaendert);

    assertThat(notiz.autor()).isEqualTo("kit-bot");
    assertThat(notiz.angelegtAm()).isEqualTo(angelegt);
    assertThat(notiz.geaendertAm()).isEqualTo(geaendert);
  }
}
