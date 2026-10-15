package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Karteneintrag;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Wochenwert;
import org.mwolff.manban.nightrun.domain.Nacharbeit.Zustand;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Aktivitaet;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.BoardKarte;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Eingaben;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Kartengruppe;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Kommentar;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.LabelEreignis;

/** Die Zählung der Platte „Nacharbeit“ (Issue #1756, Plan #1752 E1, E3–E10). */
// PMD.TooManyMethods: Das Paket verlangt je Szenario der fachlichen Quelle und je Grenzfall der
// Zählung einen Test in genau dieser Datei; eine Aufteilung risse die Szenarien auseinander.
@SuppressWarnings("PMD.TooManyMethods")
class NacharbeitErmittlungTest {

  private static final Instant JETZT = Instant.parse("2026-10-15T06:00:00Z");

  /** Ein Run der letzten Nacht, im jüngsten Fenster. */
  private static final Instant NACHT = vor(Duration.ofHours(8));

  /** Kennung einer interaktiven Sitzung: zu keinem aufbewahrten NIGHT-Lauf gehörig. */
  private static final Instant INTERAKTIV = vor(Duration.ofHours(2));

  private static Instant vor(Duration abstand) {
    return JETZT.minus(abstand);
  }

  private static Instant tageZurueck(int tage) {
    return vor(Duration.ofDays(tage));
  }

  private static BoardKarte paket(long id) {
    return new BoardKarte(id, (int) (1000 + id), "Paket " + id, Kartengruppe.PAKET);
  }

  private static BoardKarte dokument(long id) {
    return new BoardKarte(id, (int) (2000 + id), "Plan " + id, Kartengruppe.DOKUMENT);
  }

  private static Aktivitaet nachInProgress(long cardId, Instant lauf) {
    return bewegung(cardId, lauf, "IN_PROGRESS", "READY");
  }

  private static Aktivitaet zurueck(long cardId, Instant lauf) {
    return bewegung(cardId, lauf, "BACKLOG", "IN_PROGRESS");
  }

  private static Aktivitaet bewegung(
      long cardId, Instant lauf, String nachher, @Nullable String vorher) {
    return new Aktivitaet(cardId, "MOVED", lauf, nachher, vorher);
  }

  /** Die Eingaben einer Lage am Board; Erfassungsbeginn lange vor allen Runs. */
  private static final class Lage {
    private final List<BoardKarte> karten = new ArrayList<>();
    private final List<Instant> laufstarts = new ArrayList<>();
    private Instant erfassungsbeginn = tageZurueck(400);
    private final List<Aktivitaet> aktivitaeten = new ArrayList<>();
    private final List<Kommentar> kommentare = new ArrayList<>();
    private final List<LabelEreignis> labels = new ArrayList<>();

    Lage lauf(Instant start) {
      laufstarts.add(start);
      return this;
    }

    /** Pakete {@code von..bis}, im Run nach In Progress gezogen. */
    Lage pakete(int von, int bis, Instant lauf) {
      IntStream.rangeClosed(von, bis)
          .forEach(
              id -> {
                karten.add(paket(id));
                aktivitaeten.add(nachInProgress(id, lauf));
              });
      return this;
    }

    /** Dokumente {@code von..bis}, an denen der Run einen Kommentar schrieb. */
    Lage dokumente(int von, int bis, Instant lauf) {
      IntStream.rangeClosed(von, bis)
          .forEach(
              id -> {
                karten.add(dokument(id));
                kommentare.add(new Kommentar(id, lauf));
              });
      return this;
    }

    Lage karte(BoardKarte karte) {
      karten.add(karte);
      return this;
    }

    Lage aktivitaet(Aktivitaet aktivitaet) {
      aktivitaeten.add(aktivitaet);
      return this;
    }

    Lage kommentar(long cardId, Instant lauf) {
      kommentare.add(new Kommentar(cardId, lauf));
      return this;
    }

    Lage label(long cardId, String name, Instant lauf) {
      labels.add(new LabelEreignis(cardId, name, lauf));
      return this;
    }

    Lage erfassungSeit(Instant seit) {
      erfassungsbeginn = seit;
      return this;
    }

    Nacharbeit ermittle() {
      return NacharbeitErmittlung.ermittle(
          new Eingaben(karten, laufstarts, erfassungsbeginn, aktivitaeten, kommentare, labels),
          JETZT);
    }
  }

