package org.mwolff.manban.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.comment.domain.Antwort.Einzelantwort;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.EingegangeneAntwort;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.FrageStand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.FrageZustand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.RueckfrageZustand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.Stand;

/**
 * Stand je Frage und je Rückfrage (Issue #2210, Plan #2206 E8, E11): Gültigkeit der Antworten,
 * Ergänzen, Zurückstellen und die überholte Freigabe.
 */
class RueckfragenAuswertungTest {

  private static final Instant FRAGE_AM = Instant.parse("2026-11-22T10:00:00Z");
  private static final Instant SPAETER = Instant.parse("2026-11-22T11:00:00Z");
  private static final Instant NOCH_SPAETER = Instant.parse("2026-11-22T12:00:00Z");

  private static final String BLOCK =
      """
      ```json kit-vorlage
      [
        {"nr": 1, "frage": "F1", "empfehlung": "E1",
         "optionen": [{"text": "A"}, {"text": "B", "empfohlen": true}]},
        {"nr": 2, "frage": "F2", "empfehlung": "E2"},
        {"nr": 3, "frage": "F3", "empfehlung": "E3", "optionen": [{"text": "X"}]}
      ]
      ```
      """;

  private static Rueckfrage rueckfrage() {
    return Rueckfrage.lies(9L, FRAGE_AM, "## Rückfrage\n" + BLOCK).orElseThrow();
  }

  private static Rueckfrage freigabe() {
    return Rueckfrage.lies(9L, FRAGE_AM, "## Rückfrage\nVorschau: https://p\nCommit: abc\n" + BLOCK)
        .orElseThrow();
  }

  private static EingegangeneAntwort vomKunden(Antwort antwort, Instant am) {
    return new EingegangeneAntwort(antwort, am, false, null);
  }

  private static Antwort alles() {
    return new Antwort(9L, true, List.of(), null, null);
  }

  private static Antwort nur(Einzelantwort... antworten) {
    return new Antwort(9L, false, List.of(antworten), null, null);
  }

  private static Stand auswerten(
      Rueckfrage rueckfrage, @Nullable Vorschau vorschau, EingegangeneAntwort... antworten) {
    return RueckfragenAuswertung.auswerten(
        rueckfrage, List.of(antworten), Optional.ofNullable(vorschau));
  }

  @Test
  void ohneAntwortIstAllesOffen() {
    // When
    Stand stand = auswerten(rueckfrage(), null);

    // Then
    assertThat(stand.rueckfrage()).isEqualTo(rueckfrage());
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
    assertThat(stand.fragen()).extracting(FrageStand::zustand).containsOnly(FrageZustand.OFFEN);
  }

  @Test
  void allesUebernehmenBeantwortetJedeFrageMitDerEmpfehlung() {
    // When
    Stand stand = auswerten(rueckfrage(), null, vomKunden(alles(), SPAETER));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
    assertThat(stand.fragen())
        .containsExactly(
            new FrageStand(1, FrageZustand.BEANTWORTET, 2, null, true),
            new FrageStand(2, FrageZustand.BEANTWORTET, null, null, true),
            new FrageStand(3, FrageZustand.BEANTWORTET, null, null, true));
  }

  @Test
  void jaMitAbweichungen() {
    // Given
    Antwort antwort =
        new Antwort(
            9L,
            true,
            List.of(Einzelantwort.gewaehlt(1, 1), Einzelantwort.eigene(2, "anders")),
            null,
            null);

    // When
    Stand stand = auswerten(rueckfrage(), null, vomKunden(antwort, SPAETER));

    // Then
    assertThat(stand.fragen())
        .containsExactly(
            new FrageStand(1, FrageZustand.BEANTWORTET, 1, null, false),
            new FrageStand(2, FrageZustand.BEANTWORTET, null, "anders", false),
            new FrageStand(3, FrageZustand.BEANTWORTET, null, null, true));
  }

  @Test
  void zurueckgestellteFrageHaeltOffen() {
    // Given
    Antwort antwort = new Antwort(9L, true, List.of(Einzelantwort.zurueckgestellt(2)), null, null);

    // When
    Stand stand = auswerten(rueckfrage(), null, vomKunden(antwort, SPAETER));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
    assertThat(stand.fragen().get(1))
        .isEqualTo(new FrageStand(2, FrageZustand.ZURUECKGESTELLT, null, null, false));
  }

