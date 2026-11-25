package org.mwolff.manban.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.comment.domain.Antwort.Einzelantwort;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.EingegangeneAntwort;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.FrageStand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.FrageZustand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.RueckfrageZustand;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.Stand;

/**
 * Eine Antwort gilt der Fassung der Rückfrage, die zu ihrer Zeit stand (Issue #2262): Ändert der
 * Autor die Rückfrage danach, braucht die neue Fassung eine neue Zustimmung.
 */
class RueckfrageFassungTest {

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

  private static EingegangeneAntwort vomKunden(Antwort antwort, Instant am) {
    return new EingegangeneAntwort(antwort, am, false, null);
  }

  private static Antwort alles() {
    return new Antwort(9L, true, List.of(), null, null);
  }

  private static Antwort nur(Einzelantwort... antworten) {
    return new Antwort(9L, false, List.of(antworten), null, null);
  }

  private static Stand auswerten(Rueckfrage rueckfrage, EingegangeneAntwort... antworten) {
    return RueckfragenAuswertung.auswerten(rueckfrage, List.of(antworten), Optional.empty());
  }

  /**
   * Angriff aus dem Sicherheitsbefund (Issue #2262): Die KI legt über einen gewöhnlichen Zugang
   * eine Rückfrage an, der Mensch übernimmt alle Empfehlungen, danach tauscht die KI die
   * Empfehlungen in ihrem Kommentar aus. Die frühere Zustimmung gilt für die neue Fassung nicht.
   */
  @Test
  void zustimmungGiltNichtFuerEineSpaeterGeaenderteRueckfrage() {
    // Given: dieselbe Kommentar-ID, nach der Antwort mit ausgetauschten Empfehlungen
    String ausgetauscht =
        BLOCK
            .replace(
                "\"text\": \"A\"}, {\"text\": \"B\", \"empfohlen\": true}",
                "\"text\": \"A\", \"empfohlen\": true}, {\"text\": \"B\"}")
            .replace("\"E2\"", "\"Alles löschen\"");
    Rueckfrage vorher = rueckfrage();
    Rueckfrage nachher =
        Rueckfrage.lies(9L, FRAGE_AM, NOCH_SPAETER, "## Rückfrage\n" + ausgetauscht).orElseThrow();
    EingegangeneAntwort zustimmung = vomKunden(alles(), SPAETER);

    // When
    Stand vorDemAustausch = auswerten(vorher, zustimmung);
    Stand nachDemAustausch = auswerten(nachher, zustimmung);

    // Then: vor dem Austausch beantwortet, danach wieder offen und ohne übernommene Empfehlung
    assertThat(ausgetauscht).isNotEqualTo(BLOCK);
    assertThat(vorDemAustausch.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
    assertThat(nachDemAustausch.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
    assertThat(nachDemAustausch.fragen())
        .containsExactly(FrageStand.offen(1), FrageStand.offen(2), FrageStand.offen(3));
    assertThat(nachDemAustausch.fragen()).noneMatch(FrageStand::empfehlung);
  }

  @Test
  void eineAntwortNachDerAenderungGiltFuerDieNeueFassung() {
    // Given: geändert um SPAETER, beantwortet erst danach
    Rueckfrage geaendert =
        Rueckfrage.lies(9L, FRAGE_AM, SPAETER, "## Rückfrage\n" + BLOCK).orElseThrow();

    // When
    Stand stand =
        auswerten(
            geaendert,
            vomKunden(nur(Einzelantwort.gewaehlt(1, 1)), FRAGE_AM.plusSeconds(1)),
            vomKunden(alles(), NOCH_SPAETER));

    // Then: nur die Antwort nach der Änderung zählt — Frage 1 trägt die Empfehlung, nicht Wahl 1
    assertThat(geaendert.geaendertAm()).isEqualTo(SPAETER);
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.BEANTWORTET);
    assertThat(stand.fragen().getFirst())
        .isEqualTo(new FrageStand(1, FrageZustand.BEANTWORTET, 2, null, true));
  }

  @Test
  void eineAntwortImAugenblickDerAenderungZaehltNicht() {
    // Given
    Rueckfrage geaendert =
        Rueckfrage.lies(9L, FRAGE_AM, SPAETER, "## Rückfrage\n" + BLOCK).orElseThrow();

    // When
    Stand stand = auswerten(geaendert, vomKunden(alles(), SPAETER));

    // Then: Die Reihenfolge ist nicht feststellbar, im Zweifel gilt die Zustimmung nicht
    assertThat(stand.zustand()).isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void ohneAenderungIstDieFassungDieDerAnlage() {
    // Then
    assertThat(rueckfrage().geaendertAm()).isEqualTo(FRAGE_AM);
  }
}
