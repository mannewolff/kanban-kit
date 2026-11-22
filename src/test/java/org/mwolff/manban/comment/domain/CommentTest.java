package org.mwolff.manban.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

  private static Comment vonDerKi() {
    return new Comment(
        7L,
        1L,
        3L,
        "Rechenzentrum",
        "## Gespräch\nHallo",
        ANGELEGT,
        ANGELEGT,
        null,
        "TOKEN",
        "anbieter",
        null,
        null,
        null,
        null,
        "STANDARD",
        true);
  }

  @Test
  void zugangsartUndKiBleibenBeimAendernDesTexts() {
    // Issue #2208, Plan #2206 E6: Der Absender steht beim Anlegen fest.
    Comment neu = vonDerKi().withBody("## Gespräch\nNeu", null, "SESSION", SPAETER);

    assertThat(neu.zugangArt()).isEqualTo("STANDARD");
    assertThat(neu.vonKi()).isTrue();
  }

  @Test
  void zugangsartUndKiBleibenBeimSpiegelnUndLoeschen() {
    Comment gespiegelt = vonDerKi().withSpiegelText("neu", SPAETER);
    Comment geloescht = vonDerKi().alsImFremdenSystemGeloescht(SPAETER);

    assertThat(gespiegelt.zugangArt()).isEqualTo("STANDARD");
    assertThat(gespiegelt.vonKi()).isTrue();
    assertThat(geloescht.zugangArt()).isEqualTo("STANDARD");
    assertThat(geloescht.vonKi()).isTrue();
  }

  @Test
  void einGespiegelterKommentarHatKeineZugangsartUndIstNichtVonDerKi() {
    Comment neu = Comment.gespiegelt(1L, "ext-1", "kit", "## Gespräch", ANGELEGT, ANGELEGT, true);

    assertThat(neu.zugangArt()).isNull();
    assertThat(neu.vonKi()).isFalse();
  }

  @Test
  void dieBisherigenParameterlistenLassenZugangsartUndKiLeer() {
    Comment kurz = new Comment(1L, 1L, 1L, "Ada", "x", ANGELEGT, ANGELEGT, null, null, null, null);

    assertThat(kommentar("TOKEN").zugangArt()).isNull();
    assertThat(kommentar("TOKEN").vonKi()).isFalse();
    assertThat(kurz.zugangArt()).isNull();
    assertThat(kurz.vonKi()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {"## Antwort", "## Answer", "\n\n  ## antwort\nx", "## ANSWER zu 1"})
  void kopfIstAntwort_ersteNichtleereZeileBeginntMitDemKopf(String text) {
    assertThat(Comment.kopfIstAntwort(text)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "  \n ", "Hallo", "## Gespräch\n## Antwort", "x ## Antwort", "## Antwor"})
  void kopfIstAntwort_sonstNicht(String text) {
    assertThat(Comment.kopfIstAntwort(text)).isFalse();
  }
}