  private static Lage vierzehnPaketeEinesZurueck() {
    return new Lage().lauf(NACHT).pakete(1, 14, NACHT).aktivitaet(zurueck(5, NACHT));
  }

  private static Lage sechsDokumenteEinesAngehalten() {
    return new Lage().lauf(NACHT).dokumente(1, 6, NACHT).label(3, "kit:clarify", NACHT);
  }

  private static Wochenwert jungeWochePakete(Nacharbeit nacharbeit) {
    return nacharbeit.wochen().getLast().pakete();
  }

  @Test
  @DisplayName("Szenario: Der Runner gibt ein Paket zurück")
  void derRunnerGibtEinPaketZurueck() {
    Nacharbeit nacharbeit = vierzehnPaketeEinesZurueck().ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(14);
    assertThat(nacharbeit.pakete().karten()).containsExactly(new Karteneintrag(1005, "Paket 5", 1));
    assertThat(jungeWochePakete(nacharbeit)).isEqualTo(new Wochenwert(Zustand.QUOTE, 1, 14));
  }

  @Test
  @DisplayName("Szenario: Der Nachtlauf hält einen Plan an")
  void derNachtlaufHaeltEinenPlanAn() {
    Nacharbeit nacharbeit = sechsDokumenteEinesAngehalten().ermittle();

    assertThat(nacharbeit.dokumente().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.dokumente().bearbeitet()).isEqualTo(6);
    assertThat(nacharbeit.dokumente().karten())
        .containsExactly(new Karteneintrag(2003, "Plan 3", 1));
    assertThat(nacharbeit.wochen().getLast().dokumente())
        .isEqualTo(new Wochenwert(Zustand.QUOTE, 1, 6));
  }

  @Test
  @DisplayName("Szenario: Tagesarbeit zählt nicht")
  void tagesarbeitZaehltNicht() {
    Nacharbeit nacharbeit =
        vierzehnPaketeEinesZurueck()
            .karte(paket(20))
            .aktivitaet(bewegung(7, INTERAKTIV, "READY", "IN_PROGRESS"))
            .aktivitaet(bewegung(20, INTERAKTIV, "PARKED", "READY"))
            .aktivitaet(bewegung(20, INTERAKTIV, "READY", "PARKED"))
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(14);
  }

  @Test
  @DisplayName("Szenario: Der Owner klärt einen Halt")
  void derOwnerKlaertEinenHalt() {
    // Das Abnehmen eines Labels wird nicht erfasst; die Antwort des Owners trägt keine Run-Kennung.
    Nacharbeit nacharbeit = sechsDokumenteEinesAngehalten().kommentar(3, INTERAKTIV).ermittle();

    assertThat(nacharbeit.dokumente().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.dokumente().bearbeitet()).isEqualTo(6);
  }

  @Test
  @DisplayName("Szenario: Ein Paket scheitert zweimal")
  void einPaketScheitertZweimal() {
    Instant vorigeWoche = tageZurueck(8);
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(vorigeWoche)
            .lauf(NACHT)
            .pakete(1, 7, vorigeWoche)
            .aktivitaet(zurueck(1, vorigeWoche))
            .pakete(8, 14, NACHT)
            .aktivitaet(nachInProgress(1, NACHT))
            .aktivitaet(zurueck(1, NACHT))
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(14);
    assertThat(nacharbeit.pakete().karten()).containsExactly(new Karteneintrag(1001, "Paket 1", 2));
    assertThat(nacharbeit.wochen().get(Wochenfenster.ANZAHL - 2).pakete())
        .isEqualTo(new Wochenwert(Zustand.QUOTE, 1, 7));
    assertThat(jungeWochePakete(nacharbeit)).isEqualTo(new Wochenwert(Zustand.QUOTE, 1, 8));
  }

