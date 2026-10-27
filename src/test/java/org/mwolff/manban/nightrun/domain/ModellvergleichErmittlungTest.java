package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.ABDECKUNG;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.PAKETE;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.PLAN;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.REVIEW;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.UMSETZUNG;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Kopf;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Stufe;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Zeile;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung.Eingaben;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung.Vorgang;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Aktivitaet;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.BoardKarte;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Kartengruppe;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.LabelEreignis;

/** Der Modellvergleich je Stufe (Issue #1946, Plan #1905 E7, E8, E10–E12, E15). */
// PMD.TooManyMethods: Das Paket verlangt je Szenario der fachlichen Quelle und je weiterem Fall
// einen Test in genau dieser Datei; eine Aufteilung risse die Szenarien auseinander.
@SuppressWarnings("PMD.TooManyMethods")
class ModellvergleichErmittlungTest {

  private static final Instant JETZT = Instant.parse("2026-10-15T06:00:00Z");

  /** Ein Run der letzten Nacht, im jüngsten Fenster. */
  private static final Instant NACHT = vor(Duration.ofHours(8));

  /** Ein Run der Nacht davor. */
  private static final Instant VORNACHT = vor(Duration.ofHours(32));

  private static final String OPUS = "claude-opus-5-5";
  private static final String FABLE = "claude-fable-5-1";

  /** Abstand der Karten-ID zur Kartennummer, damit eine Verwechslung auffällt. */
  private static final int ID_VERSATZ = 50_000;

  private static Instant vor(Duration abstand) {
    return JETZT.minus(abstand);
  }

  private static Instant tageZurueck(int tage) {
    return vor(Duration.ofDays(tage));
  }

  private static NightRunUsage verbrauch(@Nullable String kosten, @Nullable Long modellzeitMs) {
    return new NightRunUsage(
        kosten == null ? null : new BigDecimal(kosten), null, null, null, modellzeitMs, null);
  }

  private static NightRunAttempt vorgang(
      NightRunAttemptStage stufe,
      int karte,
      @Nullable String modell,
      @Nullable String denktiefe,
      boolean gelungen,
      @Nullable NightRunUsage verbrauch,
      List<Integer> geschrieben) {
    return new NightRunAttempt(stufe, karte, modell, denktiefe, gelungen, verbrauch, geschrieben);
  }

  private static NightRunAttempt umsetzung(int karte, @Nullable String modell) {
    return vorgang(UMSETZUNG, karte, modell, null, true, verbrauch("1.50", 60_000L), List.of());
  }

  private static NightRunAttempt plan(int karte, String modell, Integer... geschrieben) {
    return vorgang(PLAN, karte, modell, null, true, verbrauch("2", 1000L), List.of(geschrieben));
  }

  private static NightRunAttempt pruefung(int karte, String modell) {
    return vorgang(REVIEW, karte, modell, null, true, verbrauch("0.5", 500L), List.of());
  }

  /** Die Eingaben einer Lage am Board. */
  private static final class Lage {
    private final List<BoardKarte> karten = new ArrayList<>();
    private final List<Vorgang> vorgaenge = new ArrayList<>();
    private final List<Aktivitaet> aktivitaeten = new ArrayList<>();
    private final List<LabelEreignis> labels = new ArrayList<>();
    private int position;

    Lage paket(int nummer) {
      karten.add(new BoardKarte(nummer + ID_VERSATZ, nummer, "Paket", Kartengruppe.PAKET));
      return this;
    }

    Lage pakete(int von, int bis) {
      IntStream.rangeClosed(von, bis).forEach(this::paket);
      return this;
    }

    Lage dokument(int nummer) {
      karten.add(new BoardKarte(nummer + ID_VERSATZ, nummer, "Plan", Kartengruppe.DOKUMENT));
      return this;
    }

    Lage vorgang(Instant lauf, NightRunAttempt vorgang) {
      Vorgang eintrag = new Vorgang(lauf, NightRunKind.NIGHT, true, position, vorgang);
      position++;
      return eintrag(eintrag);
    }

    Lage eintrag(Vorgang vorgang) {
      vorgaenge.add(vorgang);
      return this;
    }

