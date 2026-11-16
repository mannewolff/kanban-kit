package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Die Verbindung gibt ihren Zugang nie in einer Textform preis (Issue #2156, Plan #2152 A13). */
class VerbindungTest {

  @Test
  void dieTextformNenntDenZugangNicht() {
    Verbindung verbindung =
        new Verbindung(
            7L,
            FremdBoardArt.GITLAB,
            "https://gitlab.com",
            "gruppe/projekt",
            "v1:geheimer-zugang",
            "kit-bot",
            new StatusLabels("Backlog", "Ready", "In progress", "In review"),
            11L,
            Instant.parse("2026-11-15T09:00:00Z"),
            null,
            null,
            null);

    assertThat(verbindung.toString())
        .doesNotContain("geheimer-zugang")
        .contains("projectId=7")
        .contains("GITLAB")
        .contains("gruppe/projekt")
        .contains("boardId=11")
        .contains("zugangVerschluesselt=***");
  }
}