  @Test
  @DisplayName("Pakete zählen in der Kartenliste je Rückzug, auch zweimal im selben Run (E9)")
  void einPaketScheitertZweimalImSelbenRun() {
    Nacharbeit nacharbeit =
        vierzehnPaketeEinesZurueck()
            .aktivitaet(nachInProgress(5, NACHT))
            .aktivitaet(zurueck(5, NACHT))
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.pakete().karten()).containsExactly(new Karteneintrag(1005, "Paket 5", 2));
  }

  @Test
  @DisplayName("Szenario: Übersprungene Pakete zählen nicht")
  void uebersprungenePaketeZaehlenNicht() {
    Nacharbeit nacharbeit =
        vierzehnPaketeEinesZurueck()
            .karte(paket(15))
            .karte(paket(16))
            .aktivitaet(bewegung(15, NACHT, "BACKLOG", "READY"))
            .aktivitaet(bewegung(16, NACHT, "BACKLOG", "READY"))
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(14);
  }

  @Test
  @DisplayName("Szenario: Keine Runs im Zeitraum")
  void keineRunsImZeitraum() {
    Instant alt = tageZurueck(100);
    Nacharbeit nacharbeit = new Lage().lauf(alt).pakete(1, 3, alt).ermittle();

    assertThat(nacharbeit.runsImZeitraum()).isFalse();
    assertThat(nacharbeit.pakete().bearbeitet()).isZero();
    assertThat(nacharbeit.dokumente().bearbeitet()).isZero();
    assertThat(nacharbeit.pakete().datenbeginn()).isEqualTo(alt);
    assertThat(nacharbeit.wochen())
        .allSatisfy(w -> assertThat(w.pakete().zustand()).isEqualTo(Zustand.KEINE_KARTE));
  }

  @Test
  void ohneAufbewahrtenRunHatKeineWocheDaten() {
    Nacharbeit nacharbeit = new Lage().pakete(1, 3, NACHT).ermittle();

    assertThat(nacharbeit.runsImZeitraum()).isFalse();
    assertThat(nacharbeit.pakete().datenbeginn()).isNull();
    assertThat(nacharbeit.dokumente().datenbeginn()).isNull();
    assertThat(nacharbeit.pakete().bearbeitet()).isZero();
    assertThat(nacharbeit.wochen())
        .allSatisfy(
            w -> {
              assertThat(w.pakete().zustand()).isEqualTo(Zustand.KEINE_DATEN);
              assertThat(w.dokumente().zustand()).isEqualTo(Zustand.KEINE_DATEN);
            });
  }

  @Test
  void einRunNachJetztZaehltNichtAlsRunImZeitraum() {
    Nacharbeit nacharbeit = new Lage().lauf(JETZT.plusSeconds(60)).ermittle();

    assertThat(nacharbeit.runsImZeitraum()).isFalse();
  }

  @Test
  @DisplayName("Szenario: Runs nur mit Paketen")
  void runsNurMitPaketen() {
    Nacharbeit nacharbeit = vierzehnPaketeEinesZurueck().ermittle();

    assertThat(nacharbeit.runsImZeitraum()).isTrue();
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(14);
    assertThat(nacharbeit.dokumente().bearbeitet()).isZero();
    assertThat(nacharbeit.dokumente().karten()).isEmpty();
    assertThat(nacharbeit.wochen().getLast().dokumente())
        .isEqualTo(new Wochenwert(Zustand.KEINE_KARTE, 0, 0));
  }

  @Test
  @DisplayName("Szenario: Ältere Wochen ohne Daten")
  void aeltereWochenOhneDaten() {
    Instant aeltester = tageZurueck(33);
    Nacharbeit nacharbeit = new Lage().lauf(aeltester).lauf(NACHT).pakete(1, 2, NACHT).ermittle();

    assertThat(nacharbeit.pakete().datenbeginn()).isEqualTo(aeltester);
    assertThat(nacharbeit.wochen().subList(0, 7))
        .allSatisfy(w -> assertThat(w.pakete().zustand()).isEqualTo(Zustand.KEINE_DATEN));
    assertThat(nacharbeit.wochen().subList(7, 11))
        .allSatisfy(w -> assertThat(w.pakete().zustand()).isEqualTo(Zustand.KEINE_KARTE));
    assertThat(jungeWochePakete(nacharbeit)).isEqualTo(new Wochenwert(Zustand.QUOTE, 0, 2));
  }

  @Test
  void wochenTragenIhrenBeginnAusDenWochenfenstern() {
    Nacharbeit nacharbeit = new Lage().ermittle();

    assertThat(nacharbeit.wochen()).hasSize(Wochenfenster.ANZAHL);
    for (int i = 0; i < Wochenfenster.ANZAHL; i++) {
      assertThat(nacharbeit.wochen().get(i).beginn()).isEqualTo(Wochenfenster.beginn(i, JETZT));
    }
  }

  @Test
  void kartenAusserhalbDerBoardKartenZaehlenNicht() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .pakete(1, 2, NACHT)
            .aktivitaet(nachInProgress(99, NACHT))
            .aktivitaet(zurueck(99, NACHT))
            .kommentar(98, NACHT)
            .label(98, "kit:clarify", NACHT)
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isZero();
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(2);
    assertThat(nacharbeit.dokumente().bearbeitet()).isZero();
  }

  @Test
  void andereKartenZaehlenInKeinerGruppe() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .karte(new BoardKarte(50, 1050, "Vorhaben", Kartengruppe.ANDERE))
            .aktivitaet(nachInProgress(50, NACHT))
            .aktivitaet(zurueck(50, NACHT))
            .kommentar(50, NACHT)
            .label(50, "kit:clarify", NACHT)
            .ermittle();

    assertThat(nacharbeit.pakete().bearbeitet()).isZero();
    assertThat(nacharbeit.dokumente().bearbeitet()).isZero();
  }

  @Test
  void interaktiveLaeufeZaehlenNicht() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .pakete(1, 2, INTERAKTIV)
            .aktivitaet(zurueck(1, INTERAKTIV))
            .dokumente(3, 3, INTERAKTIV)
            .label(3, "kit:clarify", INTERAKTIV)
            .ermittle();

    assertThat(nacharbeit.runsImZeitraum()).isTrue();
    assertThat(nacharbeit.pakete().bearbeitet()).isZero();
    assertThat(nacharbeit.pakete().betroffen()).isZero();
    assertThat(nacharbeit.dokumente().bearbeitet()).isZero();
  }

  @Test
  void laufstartZaehltAufMillisekundenGekuerzt() {
    Instant mitNanos = NACHT.plusNanos(123_456);
    Nacharbeit nacharbeit = new Lage().lauf(mitNanos).pakete(1, 1, NACHT).ermittle();

    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(1);
  }

  @Test
  void eineSpurNachDemFensterendeZaehltInDieWocheIhresLaufstarts() {
    // Der Run startete kurz vor der Grenze zum jüngsten Fenster; seine Spuren entstanden danach.
    Instant start = vor(Duration.ofDays(7).plusMinutes(30));
    Nacharbeit nacharbeit =
        new Lage().lauf(start).pakete(1, 1, start).aktivitaet(zurueck(1, start)).ermittle();

    assertThat(nacharbeit.wochen().get(Wochenfenster.ANZAHL - 2).pakete())
        .isEqualTo(new Wochenwert(Zustand.QUOTE, 1, 1));
    assertThat(jungeWochePakete(nacharbeit)).isEqualTo(new Wochenwert(Zustand.KEINE_KARTE, 0, 0));
  }

  @Test
  void einRunVorDemAeltestenFensterZaehltNicht() {
    Instant alt = tageZurueck(90);
    Nacharbeit nacharbeit =
        new Lage().lauf(alt).lauf(NACHT).pakete(1, 1, alt).aktivitaet(zurueck(1, alt)).ermittle();

    assertThat(nacharbeit.pakete().bearbeitet()).isZero();
    assertThat(nacharbeit.pakete().karten()).isEmpty();
  }

  @Test
  void beideHaltLabelsImSelbenRunZaehlenEinmal() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .karte(dokument(1))
            .label(1, "kit:clarify", NACHT)
            .label(1, "run:waiting", NACHT)
            .ermittle();

    assertThat(nacharbeit.dokumente().karten())
        .containsExactly(new Karteneintrag(2001, "Plan 1", 1));
    assertThat(nacharbeit.dokumente().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.dokumente().bearbeitet()).isEqualTo(1);
  }

  @Test
  void deutscheHaltLabelsZaehlenJeRun() {
    Instant vorigeWoche = tageZurueck(8);
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(vorigeWoche)
            .lauf(NACHT)
            .karte(dokument(1))
            .label(1, "kit:klaeren", vorigeWoche)
            .label(1, "lauf:wartet", NACHT)
            .ermittle();

    assertThat(nacharbeit.dokumente().karten())
        .containsExactly(new Karteneintrag(2001, "Plan 1", 2));
    assertThat(nacharbeit.dokumente().betroffen()).isEqualTo(1);
  }

  @Test
  void andereLabelsHaltenNichtAnZaehlenAberAlsBearbeitet() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .karte(dokument(1))
            .label(1, "kit:protected", NACHT)
            .karte(paket(2))
            .label(2, "kit:clarify", NACHT)
            .ermittle();

    assertThat(nacharbeit.dokumente().betroffen()).isZero();
    assertThat(nacharbeit.dokumente().bearbeitet()).isEqualTo(1);
    assertThat(nacharbeit.pakete().bearbeitet()).isZero();
  }

  @Test
  void rueckzugAusReadyZaehltNicht() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .karte(paket(1))
            .aktivitaet(bewegung(1, NACHT, "BACKLOG", "READY"))
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isZero();
    assertThat(nacharbeit.pakete().bearbeitet()).isZero();
  }

  @Test
  void rueckzugNachReadyZaehlt() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .pakete(1, 1, NACHT)
            .aktivitaet(bewegung(1, NACHT, "READY", "IN_PROGRESS"))
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isEqualTo(1);
  }

  @Test
  void rueckzugNachInReviewZaehltNicht() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .pakete(1, 1, NACHT)
            .aktivitaet(bewegung(1, NACHT, "IN_REVIEW", "IN_PROGRESS"))
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isZero();
  }

  @Test
  void rueckzugOhneBekanntenVorgaengerstatusZaehltNicht() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .karte(paket(1))
            .aktivitaet(bewegung(1, NACHT, "BACKLOG", null))
            .ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isZero();
  }

  @Test
  void eineKarteMitSignalZaehltImmerZurBezugszahl() {
    // Der Mensch zog das Paket nach In Progress, der Run zog es zurück.
    Nacharbeit nacharbeit =
        new Lage().lauf(NACHT).karte(paket(1)).aktivitaet(zurueck(1, NACHT)).ermittle();

    assertThat(nacharbeit.pakete().betroffen()).isEqualTo(1);
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(1);
  }

  @Test
  void nurAngelegterPlanZaehltNichtZurBezugszahl() {
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(NACHT)
            .karte(dokument(1))
            .aktivitaet(new Aktivitaet(1, "CREATED", NACHT, null, null))
            .karte(dokument(2))
            .aktivitaet(new Aktivitaet(2, "UPDATED", NACHT, null, null))
            .ermittle();

    assertThat(nacharbeit.dokumente().bearbeitet()).isEqualTo(1);
  }

  @Test
  void planInZweiRunsBearbeitetLaufstandUeberschrieben() {
    // Den Laufstand trägt nur der letzte Schreiber; der frühere Run zählt über seine Aktivität.
    Instant vorigeWoche = tageZurueck(8);
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(vorigeWoche)
            .lauf(NACHT)
            .karte(dokument(1))
            .aktivitaet(new Aktivitaet(1, "UPDATED", vorigeWoche, null, null))
            .kommentar(1, NACHT)
            .ermittle();

    assertThat(nacharbeit.wochen().get(Wochenfenster.ANZAHL - 2).dokumente())
        .isEqualTo(new Wochenwert(Zustand.QUOTE, 0, 1));
    assertThat(nacharbeit.wochen().getLast().dokumente())
        .isEqualTo(new Wochenwert(Zustand.QUOTE, 0, 1));
    assertThat(nacharbeit.dokumente().bearbeitet()).isEqualTo(1);
  }

  @Test
  void bezugszahlZaehltJedeKarteJeWocheUndObenHoechstensEinmal() {
    Instant zweiteNacht = vor(Duration.ofDays(2));
    Instant vorigeWoche = tageZurueck(8);
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(vorigeWoche)
            .lauf(zweiteNacht)
            .lauf(NACHT)
            .pakete(1, 1, NACHT)
            .aktivitaet(nachInProgress(1, zweiteNacht))
            .aktivitaet(nachInProgress(1, vorigeWoche))
            .ermittle();

    assertThat(jungeWochePakete(nacharbeit)).isEqualTo(new Wochenwert(Zustand.QUOTE, 0, 1));
    assertThat(nacharbeit.wochen().get(Wochenfenster.ANZAHL - 2).pakete())
        .isEqualTo(new Wochenwert(Zustand.QUOTE, 0, 1));
    assertThat(nacharbeit.pakete().bearbeitet()).isEqualTo(1);
  }

  @Test
  void dokumenteHabenDatenErstAbDemErfassungsbeginn() {
    Instant aeltester = tageZurueck(60);
    Instant erfassung = tageZurueck(10);
    Instant vorDerErfassung = tageZurueck(20);
    Nacharbeit nacharbeit =
        new Lage()
            .erfassungSeit(erfassung)
            .lauf(aeltester)
            .lauf(vorDerErfassung)
            .lauf(NACHT)
            .pakete(1, 1, vorDerErfassung)
            .dokumente(11, 11, vorDerErfassung)
            .label(11, "kit:clarify", vorDerErfassung)
            .dokumente(12, 12, NACHT)
            .ermittle();

    assertThat(nacharbeit.pakete().datenbeginn()).isEqualTo(aeltester);
    assertThat(nacharbeit.dokumente().datenbeginn()).isEqualTo(erfassung);
    int wocheVorDerErfassung = Wochenfenster.index(vorDerErfassung, JETZT).orElseThrow();
    assertThat(nacharbeit.wochen().get(wocheVorDerErfassung).dokumente())
        .isEqualTo(new Wochenwert(Zustand.KEINE_DATEN, 0, 0));
    assertThat(nacharbeit.wochen().get(wocheVorDerErfassung).pakete())
        .isEqualTo(new Wochenwert(Zustand.QUOTE, 0, 1));
    assertThat(nacharbeit.dokumente().bearbeitet()).isEqualTo(1);
    assertThat(nacharbeit.dokumente().betroffen()).isZero();
    assertThat(nacharbeit.dokumente().karten()).isEmpty();
  }

  @Test
  void dokumenteBeginnenMitDemAeltestenRunWennDieErfassungFrueherBegann() {
    Instant aeltester = tageZurueck(20);
    Nacharbeit nacharbeit = new Lage().erfassungSeit(tageZurueck(30)).lauf(aeltester).ermittle();

    assertThat(nacharbeit.dokumente().datenbeginn()).isEqualTo(aeltester);
  }

  @Test
  void eineWocheDieGenauAmDatenbeginnEndetHatDaten() {
    // Wochenfenster.index ordnet den Grenzzeitpunkt dem älteren Fenster zu; dort liegt der Run.
    Instant aeltester = Wochenfenster.beginn(Wochenfenster.ANZAHL - 1, JETZT);
    Nacharbeit nacharbeit = new Lage().lauf(aeltester).ermittle();

    assertThat(Wochenfenster.index(aeltester, JETZT)).hasValue(Wochenfenster.ANZAHL - 2);
    assertThat(nacharbeit.wochen().get(Wochenfenster.ANZAHL - 2).pakete().zustand())
        .isEqualTo(Zustand.KEINE_KARTE);
    assertThat(nacharbeit.wochen().get(Wochenfenster.ANZAHL - 3).pakete().zustand())
        .isEqualTo(Zustand.KEINE_DATEN);
  }

  @Test
  void eineWocheDieKurzVorDemDatenbeginnEndetHatKeineDaten() {
    Instant aeltester = Wochenfenster.beginn(Wochenfenster.ANZAHL - 1, JETZT).plusMillis(1);
    Nacharbeit nacharbeit = new Lage().lauf(aeltester).ermittle();

    assertThat(nacharbeit.wochen().get(Wochenfenster.ANZAHL - 2).pakete().zustand())
        .isEqualTo(Zustand.KEINE_DATEN);
    assertThat(jungeWochePakete(nacharbeit).zustand()).isEqualTo(Zustand.KEINE_KARTE);
  }

  @Test
  void kartenlisteNachAnzahlAbsteigendDannNachNummer() {
    Instant vorigeWoche = tageZurueck(8);
    Nacharbeit nacharbeit =
        new Lage()
            .lauf(vorigeWoche)
            .lauf(NACHT)
            .pakete(1, 3, NACHT)
            .aktivitaet(zurueck(3, NACHT))
            .aktivitaet(zurueck(1, NACHT))
            .aktivitaet(zurueck(2, vorigeWoche))
            .aktivitaet(zurueck(2, NACHT))
            .ermittle();

    assertThat(nacharbeit.pakete().karten())
        .containsExactly(
            new Karteneintrag(1002, "Paket 2", 2),
            new Karteneintrag(1001, "Paket 1", 1),
            new Karteneintrag(1003, "Paket 3", 1));
  }
}