    /** Umsetzungen der Pakete {@code von..bis} mit {@code modell} im Run {@code lauf}. */
    Lage umsetzungen(int von, int bis, @Nullable String modell, Instant lauf) {
      IntStream.rangeClosed(von, bis).forEach(k -> vorgang(lauf, umsetzung(k, modell)));
      return this;
    }

    Lage rueckzug(int nummer, Instant lauf) {
      aktivitaeten.add(new Aktivitaet(nummer + ID_VERSATZ, "MOVED", lauf, "READY", "IN_PROGRESS"));
      return this;
    }

    Lage bewegung(int nummer, Instant lauf, String nachher, String vorher) {
      aktivitaeten.add(new Aktivitaet(nummer + ID_VERSATZ, "MOVED", lauf, nachher, vorher));
      return this;
    }

    Lage halt(int nummer, Instant lauf) {
      return label(nummer, "kit:clarify", lauf);
    }

    Lage label(int nummer, String name, Instant lauf) {
      labels.add(new LabelEreignis(nummer + ID_VERSATZ, name, lauf));
      return this;
    }

    Modellvergleich ermittle() {
      return ModellvergleichErmittlung.ermittle(
          new Eingaben(karten, vorgaenge, aktivitaeten, labels), JETZT);
    }
  }

  private static Stufe stufe(Modellvergleich vergleich, NightRunAttemptStage stufe) {
    return vergleich.stufen().stream().filter(s -> s.stufe() == stufe).findFirst().orElseThrow();
  }

  private static Zeile zeile(Modellvergleich vergleich, NightRunAttemptStage stufe, String modell) {
    return zeile(vergleich, stufe, modell, null);
  }

  private static Zeile zeile(
      Modellvergleich vergleich,
      NightRunAttemptStage stufe,
      String modell,
      @Nullable String denktiefe) {
    return stufe(vergleich, stufe).zeilen().stream()
        .filter(z -> z.model().equals(modell))
        .filter(z -> denktiefe == null ? z.effort() == null : denktiefe.equals(z.effort()))
        .findFirst()
        .orElseThrow();
  }

  // --- Szenarien der fachlichen Quelle -----------------------------------------------------

  @Test
  @DisplayName("Szenario: Zwei Modelle in der Umsetzung")
  void zweiModelleInDerUmsetzung() {
    Modellvergleich vergleich =
        new Lage()
            .pakete(1, 12)
            .pakete(101, 108)
            .umsetzungen(1, 12, OPUS, NACHT)
            .umsetzungen(101, 108, FABLE, NACHT)
            .rueckzug(1, NACHT)
            .rueckzug(101, NACHT)
            .rueckzug(102, NACHT)
            .rueckzug(103, NACHT)
            .ermittle();

    Zeile fable = zeile(vergleich, UMSETZUNG, FABLE);
    assertThat(fable.vorgaenge()).isEqualTo(8);
    assertThat(fable.erfolgreich()).isEqualTo(5);
    assertThat(fable.enoughData()).isTrue();
    assertThat(fable.kostenMedian()).isEqualByComparingTo("1.50");
    assertThat(fable.modellzeitMedianMs()).isEqualTo(60_000L);
    Zeile opus = zeile(vergleich, UMSETZUNG, OPUS);
    assertThat(opus.vorgaenge()).isEqualTo(12);
    assertThat(opus.erfolgreich()).isEqualTo(11);
    assertThat(opus.kostenMedian()).isEqualByComparingTo("1.50");
    assertThat(opus.modellzeitMedianMs()).isEqualTo(60_000L);
    assertThat(stufe(vergleich, UMSETZUNG).zeilen()).containsExactly(fable, opus);
  }

  @Test
  @DisplayName("Szenario: Zwei Versuche an derselben Karte")
  void zweiVersucheAnDerselbenKarte() {
    Modellvergleich vergleich =
        new Lage()
            .pakete(1, 9)
            .vorgang(VORNACHT, umsetzung(1, FABLE))
            .rueckzug(1, VORNACHT)
            .vorgang(NACHT, umsetzung(1, OPUS))
            .umsetzungen(2, 5, FABLE, VORNACHT)
            .umsetzungen(6, 9, OPUS, NACHT)
            .ermittle();

    Zeile fable = zeile(vergleich, UMSETZUNG, FABLE);
    assertThat(fable.vorgaenge()).isEqualTo(5);
    assertThat(fable.erfolgreich()).isEqualTo(4);
    Zeile opus = zeile(vergleich, UMSETZUNG, OPUS);
    assertThat(opus.vorgaenge()).isEqualTo(5);
    assertThat(opus.erfolgreich()).isEqualTo(5);
  }