  @Test
  void ergaenzendeAntwortSchliesst() {
    // Given
    Antwort erste = new Antwort(9L, true, List.of(Einzelantwort.zurueckgestellt(2)), null, null);
    Antwort zweite = nur(Einzelantwort.eigene(2, "jetzt"));

    // When — in falscher Reihenfolge übergeben, die Zeit zählt
    Stand stand =
        auswerten(rueckfrage(), null, vomKunden(zweite, NOCH_SPAETER), vomKunden(erste, SPAETER));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
    assertThat(stand.fragen().get(0).wahl()).isEqualTo(2);
    assertThat(stand.fragen().get(1))
        .isEqualTo(new FrageStand(2, FrageZustand.BEANTWORTET, null, "jetzt", false));
  }

  @Test
  void spaeteresJaUeberschreibtKeineBeantworteteFrage() {
    // Given
    Antwort erste = nur(Einzelantwort.gewaehlt(1, 1));

    // When
    Stand stand =
        auswerten(rueckfrage(), null, vomKunden(erste, SPAETER), vomKunden(alles(), NOCH_SPAETER));

    // Then
    assertThat(stand.fragen().get(0))
        .isEqualTo(new FrageStand(1, FrageZustand.BEANTWORTET, 1, null, false));
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
  }

  @Test
  void teilantwortLaesstRestOffen() {
    // When
    Stand stand =
        auswerten(rueckfrage(), null, vomKunden(nur(Einzelantwort.gewaehlt(3, 1)), SPAETER));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
    assertThat(stand.fragen())
        .extracting(FrageStand::zustand)
        .containsExactly(FrageZustand.OFFEN, FrageZustand.OFFEN, FrageZustand.BEANTWORTET);
  }

  @Test
  void unpassendeEinzelantwortenZaehlenNicht() {
    // Given — Wahl außerhalb der Möglichkeiten, Wahl ohne Möglichkeiten, unbekannte Frage
    Antwort antwort =
        nur(
            Einzelantwort.gewaehlt(1, 3),
            Einzelantwort.gewaehlt(1, 0),
            Einzelantwort.gewaehlt(2, 1),
            Einzelantwort.gewaehlt(7, 1));

    // When
    Stand stand = auswerten(rueckfrage(), null, vomKunden(antwort, SPAETER));

    // Then
    assertThat(stand.fragen()).extracting(FrageStand::zustand).containsOnly(FrageZustand.OFFEN);
  }

