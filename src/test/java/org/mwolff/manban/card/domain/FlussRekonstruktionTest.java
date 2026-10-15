package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Archivereignis;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Kartenstand;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spalte;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenfolge;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenort;
import org.mwolff.manban.card.domain.FlussRekonstruktion.Spaltenzeile;
import org.mwolff.manban.card.domain.FlussStay.EndedBy;

/**
 * Rekonstruktion der Zustandsaufenthalte aus dem Spaltenverlauf (Issue #1769, Plan #1765, E3–E8).
 */
// PMD.TooManyMethods: je Grenzfall der Rekonstruktion eine kleine Testmethode — die Fälle sind
// der Inhalt des Pakets (Issue #1769) und bleiben so einzeln lesbar.
@SuppressWarnings("PMD.TooManyMethods")
class FlussRekonstruktionTest {

  private static final Instant BASIS = Instant.parse("2026-01-05T08:00:00Z");
  private static final long KARTEN_ID = 42L;
  private static final long B1 = 1L;
  private static final long B2 = 2L;

  private static final Spalte BACKLOG = new Spalte(10, "Backlog");
  private static final Spalte ANSTEHEND = new Spalte(11, "Anstehend");
  private static final Spalte PARKEN = new Spalte(12, "Parken");
  private static final Spalte READY = new Spalte(13, "Ready");
  private static final Spalte IN_PROGRESS = new Spalte(14, "In Progress");
  private static final Spalte WARTEN = new Spalte(15, "Warten");
  private static final Spalte IN_REVIEW = new Spalte(16, "In Review");
  private static final Spalte DONE = new Spalte(17, "Done");
  private static final Spalte READY_B2 = new Spalte(22, "Ready");

  private static final List<Spaltenfolge> FOLGEN =
      List.of(
          new Spaltenfolge(B2, List.of(new Spalte(20, "Backlog"), READY_B2)),
          new Spaltenfolge(
              B1,
              List.of(BACKLOG, ANSTEHEND, PARKEN, READY, IN_PROGRESS, WARTEN, IN_REVIEW, DONE)));

  private static Instant um(int stunden) {
    return BASIS.plusSeconds(3600L * stunden);
  }

  private static Kartenstand karte(CardStatus status) {
    return new Kartenstand(KARTEN_ID, status, um(0), null, null);
  }

  private static Spaltenzeile zeile(Spalte spalte, int von, @Nullable Integer bis) {
    return zeile(B1, spalte, spalte.name(), von, bis);
  }

  private static Spaltenzeile zeile(
      long board, Spalte spalte, String name, int von, @Nullable Integer bis) {
    return new Spaltenzeile(
        new Spaltenort(spalte.id(), board), name, um(von), bis == null ? null : um(bis));
  }

  private static Spaltenzeile geloescht(int von, @Nullable Integer bis) {
    return new Spaltenzeile(null, "Alt", um(von), bis == null ? null : um(bis));
  }

  private static FlussStay stay(
      long board,
      @Nullable CardStatus status,
      int von,
      @Nullable Integer bis,
      boolean bekannt,
      @Nullable EndedBy endedBy) {
    return new FlussStay(
        null, KARTEN_ID, board, status, um(von), bis == null ? null : um(bis), bekannt, endedBy);
  }

  private static List<FlussStay> rekonstruiere(
      Kartenstand karte, List<Spaltenzeile> zeilen, List<Archivereignis> ereignisse) {
    return FlussRekonstruktion.rekonstruiere(karte, zeilen, ereignisse, FOLGEN);
  }