  @Test
  @DisplayName("Szenario: Halt in einer späteren Nacht")
  void haltInEinerSpaeterenNacht() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(500)
            .dokument(600)
            .vorgang(VORNACHT, plan(500, OPUS, 600))
            .vorgang(NACHT, pruefung(600, FABLE))
            .halt(600, NACHT)
            .ermittle();

    Zeile opus = zeile(vergleich, PLAN, OPUS);
    assertThat(opus.vorgaenge()).isEqualTo(1);
    assertThat(opus.erfolgreich()).isEqualTo(1);
    Zeile fable = zeile(vergleich, REVIEW, FABLE);
    assertThat(fable.vorgaenge()).isEqualTo(1);
    assertThat(fable.erfolgreich()).isEqualTo(1);
  }

  @Test
  @DisplayName("Szenario: Zu wenige Vorgänge")
  void zuWenigeVorgaenge() {
    Lage lage = new Lage().dokument(600);
    IntStream.range(0, 3).forEach(i -> lage.vorgang(NACHT, pruefung(600, FABLE)));

    Zeile fable = zeile(lage.ermittle(), REVIEW, FABLE);

    assertThat(fable.enoughData()).isFalse();
    assertThat(fable.vorgaenge()).isEqualTo(3);
    assertThat(fable.kostenMedian()).isEqualByComparingTo("0.5");
    assertThat(fable.modellzeitMedianMs()).isEqualTo(500L);
  }

  @Test
  @DisplayName("Szenario: Ältere Läufe ohne Modell")
  void aeltereLaeufeOhneModell() {
    Lage lage = new Lage().pakete(1, 26);
    IntStream.rangeClosed(1, 20)
        .forEach(k -> lage.vorgang(tageZurueck(22 + 3 * k), umsetzung(k, null)));
    int[] tage = {20, 15, 10, 5, 2, 1};
    IntStream.range(0, tage.length)
        .forEach(i -> lage.vorgang(tageZurueck(tage[i]), umsetzung(21 + i, OPUS)));

    Modellvergleich vergleich = lage.ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS).vorgaenge()).isEqualTo(6);
    assertThat(vergleich.kopf()).isEqualTo(new Kopf(tageZurueck(20), false, 0, 6));
  }

  // --- Zurechnung der Nacharbeit (E8, E15) --------------------------------------------------

  @Test
  void haltDurchEinePruefungTrifftDenSchreibendenPlanVorgangDesselbenRuns() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(500)
            .dokument(600)
            .vorgang(NACHT, plan(500, OPUS, 600))
            .vorgang(NACHT, pruefung(600, FABLE))
            .halt(600, NACHT)
            .ermittle();

    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isZero();
    assertThat(zeile(vergleich, REVIEW, FABLE).erfolgreich()).isEqualTo(1);
  }

  @Test
  void haltTrifftNurDenLetztenSchreibendenVorgangDesRuns() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(500)
            .dokument(600)
            .vorgang(NACHT, plan(500, FABLE, 600))
            .vorgang(NACHT, plan(500, OPUS, 600))
            .halt(600, NACHT)
            .halt(600, NACHT)
            .ermittle();

    assertThat(zeile(vergleich, PLAN, FABLE).erfolgreich()).isEqualTo(1);
    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isZero();
  }

  @Test
  void derLetzteVorgangRichtetSichNachDerPositionNichtNachDerEingabefolge() {
    Lage lage = new Lage().dokument(500).dokument(600);
    lage.eintrag(new Vorgang(NACHT, NightRunKind.NIGHT, true, 7, plan(500, OPUS, 600)));
    lage.eintrag(new Vorgang(NACHT, NightRunKind.NIGHT, true, 3, plan(500, FABLE, 600)));

    Modellvergleich vergleich = lage.halt(600, NACHT).ermittle();

    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isZero();
    assertThat(zeile(vergleich, PLAN, FABLE).erfolgreich()).isEqualTo(1);
  }

  @Test
  void haltAnDerKennzeichenKarteEinerFachplanKetteTrifftDenPlanVorgang() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(500)
            .dokument(600)
            .vorgang(NACHT, plan(500, OPUS, 600))
            .vorgang(NACHT, pruefung(600, FABLE))
            .vorgang(NACHT, vorgang(PAKETE, 500, FABLE, null, true, null, List.of()))
            .halt(500, NACHT)
            .ermittle();

    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isZero();
    assertThat(zeile(vergleich, PAKETE, FABLE).erfolgreich()).isEqualTo(1);
    assertThat(zeile(vergleich, REVIEW, FABLE).erfolgreich()).isEqualTo(1);
  }

  @Test
  void haltAnDerKennzeichenKarteEinerPlanAuftragsKetteTrifftDenPaketeVorgang() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(600)
            .dokument(700)
            .vorgang(NACHT, vorgang(PAKETE, 600, OPUS, null, true, null, List.of(700)))
            .vorgang(NACHT, vorgang(ABDECKUNG, 600, FABLE, null, true, null, List.of()))
            .halt(600, NACHT)
            .ermittle();

    assertThat(zeile(vergleich, PAKETE, OPUS).erfolgreich()).isZero();
    assertThat(zeile(vergleich, ABDECKUNG, FABLE).erfolgreich()).isEqualTo(1);
  }

  @Test
  void haltOhnePassendenVorgangZaehltNirgends() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(500)
            .dokument(600)
            .dokument(800)
            .vorgang(NACHT, plan(500, OPUS, 600))
            .vorgang(NACHT, pruefung(800, FABLE))
            .vorgang(NACHT, vorgang(UMSETZUNG, 800, FABLE, null, true, null, List.of(800)))
            .halt(800, NACHT)
            .ermittle();

    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isEqualTo(1);
    assertThat(zeile(vergleich, REVIEW, FABLE).erfolgreich()).isEqualTo(1);
    assertThat(zeile(vergleich, UMSETZUNG, FABLE).erfolgreich()).isEqualTo(1);
  }

  @Test
  void haltAnEinemPaketZaehltNicht() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(500)
            .paket(1)
            .vorgang(NACHT, plan(500, OPUS, 1))
            .halt(1, NACHT)
            .ermittle();

    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isEqualTo(1);
  }

  @Test
  void haltAnEinerFremdenKarteZaehltNicht() {
    Modellvergleich vergleich =
        new Lage().dokument(500).vorgang(NACHT, plan(500, OPUS, 900)).halt(900, NACHT).ermittle();

    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isEqualTo(1);
  }

  @Test
  void einLaufWartetLabelInEnglischIstEinHaltEinAnderesLabelNicht() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(500)
            .dokument(600)
            .dokument(700)
            .vorgang(NACHT, plan(500, OPUS, 600))
            .vorgang(NACHT, plan(500, FABLE, 700))
            .label(600, "run:waiting", NACHT)
            .label(700, "kit:protected", NACHT)
            .ermittle();

    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isZero();
    assertThat(zeile(vergleich, PLAN, FABLE).erfolgreich()).isEqualTo(1);
  }

  @Test
  void rueckzuegeTreffenDieErstenUmsetzungenDerKarteImRunNachPosition() {
    Lage lage = new Lage().paket(1).paket(2);
    lage.eintrag(new Vorgang(NACHT, NightRunKind.NIGHT, true, 9, umsetzung(1, OPUS)));
    lage.eintrag(new Vorgang(NACHT, NightRunKind.NIGHT, true, 4, umsetzung(1, FABLE)));
    lage.eintrag(new Vorgang(NACHT, NightRunKind.NIGHT, true, 5, umsetzung(2, FABLE)));

    Modellvergleich vergleich = lage.rueckzug(1, NACHT).ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, FABLE).erfolgreich()).isEqualTo(1);
    assertThat(zeile(vergleich, UMSETZUNG, OPUS).erfolgreich()).isEqualTo(1);
  }

  @Test
  void zweiRueckzuegeTreffenZweiUmsetzungen() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(NACHT, umsetzung(1, FABLE))
            .vorgang(NACHT, umsetzung(1, FABLE))
            .vorgang(NACHT, umsetzung(1, OPUS))
            .rueckzug(1, NACHT)
            .rueckzug(1, NACHT)
            .ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, FABLE).erfolgreich()).isZero();
    assertThat(zeile(vergleich, UMSETZUNG, OPUS).erfolgreich()).isEqualTo(1);
  }

  @Test
  void nurEineBewegungAusInProgressIstEinRueckzug() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .paket(2)
            .vorgang(NACHT, umsetzung(1, OPUS))
            .vorgang(NACHT, umsetzung(2, OPUS))
            .bewegung(1, NACHT, "BACKLOG", "READY")
            .bewegung(2, NACHT, "IN_REVIEW", "IN_PROGRESS")
            .ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS).erfolgreich()).isEqualTo(2);
  }

  @Test
  void einRueckzugAnEinemDokumentOderEinerFremdenKarteTrifftKeineUmsetzung() {
    Modellvergleich vergleich =
        new Lage()
            .dokument(600)
            .vorgang(NACHT, umsetzung(600, OPUS))
            .rueckzug(600, NACHT)
            .rueckzug(4711, NACHT)
            .ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS).erfolgreich()).isEqualTo(1);
  }

  @Test
  void einNichtGelungenerVorgangIstNieErfolgreich() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .dokument(600)
            .vorgang(NACHT, vorgang(UMSETZUNG, 1, OPUS, null, false, null, List.of()))
            .vorgang(NACHT, vorgang(REVIEW, 600, OPUS, null, false, null, List.of()))
            .vorgang(NACHT, vorgang(PLAN, 600, OPUS, null, false, null, List.of(600)))
            .ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS).erfolgreich()).isZero();
    assertThat(zeile(vergleich, REVIEW, OPUS).erfolgreich()).isZero();
    assertThat(zeile(vergleich, PLAN, OPUS).erfolgreich()).isZero();
  }

  @Test
  void eineUmsetzungAnEinemAlsUebergangenGemeldetenPaketIstErfolgreich() {
    Modellvergleich vergleich = new Lage().paket(1).vorgang(NACHT, umsetzung(1, OPUS)).ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS).erfolgreich()).isEqualTo(1);
  }

  @Test
  void dieLaufkennungIstDerAufMillisekundenGekuerzteStart() {
    Instant mitNanos = NACHT.plusNanos(123_456);
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(mitNanos, umsetzung(1, OPUS))
            .rueckzug(1, NACHT.plusNanos(123_000))
            .ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS).erfolgreich()).isZero();
  }

  // --- Welche Vorgänge zählen (E7) -----------------------------------------------------------

  @Test
  void einVomWaechterAbgeschlossenerLaufZaehltEinVonHandBeendeterNicht() {
    Lage lage = new Lage().paket(1).paket(2);
    // abortKind = SILENCED, complete = true: der Lauf hat sein Ende gemeldet
    lage.eintrag(new Vorgang(NACHT, NightRunKind.NIGHT, true, 0, umsetzung(1, OPUS)));
    // nur per closed_at beendet, complete = false
    lage.eintrag(new Vorgang(VORNACHT, NightRunKind.NIGHT, false, 0, umsetzung(2, OPUS)));

    assertThat(zeile(lage.ermittle(), UMSETZUNG, OPUS).vorgaenge()).isEqualTo(1);
  }

  @Test
  void einLaufOhneEndeZaehltErstNachSeinerMeldungMitEnde() {
    Vorgang laufend = new Vorgang(NACHT, NightRunKind.NIGHT, false, 0, umsetzung(1, OPUS));
    Vorgang beendet = new Vorgang(NACHT, NightRunKind.NIGHT, true, 0, umsetzung(1, OPUS));

    Modellvergleich vorher = new Lage().paket(1).eintrag(laufend).ermittle();
    Modellvergleich nachher = new Lage().paket(1).eintrag(beendet).ermittle();

    assertThat(stufe(vorher, UMSETZUNG).zeilen()).isEmpty();
    assertThat(vorher.kopf().vorgaengeImZeitraum()).isZero();
    assertThat(zeile(nachher, UMSETZUNG, OPUS).vorgaenge()).isEqualTo(1);
  }

  @Test
  void interaktiveSitzungenZaehlenNicht() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .eintrag(new Vorgang(NACHT, NightRunKind.INTERACTIVE, true, 0, umsetzung(1, OPUS)))
            .ermittle();

    assertThat(stufe(vergleich, UMSETZUNG).zeilen()).isEmpty();
  }

  @Test
  void kartenAndererBoardsZaehlenNicht() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(NACHT, umsetzung(1, OPUS))
            .vorgang(NACHT, umsetzung(2, OPUS))
            .vorgang(NACHT, umsetzung(3, null))
            .ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS).vorgaenge()).isEqualTo(1);
    assertThat(vergleich.kopf().ohneModell()).isZero();
  }

  @Test
  void dieFenstergrenzenRichtenSichNachDemLaufstart() {
    Instant zwoelfWochen = vor(Duration.ofDays(84));
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(zwoelfWochen.plusSeconds(1), umsetzung(1, OPUS))
            .vorgang(zwoelfWochen, umsetzung(1, FABLE))
            .vorgang(JETZT, umsetzung(1, OPUS))
            .vorgang(JETZT.plusSeconds(1), umsetzung(1, FABLE))
            .ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS).vorgaenge()).isEqualTo(2);
    assertThat(stufe(vergleich, UMSETZUNG).zeilen()).hasSize(1);
  }

  // --- Medians und Teilmengen (E10) ----------------------------------------------------------

  private static NightRunAttempt mitVerbrauch(@Nullable String kosten, @Nullable Long ms) {
    return vorgang(UMSETZUNG, 1, OPUS, null, true, verbrauch(kosten, ms), List.of());
  }

  @Test
  void derMedianEinerUngeradenAnzahlIstDerMittlereWert() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(NACHT, mitVerbrauch("10", 9000L))
            .vorgang(NACHT, mitVerbrauch("1", 1000L))
            .vorgang(NACHT, mitVerbrauch("2", 3000L))
            .ermittle();

    Zeile opus = zeile(vergleich, UMSETZUNG, OPUS);
    assertThat(opus.kostenMedian()).isEqualByComparingTo("2");
    assertThat(opus.modellzeitMedianMs()).isEqualTo(3000L);
  }

  @Test
  void derMedianEinerGeradenAnzahlIstDasMittelDerBeidenMittleren() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(NACHT, mitVerbrauch("10", 9000L))
            .vorgang(NACHT, mitVerbrauch("1", 1000L))
            .vorgang(NACHT, mitVerbrauch("3", 2001L))
            .vorgang(NACHT, mitVerbrauch("2", 2000L))
            .ermittle();

    Zeile opus = zeile(vergleich, UMSETZUNG, OPUS);
    assertThat(opus.kostenMedian()).isEqualByComparingTo("2.5");
    assertThat(opus.modellzeitMedianMs()).isEqualTo(2001L);
  }

  @Test
  void einWertOhneAngabeZaehltNurInDerZahlDerVorgaenge() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(NACHT, mitVerbrauch("1", null))
            .vorgang(NACHT, mitVerbrauch("3", null))
            .vorgang(NACHT, mitVerbrauch(null, null))
            .vorgang(NACHT, vorgang(UMSETZUNG, 1, OPUS, null, true, null, List.of()))
            .ermittle();

    Zeile opus = zeile(vergleich, UMSETZUNG, OPUS);
    assertThat(opus.vorgaenge()).isEqualTo(4);
    assertThat(opus.erfolgreich()).isEqualTo(4);
    assertThat(opus.kostenMedian()).isEqualByComparingTo("2");
    assertThat(opus.kostenAus()).isEqualTo(2);
    assertThat(opus.modellzeitMedianMs()).isNull();
    assertThat(opus.modellzeitAus()).isZero();
  }

  @Test
  void dieMindestzahlGiltJeZeile() {
    Lage lage = new Lage().paket(1);
    IntStream.range(0, 3)
        .forEach(
            i -> {
              lage.vorgang(NACHT, vorgang(UMSETZUNG, 1, OPUS, "high", true, null, List.of()));
              lage.vorgang(NACHT, vorgang(UMSETZUNG, 1, OPUS, "low", true, null, List.of()));
            });
    IntStream.range(0, 5).forEach(i -> lage.vorgang(NACHT, umsetzung(1, FABLE)));

    Modellvergleich vergleich = lage.ermittle();

    assertThat(zeile(vergleich, UMSETZUNG, OPUS, "high").enoughData()).isFalse();
    assertThat(zeile(vergleich, UMSETZUNG, OPUS, "low").enoughData()).isFalse();
    assertThat(zeile(vergleich, UMSETZUNG, FABLE).enoughData()).isTrue();
    assertThat(ModellvergleichErmittlung.MINDESTZAHL).isEqualTo(5);
  }

  // --- Zeilen, Stufen und Kopf (E11, E12) ----------------------------------------------------

  @Test
  void dieZeilenOrdnenSichNachModellDannOhneDenktiefeDannNachDenktiefe() {
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(NACHT, vorgang(UMSETZUNG, 1, "b-modell", null, true, null, List.of()))
            .vorgang(NACHT, vorgang(UMSETZUNG, 1, "a-modell", "medium", true, null, List.of()))
            .vorgang(NACHT, vorgang(UMSETZUNG, 1, "a-modell", null, true, null, List.of()))
            .vorgang(NACHT, vorgang(UMSETZUNG, 1, "a-modell", "high", true, null, List.of()))
            .vorgang(NACHT, vorgang(UMSETZUNG, 1, "a-modell", "medium", true, null, List.of()))
            .ermittle();

    assertThat(stufe(vergleich, UMSETZUNG).zeilen())
        .extracting(Zeile::model, Zeile::effort, Zeile::vorgaenge)
        .containsExactly(
            tuple("a-modell", null, 1),
            tuple("a-modell", "high", 1),
            tuple("a-modell", "medium", 2),
            tuple("b-modell", null, 1));
  }

  @Test
  void alleFuenfStufenStehenInKettenreihenfolgeAuchLeer() {
    Modellvergleich vergleich = new Lage().paket(1).vorgang(NACHT, umsetzung(1, OPUS)).ermittle();

    assertThat(vergleich.stufen())
        .extracting(Stufe::stufe)
        .containsExactly(PLAN, REVIEW, PAKETE, ABDECKUNG, UMSETZUNG);
    assertThat(stufe(vergleich, PLAN).zeilen()).isEmpty();
    assertThat(stufe(vergleich, UMSETZUNG).zeilen()).hasSize(1);
  }

  @Test
  void ohneVorgangMitModellHatDerKopfKeinenDatenbeginn() {
    Modellvergleich vergleich = new Lage().paket(1).vorgang(NACHT, umsetzung(1, null)).ermittle();

    assertThat(vergleich.kopf()).isEqualTo(new Kopf(null, false, 0, 0));
    assertThat(vergleich.stufen()).allSatisfy(s -> assertThat(s.zeilen()).isEmpty());
  }

  @Test
  void derKopfZaehltVorgaengeOhneModellAbDemDatenbeginn() {
    Instant erstesFenster = tageZurueck(80);
    Modellvergleich vergleich =
        new Lage()
            .paket(1)
            .vorgang(tageZurueck(83), umsetzung(1, null))
            .vorgang(erstesFenster, umsetzung(1, OPUS))
            .vorgang(erstesFenster, umsetzung(1, null))
            .vorgang(NACHT, umsetzung(1, null))
            .vorgang(NACHT, umsetzung(1, FABLE))
            .ermittle();

    assertThat(vergleich.kopf()).isEqualTo(new Kopf(erstesFenster, true, 2, 2));
  }

  @Test
  void einDatenbeginnNachDemErstenFensterNenntNichtDieLetztenZwoelfWochen() {
    Modellvergleich vergleich =
        new Lage().paket(1).vorgang(tageZurueck(76), umsetzung(1, OPUS)).ermittle();

    assertThat(vergleich.kopf().letzte12Wochen()).isFalse();
    assertThat(vergleich.kopf().datenbeginn()).isEqualTo(tageZurueck(76));
  }
}
