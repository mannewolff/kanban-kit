package org.mwolff.manban.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** Spiegeltext des {@link Comment}-Records (Issue #2162, Plan #2152 A19). */
class CommentTest {

  private static final Instant ANGELEGT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant SPAETER = Instant.parse("2026-01-02T00:00:00Z");

  private static Comment kommentar(String origin) {
    return new Comment(
        7L,
        1L,
        null,
        "kit",
        "## Laufstand\nalt",
        ANGELEGT,
        ANGELEGT,
        null,
        origin,
        null,
        null,
        null,
        null,
        "ext-7");
  }

  @Test
  void spiegeltextEinerKitMeldungMitSpaetererAenderungGiltAlsVonHand() {
    Comment neu = kommentar("TOKEN").withSpiegelText("neu", SPAETER);

    assertThat(neu.body()).isEqualTo("neu");
    assertThat(neu.updatedAt()).isEqualTo(SPAETER);
    assertThat(neu.vonHandGeaendertAm()).isEqualTo(SPAETER);
    assertThat(neu.vonHandKopfzeile()).isEqualTo("## Laufstand");
    assertThat(neu.externKennung()).isEqualTo("ext-7");
  }

  @Test
  void spiegeltextEinerKitMeldungOhneSpaetereAenderungBleibtUngekennzeichnet() {
    Comment neu = kommentar("TOKEN").withSpiegelText("neu", ANGELEGT);

    assertThat(neu.body()).isEqualTo("neu");
    assertThat(neu.vonHandGeaendertAm()).isNull();
    assertThat(neu.vonHandKopfzeile()).isNull();
  }

  @Test
  void spiegeltextEinesFremdenKommentarsBleibtUngekennzeichnet() {
    Comment neu = kommentar("SESSION").withSpiegelText("neu", SPAETER);

    assertThat(neu.body()).isEqualTo("neu");
    assertThat(neu.updatedAt()).isEqualTo(SPAETER);
    assertThat(neu.vonHandGeaendertAm()).isNull();
    assertThat(neu.vonHandKopfzeile()).isNull();
  }
}
