package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardSearchService.KommentarFund;
import org.mwolff.manban.card.application.CardSearchService.Suchanfrage;
import org.mwolff.manban.card.application.KartenBestandSuche.Anfrage;
import org.mwolff.manban.card.application.KartenBestandSuche.Auszug;
import org.mwolff.manban.card.application.KartenBestandSuche.Seite;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;

/**
 * Unit-Tests der Textsuche in der Bestandssuche (Plan #1574, E3–E5): Filter, Vorrang der
 * Fundstellen und das Fenster des Auszugs. Den Abgleich selbst macht die Datenbank; hier stehen
 * seine Ergebnisse als Fundstellen-Maps.
 */
class KartenBestandSucheTextTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

  private static Card karte(long id, int number, String title) {
    return karte(id, number, title, "Text " + number);
  }

  private static Card karte(long id, int number, String title, @Nullable String description) {
    return new Card(
        id,
        10L,
        100L,
        number,
        title,
        description,
        0,
        false,
        null,
        null,
        T0,
        T0,
        CardType.CARD,
        null,
        null,
        null,
        1L,
        null,
        null,
        null,
        null);
  }

  private static Anfrage alle(int limit) {
    return new Anfrage(null, Set.of(), null, null, null, null, limit);
  }

  private static List<Integer> nummern(Seite seite) {
    return seite.treffer().stream().map(t -> t.karte().number()).toList();
  }

  // --- Textfilter und Fundstelle (E3, E4) -----------------------------------

  private static Anfrage text(String q) {
    return new Anfrage(q, Set.of(), null, null, null, null, 50);
  }

  @Test
  void pruefe_trimmtDenSuchbegriff_leerHeisstKeinTextfilter() {
    assertThat(
            KartenBestandSuche.pruefe(
                    new Suchanfrage("  Archiv \t", Set.of(), null, null, null, null, 50))
                .q())
        .isEqualTo("Archiv");
    assertThat(
            KartenBestandSuche.pruefe(new Suchanfrage(" \n ", Set.of(), null, null, null, null, 50))
                .q())
        .isNull();
    assertThat(
            KartenBestandSuche.pruefe(new Suchanfrage(null, Set.of(), null, null, null, null, 50))
                .q())
        .isNull();
  }

  @Test
  void text_liefertNurKartenMitFundstelle() {
    List<Card> karten = List.of(karte(1L, 1, "A"), karte(2L, 2, "B"), karte(3L, 3, "C"));

    Seite seite =
        KartenBestandSuche.suche(
            karten,
            text("x"),
            Map.of(1L, KartenBestandSuche.TEXT),
            Map.of(3L, new KommentarFund(9L, "x")));

    assertThat(nummern(seite)).containsExactly(3, 1);
  }

  @Test
  void text_ohneSuchbegriff_filtertNicht() {
    List<Card> karten = List.of(karte(1L, 1, "A"), karte(2L, 2, "B"));

    Seite seite = KartenBestandSuche.suche(karten, alle(50), Map.of(), Map.of());

    assertThat(nummern(seite)).containsExactly(2, 1);
  }

  @Test
  void text_titelHatVorrangVorTextUndKommentar() {
    Card k = karte(1L, 1, "Archiv lesen", "Archiv im Text");

    Auszug auszug =
        KartenBestandSuche.suche(
                List.of(k),
                text("archiv"),
                Map.of(1L, KartenBestandSuche.TITEL),
                Map.of(1L, new KommentarFund(9L, "Archiv im Kommentar")))
            .treffer()
            .getFirst()
            .auszug();

    assertThat(auszug).isEqualTo(new Auszug("Archiv lesen", "TITLE", null));
  }

  @Test
  void text_textHatVorrangVorKommentar() {
    Card k = karte(1L, 1, "A", "Zeile\nmit Archiv");

    Auszug auszug =
        KartenBestandSuche.suche(
                List.of(k),
                text("archiv"),
                Map.of(1L, KartenBestandSuche.TEXT),
                Map.of(1L, new KommentarFund(9L, "Archiv im Kommentar")))
            .treffer()
            .getFirst()
            .auszug();

    assertThat(auszug).isEqualTo(new Auszug("Zeile mit Archiv", "BODY", null));
  }

  @Test
  void text_nurImKommentar_nenntDenKommentar() {
    Card k = karte(1L, 1, "A");

    Auszug auszug =
        KartenBestandSuche.suche(
                List.of(k),
                text("review"),
                Map.of(),
                Map.of(1L, new KommentarFund(9L, "Ein Review")))
            .treffer()
            .getFirst()
            .auszug();

    assertThat(auszug).isEqualTo(new Auszug("Ein Review", "COMMENT", 9L));
  }

  @Test
  void text_unbekannteFundstelle_faelltAufKommentarUndDannAufDenAnfangZurueck() {
    Card k = karte(1L, 1, "A", "Anfang");

    Auszug auszug =
        KartenBestandSuche.suche(List.of(k), text("x"), Map.of(1L, "ANDERS"), Map.of())
            .treffer()
            .getFirst()
            .auszug();

    assertThat(auszug).isEqualTo(new Auszug("Anfang", "BODY", null));
  }

  // --- Auszug-Fenster (E5) --------------------------------------------------

  @Test
  void fenster_kurzerText_bleibtGanz() {
    assertThat(KartenBestandSuche.fenster("Ein kurzer\nText", "text")).isEqualTo("Ein kurzer Text");
  }

  @Test
  void fenster_ohneText_istLeer() {
    assertThat(KartenBestandSuche.fenster(null, "x")).isEmpty();
  }

  @Test
  void fenster_ohneSuchbegriff_istDerAnfang() {
    assertThat(KartenBestandSuche.fenster("a".repeat(300), null)).isEqualTo("a".repeat(200));
  }

  @Test
  void fenster_mitte_hatBeideRaender_undHoechstens200Zeichen() {
    String text = "a".repeat(300) + "XY" + "b".repeat(300);

    String fenster = KartenBestandSuche.fenster(text, "xy");

    assertThat(fenster).isEqualTo("…" + "a".repeat(98) + "XY" + "b".repeat(98) + "…");
    assertThat(fenster).hasSize(200);
  }

  @Test
  void fenster_amAnfang_hatNurDenRechtenRand() {
    String text = "a".repeat(50) + "XY" + "b".repeat(300);

    assertThat(KartenBestandSuche.fenster(text, "xy"))
        .isEqualTo("a".repeat(50) + "XY" + "b".repeat(147) + "…");
  }

  @Test
  void fenster_amEnde_hatNurDenLinkenRand() {
    String text = "a".repeat(300) + "XY";

    assertThat(KartenBestandSuche.fenster(text, "xy")).isEqualTo("…" + "a".repeat(197) + "XY");
  }

  @Test
  void fenster_genau201Zeichen_wirdGekuerzt() {
    String text = "XY" + "b".repeat(199);

    assertThat(KartenBestandSuche.fenster(text, "xy")).isEqualTo("XY" + "b".repeat(197) + "…");
  }

  @Test
  void fenster_findetUnabhaengigVonGrossUndKleinschreibung() {
    String text = "a".repeat(300) + "Stichwort" + "b".repeat(300);

    assertThat(KartenBestandSuche.fenster(text, "STICHWORT"))
        .startsWith("…" + "a".repeat(94) + "Stichwort");
  }

  @Test
  void fenster_machtZeilenumbruecheZuLeerzeichen() {
    String text = "a".repeat(300) + "\r\nXY\n" + "b".repeat(300);

    assertThat(KartenBestandSuche.fenster(text, "xy"))
        .isEqualTo("…" + "a".repeat(97) + " XY " + "b".repeat(97) + "…");
  }

  @Test
  void fenster_stelleNichtGefunden_istDerAnfang() {
    // Die Datenbank fand "x\ny" im Rohtext; im einzeiligen Auszug steht dort "x y".
    String text = "a".repeat(300) + "x\ny" + "b".repeat(300);

    assertThat(KartenBestandSuche.fenster(text, "x\ny")).isEqualTo("a".repeat(200));
  }

  @Test
  void fenster_langerSuchbegriff_beginntAnDerFundstelle() {
    String q = "q".repeat(250);
    String text = "a".repeat(300) + q + "b".repeat(300);

    assertThat(KartenBestandSuche.fenster(text, q)).isEqualTo("…" + "q".repeat(198) + "…");
  }

  @Test
  void fenster_teiltAmLinkenRandKeinZeichenAusZweiHaelften() {
    // Das Fenster begänne auf der zweiten Hälfte des Emojis; es fällt ganz weg.
    String text = "a".repeat(201) + "😀" + "a".repeat(97) + "XY" + "b".repeat(300);

    assertThat(KartenBestandSuche.fenster(text, "xy"))
        .isEqualTo("…" + "a".repeat(97) + "XY" + "b".repeat(98) + "…");
  }

  @Test
  void fenster_teiltAmRechtenRandKeinZeichenAusZweiHaelften() {
    // Das Fenster endete nach der ersten Hälfte des Emojis; es fällt ganz weg.
    String text = "a".repeat(300) + "XY" + "b".repeat(97) + "😀" + "b".repeat(300);

    assertThat(KartenBestandSuche.fenster(text, "xy"))
        .isEqualTo("…" + "a".repeat(98) + "XY" + "b".repeat(97) + "…");
  }
}