  @Test
  void eineZusatzspalteZaehltNachDerLageInDerHeutigenSpaltenfolge() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.IN_PROGRESS),
            List.of(zeile(READY, 0, 1), zeile(PARKEN, 1, 2), zeile(WARTEN, 2, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.READY, 0, 1, true, EndedBy.STATUS),
            stay(B1, CardStatus.BACKLOG, 1, 2, true, EndedBy.STATUS),
            stay(B1, CardStatus.IN_PROGRESS, 2, null, true, null));
  }

  @Test
  void eineZeileEinesReinenStatuswechselsZaehltNachDemProzessnamen() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY),
            List.of(zeile(ANSTEHEND, 0, 3), zeile(B1, ANSTEHEND, "Ready", 3, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.BACKLOG, 0, 3, true, EndedBy.STATUS),
            stay(B1, CardStatus.READY, 3, null, true, null));
  }

  @Test
  void zeilenMitGleichemZustandUndBoardVerschmelzenZuEinemAufenthalt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY),
            List.of(
                zeile(BACKLOG, 0, 1),
                zeile(ANSTEHEND, 1, 2),
                zeile(PARKEN, 2, 3),
                zeile(READY, 3, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.BACKLOG, 0, 3, true, EndedBy.STATUS),
            stay(B1, CardStatus.READY, 3, null, true, null));
  }

  @Test
  void einBoardwechselEndetMitBoard() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY),
            List.of(zeile(READY, 0, 4), zeile(B2, READY_B2, "Ready", 4, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.READY, 0, 4, true, EndedBy.BOARD),
            stay(B2, CardStatus.READY, 4, null, true, null));
  }

  @Test
  void eineGeloeschteSpalteIstUnbekanntUndZaehltZumBoardDesVorigenAufenthalts() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.IN_PROGRESS),
            List.of(
                zeile(READY, 0, 1),
                geloescht(1, 2),
                zeile(B2, READY_B2, "Ready", 2, 3),
                zeile(B2, READY_B2, "In Progress", 3, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.READY, 0, 1, true, EndedBy.STATUS),
            stay(B1, null, 1, 2, false, EndedBy.BOARD),
            stay(B2, CardStatus.READY, 2, 3, false, EndedBy.STATUS),
            stay(B2, CardStatus.IN_PROGRESS, 3, null, true, null));
  }

  @Test
  void eineGeloeschteSpalteAmAnfangZaehltZumBoardDesFolgendenAufenthalts() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY),
            List.of(geloescht(0, 2), zeile(B2, READY_B2, "Ready", 2, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B2, null, 0, 2, false, EndedBy.STATUS),
            stay(B2, CardStatus.READY, 2, null, false, null));
  }

  @Test
  void ohneBekanntesBoardEntstehtKeinAufenthalt() {
    assertThat(
            rekonstruiere(
                new Kartenstand(KARTEN_ID, CardStatus.DONE, um(0), um(1), null),
                List.of(geloescht(0, 1), geloescht(1, null)),
                List.of()))
        .isEmpty();
  }

  @Test
  void eineSpalteAusserhalbDerSpaltenfolgeIstUnbekannt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY),
            List.of(zeile(B1, new Spalte(99, "Fremd"), "Fremd", 0, 1), zeile(READY, 1, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, null, 0, 1, false, EndedBy.STATUS),
            stay(B1, CardStatus.READY, 1, null, false, null));
  }

  @Test
  void dieRueckholungAusDemPapierkorbMachtDieZeitSeitDemLetztenBekanntenPunktUnbekannt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.IN_PROGRESS),
            List.of(zeile(READY, 0, 2), zeile(IN_PROGRESS, 2, null)),
            List.of(
                new Archivereignis(
                    CardActivityType.RESTORED, um(5), "Aus Papierkorb wiederhergestellt")));

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.READY, 0, 2, true, EndedBy.STATUS),
            stay(B1, null, 2, 5, false, EndedBy.STATUS),
            stay(B1, CardStatus.IN_PROGRESS, 5, null, false, null));
  }

  @Test
  void derLetzteBekanntePunktVorDerRueckholungKannAuchEinArchivereignisSein() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.DONE),
            List.of(zeile(DONE, 0, null)),
            List.of(
                new Archivereignis(CardActivityType.ARCHIVED, um(3), "Archiviert"),
                new Archivereignis(
                    CardActivityType.RESTORED, um(6), "Aus Papierkorb wiederhergestellt")));

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.DONE, 0, 3, true, EndedBy.STATUS),
            stay(B1, null, 3, 6, false, EndedBy.STATUS),
            stay(B1, CardStatus.DONE, 6, null, false, null));
  }

  @Test
  void dieRueckholungAusDemArchivOeffnetEinenNeuenAufenthalt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.IN_PROGRESS),
            List.of(zeile(IN_PROGRESS, 0, null)),
            List.of(
                new Archivereignis(CardActivityType.RESTORED, um(4), "Wiederhergestellt"),
                new Archivereignis(CardActivityType.ARCHIVED, um(2), "Archiviert")));

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.IN_PROGRESS, 0, 2, true, EndedBy.EXIT),
            stay(B1, CardStatus.IN_PROGRESS, 4, null, true, null));
  }

  @Test
  void eineRueckholungAusDemArchivOhneArchivierungDavorAendertNichts() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY),
            List.of(zeile(READY, 0, null)),
            List.of(new Archivereignis(CardActivityType.RESTORED, um(4), "Wiederhergestellt")));

    assertThat(aufenthalte).containsExactly(stay(B1, CardStatus.READY, 0, null, true, null));
  }

  @Test
  void eineArchivierteNichtFertigeKarteVerlaesstDenFluss() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.IN_REVIEW),
            List.of(zeile(IN_REVIEW, 0, null)),
            List.of(new Archivereignis(CardActivityType.ARCHIVED, um(3), "Archiviert")));

    assertThat(aufenthalte)
        .containsExactly(stay(B1, CardStatus.IN_REVIEW, 0, 3, true, EndedBy.EXIT));
  }

  @Test
  void dasArchivEinerFertigenKarteBleibtImFluss() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.DONE),
            List.of(zeile(IN_REVIEW, 0, 1), zeile(DONE, 1, null)),
            List.of(new Archivereignis(CardActivityType.ARCHIVED, um(3), "Archiviert")));

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.IN_REVIEW, 0, 1, true, EndedBy.STATUS),
            stay(B1, CardStatus.DONE, 1, null, true, null));
  }

  @Test
  void derPapierkorbEndetDenAufenthaltAuchEinerFertigenKarte() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.DONE, um(0), null, um(5)),
            List.of(zeile(DONE, 0, null)),
            List.of());

    assertThat(aufenthalte).containsExactly(stay(B1, CardStatus.DONE, 0, 5, true, EndedBy.EXIT));
  }

  @Test
  void eineKarteAusDerZeitVorDemSpaltenverlaufBeginntMitEinerUnbekanntenSpanne() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.READY, um(0), null, null),
            List.of(zeile(READY, 3, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, null, 0, 3, false, EndedBy.STATUS),
            stay(B1, CardStatus.READY, 3, null, false, null));
  }

  @Test
  void eineErsteZeileZumAnlagezeitpunktHatEinenBekanntenEintritt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.READY, um(3), null, null),
            List.of(zeile(READY, 3, null)),
            List.of());

    assertThat(aufenthalte).containsExactly(stay(B1, CardStatus.READY, 3, null, true, null));
  }

  @Test
  void beiEinerFertigenKarteIstDerDoneStempelDerBekannteDoneEintritt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.DONE, um(0), um(2), null),
            List.of(zeile(DONE, 5, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, null, 0, 2, false, EndedBy.STATUS),
            stay(B1, CardStatus.DONE, 2, null, true, null));
  }

  @Test
  void derDoneStempelKuerztDieAufenthalteDavorUndVerdraengtSpaetere() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.DONE, um(0), um(2), null),
            List.of(zeile(IN_PROGRESS, 1, 2), zeile(IN_REVIEW, 2, 3), zeile(DONE, 3, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, null, 0, 1, false, EndedBy.STATUS),
            stay(B1, CardStatus.IN_PROGRESS, 1, 2, false, EndedBy.STATUS),
            stay(B1, CardStatus.DONE, 2, null, true, null));
  }

  @Test
  void einDoneStempelAmBeginnDesDoneAufenthaltsMachtDenEintrittBekannt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.DONE, um(0), um(4), null),
            List.of(zeile(DONE, 4, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, null, 0, 4, false, EndedBy.STATUS),
            stay(B1, CardStatus.DONE, 4, null, true, null));
  }

  @Test
  void einDoneStempelNachDemBeginnDesDoneAufenthaltsAendertNichts() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.DONE, um(0), um(6), null),
            List.of(zeile(DONE, 4, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, null, 0, 4, false, EndedBy.STATUS),
            stay(B1, CardStatus.DONE, 4, null, false, null));
  }

  @Test
  void einDoneStempelGiltNurFuerEinenLetztenDoneAufenthalt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.IN_REVIEW, um(0), um(1), null),
            List.of(zeile(DONE, 0, 2), zeile(IN_REVIEW, 2, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.DONE, 0, 2, true, EndedBy.STATUS),
            stay(B1, CardStatus.IN_REVIEW, 2, null, true, null));
  }

  @Test
  void eineDokumentkarteOhneZustandErgibtKeineAufenthalte() {
    assertThat(
            rekonstruiere(
                new Kartenstand(KARTEN_ID, null, um(0), null, null),
                List.of(zeile(IN_REVIEW, 0, null)),
                List.of()))
        .isEmpty();
  }

  @Test
  void ohneSpaltenverlaufEntstehtKeinAufenthalt() {
    assertThat(rekonstruiere(karte(CardStatus.READY), List.of(), List.of())).isEmpty();
  }

  @Test
  void dieZeilenWerdenNachEintrittGeordnet() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY),
            List.of(zeile(READY, 2, null), zeile(BACKLOG, 0, 2)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.BACKLOG, 0, 2, true, EndedBy.STATUS),
            stay(B1, CardStatus.READY, 2, null, true, null));
  }

  @Test
  void eineLueckeImVerlaufEndetMitExit() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY),
            List.of(zeile(BACKLOG, 0, 1), zeile(READY, 2, null)),
            List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, CardStatus.BACKLOG, 0, 1, true, EndedBy.EXIT),
            stay(B1, CardStatus.READY, 2, null, true, null));
  }

  @Test
  void dieDetailTexteSindDieDerRueckholungen() {
    assertThat(FlussRekonstruktion.AUS_DEM_ARCHIV).isEqualTo("Wiederhergestellt");
    assertThat(FlussRekonstruktion.AUS_DEM_PAPIERKORB)
        .isEqualTo("Aus Papierkorb wiederhergestellt");
  }

  @Test
  void dieLageZaehltNurInDerSpaltenfolgeDesEigenenBoards() {
    List<Spaltenfolge> folgen =
        List.of(
            new Spaltenfolge(B2, List.of(new Spalte(20, "Backlog"), READY_B2, PARKEN)),
            FOLGEN.get(1));

    assertThat(
            FlussRekonstruktion.rekonstruiere(
                karte(CardStatus.BACKLOG), List.of(zeile(PARKEN, 0, null)), List.of(), folgen))
        .containsExactly(stay(B1, CardStatus.BACKLOG, 0, null, true, null));
  }

  @Test
  void dieRueckholungAusDemPapierkorbBeendetDasArchivNicht() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.IN_PROGRESS),
            List.of(zeile(IN_PROGRESS, 0, null)),
            List.of(
                new Archivereignis(CardActivityType.ARCHIVED, um(2), "Archiviert"),
                new Archivereignis(
                    CardActivityType.RESTORED, um(4), FlussRekonstruktion.AUS_DEM_PAPIERKORB)));

    assertThat(aufenthalte)
        .containsExactly(stay(B1, CardStatus.IN_PROGRESS, 0, 2, true, EndedBy.EXIT));
  }

  @Test
  void dieRueckholungAusDemArchivMachtKeineZeitUnbekannt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.DONE),
            List.of(zeile(DONE, 0, null)),
            List.of(
                new Archivereignis(CardActivityType.ARCHIVED, um(3), "Archiviert"),
                new Archivereignis(
                    CardActivityType.RESTORED, um(6), FlussRekonstruktion.AUS_DEM_ARCHIV)));

    assertThat(aufenthalte).containsExactly(stay(B1, CardStatus.DONE, 0, null, true, null));
  }

  @Test
  void nurEineRueckholungMachtDieZeitDavorUnbekannt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.DONE),
            List.of(zeile(DONE, 0, null)),
            List.of(
                new Archivereignis(
                    CardActivityType.ARCHIVED, um(3), FlussRekonstruktion.AUS_DEM_PAPIERKORB)));

    assertThat(aufenthalte).containsExactly(stay(B1, CardStatus.DONE, 0, null, true, null));
  }

  @Test
  void einEintrittNachEinerLueckeHinterEinerUnbekanntenSpanneIstBekannt() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            karte(CardStatus.READY), List.of(geloescht(0, 1), zeile(READY, 2, null)), List.of());

    assertThat(aufenthalte)
        .containsExactly(
            stay(B1, null, 0, 1, false, EndedBy.EXIT),
            stay(B1, CardStatus.READY, 2, null, true, null));
  }

  @Test
  void eineAnlageNachDerErstenZeileTeiltDenAufenthaltNicht() {
    List<FlussStay> aufenthalte =
        rekonstruiere(
            new Kartenstand(KARTEN_ID, CardStatus.READY, um(1), null, null),
            List.of(zeile(READY, 0, null)),
            List.of());

    assertThat(aufenthalte).containsExactly(stay(B1, CardStatus.READY, 0, null, true, null));
  }
}
