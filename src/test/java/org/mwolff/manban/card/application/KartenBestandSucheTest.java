package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardSearchService.KommentarFund;
import org.mwolff.manban.card.application.KartenBestandSuche.Anfrage;
import org.mwolff.manban.card.application.KartenBestandSuche.Auszug;
import org.mwolff.manban.card.application.KartenBestandSuche.Seite;
import org.mwolff.manban.card.application.KartenBestandSuche.Treffer;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Kartenart;

/**
 * Unit-Tests der Bestandssuche ohne Textfilter (Plan #1574, E5–E8); die Textsuche prüft {@link
 * KartenBestandSucheTextTest}. Reine Rechnung ohne Ports: PIT misst nur Unit-Tests, darum stehen
 * hier alle Grenzen, die der Integrationstest nur stichprobenhaft trifft.
 */
// PMD.TooManyMethods: je Filter, Grenze und Auszugsregel eine kleine Testmethode — PIT misst nur
// Unit-Tests, die Grenzen gehören darum alle hierher.
@SuppressWarnings("PMD.TooManyMethods")
class KartenBestandSucheTest {

  private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant T1 = Instant.parse("2026-02-01T00:00:00Z");
  private static final Instant T2 = Instant.parse("2026-03-01T00:00:00Z");
  private static final long BOARD = 10L;
  private static final long PROJECT = 1L;

  private static Card karte(long id, int number, String title) {
    return karte(id, number, title, CardType.CARD, null, null, false, T0, "Text " + number);
  }

  private static Card karte(
      long id,
      int number,
      String title,
      CardType type,
      @Nullable Long parentId,
      @Nullable Long derivedFrom,
      boolean archived,
      Instant createdAt,
      @Nullable String description) {
    return new Card(
        id,
        BOARD,
        100L,
        number,
        title,
        description,
        0,
        archived,
        null,
        null,
        createdAt,
        createdAt,
        type,
        parentId,
        null,
        null,
        PROJECT,
        null,
        derivedFrom,
        null,
        null);
  }

  private static Card angelegt(long id, int number, Instant createdAt) {
    return karte(id, number, "Karte " + number, CardType.CARD, null, null, false, createdAt, null);
  }

  private static Anfrage alle(int limit) {
    return new Anfrage(null, Set.of(), null, null, null, null, limit);
  }

  private static Seite suche(List<Card> karten, Anfrage anfrage) {
    return KartenBestandSuche.suche(karten, anfrage, Map.of(), Map.of());
  }

  private static List<Integer> nummern(Seite seite) {
    return seite.treffer().stream().map(t -> t.karte().number()).toList();
  }

  // --- ohne Filter ----------------------------------------------------------

  @Test
  void ohneFilter_liefertAlleKarten_nachNummerAbsteigend_archivierteEingeschlossen() {
    Card archiviert = karte(2L, 7, "Archiv", CardType.CARD, null, null, true, T0, null);
    List<Card> karten = List.of(karte(1L, 3, "A"), archiviert, karte(3L, 5, "B"));

    Seite seite = suche(karten, alle(50));

    assertThat(nummern(seite)).containsExactly(7, 5, 3);
    assertThat(seite.naechsterCursor()).isNull();
  }

  @Test
  void trefferTraegtSeineArt() {
    List<Card> karten =
        List.of(
            karte(1L, 1, "[Fachlich] F"),
            karte(2L, 2, "Paket"),
            karte(3L, 3, "Vorhaben", CardType.EPIC, null, null, false, T0, null));

    List<Kartenart> arten = suche(karten, alle(50)).treffer().stream().map(Treffer::art).toList();

    assertThat(arten)
        .containsExactly(Kartenart.VORHABEN, Kartenart.ARBEITSPAKET, Kartenart.FACHLICH);
  }

  // --- Art ------------------------------------------------------------------

  @Test
  void art_einzeln_liefertNurDieseArt() {
    List<Card> karten =
        List.of(karte(1L, 1, "[Fachlich] F"), karte(2L, 2, "[Plan] P"), karte(3L, 3, "Paket"));

    Seite seite =
        suche(karten, new Anfrage(null, Set.of(Kartenart.FACHLICH), null, null, null, null, 50));

    assertThat(nummern(seite)).containsExactly(1);
  }

  @Test
  void art_mehrere_sindOderVerknuepft() {
    List<Card> karten =
        List.of(
            karte(1L, 1, "[Fachlich] F"),
            karte(2L, 2, "[Plan] P"),
            karte(3L, 3, "Paket"),
            karte(4L, 4, "Vorhaben", CardType.EPIC, null, null, false, T0, null));

    Seite seite =
        suche(
            karten,
            new Anfrage(
                null, Set.of(Kartenart.PLAN, Kartenart.VORHABEN), null, null, null, null, 50));

    assertThat(nummern(seite)).containsExactly(4, 2);
  }