  @Test
  void antwortMitVonKiZaehltNicht() {
    // When
    Stand stand =
        auswerten(rueckfrage(), null, new EingegangeneAntwort(alles(), SPAETER, true, "APP"));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void antwortUeberStandardZugangZaehltNicht() {
    // When
    Stand stand =
        auswerten(rueckfrage(), null, new EingegangeneAntwort(alles(), SPAETER, false, "STANDARD"));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void antwortDerAppZaehlt() {
    // When
    Stand stand =
        auswerten(rueckfrage(), null, new EingegangeneAntwort(alles(), SPAETER, false, "APP"));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
  }

  @Test
  void antwortAufAndereRueckfrageZaehltNicht() {
    // When
    Stand stand =
        auswerten(
            rueckfrage(), null, vomKunden(new Antwort(10L, true, List.of(), null, null), SPAETER));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void fremderCommitIstUngueltig() {
    // When
    Stand stand =
        auswerten(
            freigabe(), null, vomKunden(new Antwort(9L, true, List.of(), null, "fremd"), SPAETER));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void commitOhneFreigabeIstUngueltig() {
    // When
    Stand stand =
        auswerten(
            rueckfrage(), null, vomKunden(new Antwort(9L, true, List.of(), null, "abc"), SPAETER));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void freigabeMitPassendemCommitIstBeantwortet() {
    // When
    Stand stand =
        auswerten(
            freigabe(),
            new Vorschau(SPAETER, "abc"),
            vomKunden(new Antwort(9L, true, List.of(), null, "abc"), NOCH_SPAETER));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
  }

  @Test
  void aeltereVorschauMitAndererRevisionUeberholtNicht() {
    // When
    Stand stand = auswerten(freigabe(), new Vorschau(FRAGE_AM, "alt"));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void juengereVorschauMitAndererRevisionUeberholt() {
    // Given
    Vorschau neuer = new Vorschau(SPAETER, "neu");

    // When
    Stand offen = auswerten(freigabe(), neuer);
    Stand freigegeben =
        auswerten(
            freigabe(), neuer, vomKunden(new Antwort(9L, true, List.of(), null, "abc"), SPAETER));

    // Then — zählt nicht als offen, und die alte Freigabe trägt nicht mehr
    assertThat(offen.zustand()).isEqualTo(RueckfrageZustand.UEBERHOLT);
    assertThat(freigegeben.zustand()).isEqualTo(RueckfrageZustand.UEBERHOLT);
    assertThat(freigegeben.fragen())
        .extracting(FrageStand::zustand)
        .containsOnly(FrageZustand.OFFEN);
    assertThat(RueckfragenAuswertung.istUeberholt(freigabe(), Optional.of(neuer))).isTrue();
  }

  @Test
  void ohneFreigabeUeberholtKeineVorschau() {
    // When
    Stand stand = auswerten(rueckfrage(), new Vorschau(SPAETER, "neu"));

    // Then
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void juengereVorschauMitGleicherRevisionUeberholtNicht() {
    // Then
    assertThat(
            RueckfragenAuswertung.istUeberholt(
                freigabe(), Optional.of(new Vorschau(SPAETER, "abc"))))
        .isFalse();
    assertThat(RueckfragenAuswertung.istUeberholt(freigabe(), Optional.empty())).isFalse();
  }

  @Test
  void wegWirdUeberDasFeldWegBeantwortet() {
    // Given
    Rueckfrage weg =
        Rueckfrage.lies(
                9L,
                FRAGE_AM,
                """
                ## Rückfrage
                ```json kit-vorlage
                [{"nr": 1, "art": "weg", "frage": "Welcher Weg?", "empfehlung": "B",
                  "optionen": [{"text": "A"}, {"text": "B", "empfohlen": true}]}]
                ```
                """)
            .orElseThrow();

    // When
    Stand gewaehlt =
        auswerten(weg, null, vomKunden(new Antwort(9L, false, List.of(), 1, null), SPAETER));
    Stand ausserhalb =
        auswerten(weg, null, vomKunden(new Antwort(9L, false, List.of(), 3, null), SPAETER));
    Stand empfohlen = auswerten(weg, null, vomKunden(alles(), SPAETER));
    Stand ohneWeg =
        auswerten(
            rueckfrage(), null, vomKunden(new Antwort(9L, false, List.of(), 1, null), SPAETER));

    // Then
    assertThat(gewaehlt.fragen())
        .containsExactly(new FrageStand(1, FrageZustand.BEANTWORTET, 1, null, false));
    assertThat(gewaehlt.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
    assertThat(ausserhalb.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
    assertThat(empfohlen.fragen())
        .containsExactly(new FrageStand(1, FrageZustand.BEANTWORTET, 2, null, true));
    assertThat(ohneWeg.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void feldWegBeantwortetKeineGewoehnlicheFrage() {
    // Given: Fragen 1 und 3 bieten eine Möglichkeit 1 an, sind aber keine Wege
    Antwort wegEins = new Antwort(9L, false, List.of(), 1, null);

    // When
    Stand stand = auswerten(rueckfrage(), null, vomKunden(wegEins, SPAETER));

    // Then
    assertThat(stand.fragen())
        .containsExactly(FrageStand.offen(1), FrageStand.offen(2), FrageStand.offen(3));
  }

  @Test
  void englischeKoepfeWerdenGelesenUndGeschrieben() {
    // Given
    Rueckfrage question = Rueckfrage.lies(9L, FRAGE_AM, "## Question\n" + BLOCK).orElseThrow();
    String answer = alles().alsKommentar(question.sprache());

    // When
    Stand stand = auswerten(question, null, vomKunden(Antwort.lies(answer).orElseThrow(), SPAETER));

    // Then
    assertThat(answer).startsWith("## Answer\n");
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
  }
}