  // --- Vorhaben -------------------------------------------------------------

  @Test
  void vorhaben_liefertZugehoerige_archiviertesMitgliedEingeschlossen() {
    Card epic = karte(1L, 10, "Vorhaben", CardType.EPIC, null, null, false, T0, null);
    Card wurzel = karte(2L, 11, "Wurzel", CardType.CARD, 1L, null, false, T0, null);
    Card archiviertesKind = karte(3L, 12, "Kind", CardType.CARD, null, 2L, true, T0, null);
    Card fremd = karte(4L, 13, "Fremd");

    Seite seite =
        suche(
            List.of(epic, wurzel, archiviertesKind, fremd),
            new Anfrage(null, Set.of(), 10, null, null, null, 50));

    assertThat(nummern(seite)).containsExactly(12, 11);
  }

  @Test
  void vorhaben_nummerOhneVorhaben_liefertLeereListe() {
    Card keinVorhaben = karte(1L, 10, "Paket");
    Card kind = karte(2L, 11, "Kind", CardType.CARD, 1L, null, false, T0, null);

    Seite unbekannt =
        suche(List.of(keinVorhaben, kind), new Anfrage(null, Set.of(), 99, null, null, null, 50));
    Seite keinEpic =
        suche(List.of(keinVorhaben, kind), new Anfrage(null, Set.of(), 10, null, null, null, 50));

    assertThat(unbekannt.treffer()).isEmpty();
    assertThat(unbekannt.naechsterCursor()).isNull();
    assertThat(keinEpic.treffer()).isEmpty();
  }

  // --- Zeitraum -------------------------------------------------------------

  @Test
  void zeitraum_istHalboffen_beginnEingeschlossen_endeAusgeschlossen() {
    Instant kurzVorT1 = T1.minusNanos(1);
    List<Card> karten =
        List.of(
            angelegt(1L, 1, T0.minusNanos(1)),
            angelegt(2L, 2, T0),
            angelegt(3L, 3, kurzVorT1),
            angelegt(4L, 4, T1));

    Seite seite = suche(karten, new Anfrage(null, Set.of(), null, T0, T1, null, 50));

    assertThat(nummern(seite)).containsExactly(3, 2);
  }

  @Test
  void zeitraum_nurBeginn_offenNachOben() {
    List<Card> karten =
        List.of(angelegt(1L, 1, T0.minusNanos(1)), angelegt(2L, 2, T0), angelegt(3L, 3, T2));

    assertThat(nummern(suche(karten, new Anfrage(null, Set.of(), null, T0, null, null, 50))))
        .containsExactly(3, 2);
  }

  @Test
  void zeitraum_nurEnde_offenNachUnten() {
    List<Card> karten =
        List.of(angelegt(1L, 1, T0), angelegt(2L, 2, T1.minusNanos(1)), angelegt(3L, 3, T1));

    assertThat(nummern(suche(karten, new Anfrage(null, Set.of(), null, null, T1, null, 50))))
        .containsExactly(2, 1);
  }

  // --- kombiniert -----------------------------------------------------------

  @Test
  void filterSindUndVerknuepft() {
    Card epic = karte(1L, 10, "Vorhaben", CardType.EPIC, null, null, false, T0, null);
    Card passt = karte(2L, 11, "[Plan] P", CardType.CARD, 1L, null, true, T1, null);
    Card falscheArt = karte(3L, 12, "Paket", CardType.CARD, 1L, null, false, T1, null);
    Card falscherZeitraum = karte(4L, 13, "[Plan] Q", CardType.CARD, 1L, null, false, T2, null);
    Card fremdesVorhaben = karte(5L, 14, "[Plan] R", CardType.CARD, null, null, false, T1, null);

    Seite seite =
        suche(
            List.of(epic, passt, falscheArt, falscherZeitraum, fremdesVorhaben),
            new Anfrage(null, Set.of(Kartenart.PLAN), 10, T1, T2, null, 50));

    assertThat(nummern(seite)).containsExactly(11);
  }

  // --- Teile (Keyset) -------------------------------------------------------

  @Test
  void teile_ueberMehrereAbrufe_ohneLueckeUndOhneDoppel() {
    List<Card> karten = new ArrayList<>();
    for (int i = 1; i <= 23; i++) {
      karten.add(karte(i, i * 2, "Karte " + i));
    }
    List<Integer> gesammelt = new ArrayList<>();
    Integer cursor = null;
    int abrufe = 0;
    do {
      Seite seite = suche(karten, new Anfrage(null, Set.of(), null, null, null, cursor, 5));
      assertThat(seite.treffer()).hasSizeLessThanOrEqualTo(5);
      gesammelt.addAll(nummern(seite));
      cursor = seite.naechsterCursor();
      abrufe++;
    } while (cursor != null);

    assertThat(abrufe).isEqualTo(5);
    assertThat(gesammelt).hasSize(23).doesNotHaveDuplicates();
    assertThat(new HashSet<>(gesammelt))
        .isEqualTo(new HashSet<>(karten.stream().map(Card::number).toList()));
    assertThat(gesammelt).isSortedAccordingTo((a, b) -> Integer.compare(b, a));
  }

  @Test
  void cursor_istDieNummerDesLetztenTreffers_undSchliesstSieAus() {
    List<Card> karten = List.of(karte(1L, 1, "A"), karte(2L, 2, "B"), karte(3L, 3, "C"));

    Seite erste = suche(karten, alle(2));
    Seite zweite = suche(karten, new Anfrage(null, Set.of(), null, null, null, 2, 2));

    assertThat(nummern(erste)).containsExactly(3, 2);
    assertThat(erste.naechsterCursor()).isEqualTo(2);
    assertThat(nummern(zweite)).containsExactly(1);
    assertThat(zweite.naechsterCursor()).isNull();
  }

  @Test
  void cursor_unterhalbAllerNummern_liefertLeereSeite() {
    Seite seite =
        suche(List.of(karte(1L, 5, "A")), new Anfrage(null, Set.of(), null, null, null, 5, 2));

    assertThat(seite.treffer()).isEmpty();
    assertThat(seite.naechsterCursor()).isNull();
  }

  @Test
  void limit_genauSoGrossWieDerRest_liefertKeinenNaechstenTeil() {
    List<Card> karten = List.of(karte(1L, 1, "A"), karte(2L, 2, "B"));

    Seite seite = suche(karten, alle(2));

    assertThat(nummern(seite)).containsExactly(2, 1);
    assertThat(seite.naechsterCursor()).isNull();
  }

  @Test
  void limit_eins_liefertGenauEinenTreffer() {
    List<Card> karten = List.of(karte(1L, 1, "A"), karte(2L, 2, "B"));

    Seite seite = suche(karten, alle(1));

    assertThat(nummern(seite)).containsExactly(2);
    assertThat(seite.naechsterCursor()).isEqualTo(2);
  }

  @Test
  void limit_unterEins_istEinFehlerDesAufrufers() {
    assertThatThrownBy(() -> suche(List.of(), alle(0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void leererBestand_liefertLeereSeite() {
    Seite seite = suche(List.of(), alle(1));

    assertThat(seite.treffer()).isEmpty();
    assertThat(seite.naechsterCursor()).isNull();
  }

  // --- Auszug (E5) ----------------------------------------------------------

  private static Auszug auszugVon(@Nullable String text) {
    Card k = karte(1L, 1, "A", CardType.CARD, null, null, false, T0, text);
    return suche(List.of(k), alle(1)).treffer().getFirst().auszug();
  }

  @Test
  void auszug_ohneTextfilter_istDerAnfangDesTexts_ausDemText() {
    Auszug auszug = auszugVon("Kurzer Text");

    assertThat(auszug).isEqualTo(new Auszug("Kurzer Text", "BODY", null));
  }

  @Test
  void auszug_kapptNach200Zeichen() {
    String genau = "a".repeat(200);

    assertThat(auszugVon(genau).text()).isEqualTo(genau);
    assertThat(auszugVon(genau + "b").text()).isEqualTo(genau);
  }

  @Test
  void auszug_machtZeilenumbruecheZuLeerzeichen() {
    assertThat(auszugVon("## Kontext\nZeile\r\nnoch\rmehr").text())
        .isEqualTo("## Kontext Zeile noch mehr");
  }

  @Test
  void auszug_zaehltNachDemErsetzenDerZeilenumbrueche() {
    // \r\n wird zu einem Leerzeichen, der Text danach passt so genau in 200 Zeichen.
    String text = "a".repeat(100) + "\r\n" + "b".repeat(99);

    assertThat(auszugVon(text).text()).isEqualTo("a".repeat(100) + " " + "b".repeat(99));
  }

  @Test
  void auszug_teiltKeinZeichenAusZweiHaelften() {
    // Ein Emoji belegt zwei char; läge seine erste Hälfte auf Position 200, fiele es ganz weg.
    String text = "a".repeat(199) + "😀" + "b";

    assertThat(auszugVon(text).text()).isEqualTo("a".repeat(199));
  }

  @Test
  void auszug_ohneText_istLeer() {
    assertThat(auszugVon(null)).isEqualTo(new Auszug("", "BODY", null));
  }

  @Test
  void auszug_mitKommentarFundstelle_kommtAusDemKommentar() {
    Card k = karte(7L, 1, "A");

    Auszug auszug =
        KartenBestandSuche.suche(
                List.of(k),
                alle(1),
                Map.of(),
                Map.of(7L, new KommentarFund(55L, "Zeile\nim Kommentar")))
            .treffer()
            .getFirst()
            .auszug();

    assertThat(auszug).isEqualTo(new Auszug("Zeile im Kommentar", "COMMENT", 55L));
  }
}
