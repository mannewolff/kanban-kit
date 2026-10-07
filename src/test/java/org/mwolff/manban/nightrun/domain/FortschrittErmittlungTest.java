package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Aktivitaet;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Karte;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Laufstand;
import org.mwolff.manban.nightrun.domain.FortschrittErmittlung.Zeitfenster;

/**
 * Der Fortschritt eines Laufs aus seinen Spuren am Board (Issue #1374, Plan #1372 E2–E6, E8, E9,
 * E12).
 *
 * <p>Karten-IDs sind hier zehnmal so groß wie die Kartennummern: Wer ID und Nummer verwechselt,
 * fällt auf.
 */
// PMD.TooManyMethods, PMD.GodClass, PMD.CyclomaticComplexity: methodenreiche Testsuite — je Fall
// der Ermittlung ein kleiner Test über dieselben Vorrichtungen; die Summen zählen die Tests, nicht
// verschachtelte Logik (höchste Einzelmethode 3). Ein Zerschneiden verteilte eine Regel auf
// Dateien, die sich dieselben Vorrichtungen teilen müssten. PMD.CouplingBetweenObjects: Die
// Kopplung zählt die Eingabe- und Ergebnistypen beider Ermittlungen (Laufseite und Kettenstand),
// die dieselben Vorrichtungen nutzen.
@SuppressWarnings({
  "PMD.TooManyMethods",
  "PMD.GodClass",
  "PMD.CyclomaticComplexity",
  "PMD.CouplingBetweenObjects"
})
class FortschrittErmittlungTest {

  private static final Instant START = Instant.parse("2026-10-03T13:01:00Z");
  private static final Instant ENDE = START.plus(Duration.ofHours(2));
  private static final Zeitfenster FENSTER = new Zeitfenster(START, ENDE);
  private static final String TOKEN = "nacht";
  private static final String AGENT = "claude-opus-5-5";

  /** Start des zweiten, parallelen Laufs B — eine halbe Stunde nach Lauf A. */
  private static final Instant START_B = um(30);

  private static final Zeitfenster FENSTER_B =
      new Zeitfenster(START_B, START_B.plus(Duration.ofHours(2)));

  private final List<Karte> karten = new ArrayList<>();
  private final List<Aktivitaet> aktivitaeten = new ArrayList<>();
  private final List<Laufstand> laufstaende = new ArrayList<>();
  private final List<Zeitfenster> fremdeFenster = new ArrayList<>();

  // --- Aufbau ----------------------------------------------------------------------------------

  private static Instant um(long minuten) {
    return START.plus(Duration.ofMinutes(minuten));
  }

  private static NightRun lauf(NightRunMode mode, @Nullable String tokenName, boolean complete) {
    return lauf(mode, START, tokenName, complete);
  }

  private static NightRun lauf(
      NightRunMode mode, Instant startedAt, @Nullable String tokenName, boolean complete) {
    return new NightRun(
        1L,
        7L,
        startedAt,
        mode,
        NightRunKind.NIGHT,
        0L,
        0,
        0,
        0,
        null,
        START,
        NightRunOrigin.TOKEN,
        tokenName,
        complete,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private Karte karte(
      int nummer,
      String titel,
      @Nullable String status,
      @Nullable Karte herkunft,
      boolean arbeitspaket,
      @Nullable String beschreibung,
      String... labels) {
    Karte k =
        new Karte(
            nummer * 10L,
            nummer,
            titel + " " + nummer,
            3L,
            status,
            Arrays.asList(labels),
            herkunft == null ? null : herkunft.id(),
            arbeitspaket,
            beschreibung);
    karten.add(k);
    return k;
  }

  private Karte anforderung(int nummer, String... labels) {
    return karte(nummer, "[Fachlich] Anforderung", "BACKLOG", null, false, null, labels);
  }

  private Karte plan(int nummer, @Nullable Karte anforderung, String... labels) {
    return planMitText(nummer, anforderung, "## Kontext\nPlan", labels);
  }

  private Karte planMitText(
      int nummer, @Nullable Karte anforderung, String beschreibung, String... labels) {
    return karte(nummer, "[Plan] Plan", "BACKLOG", anforderung, false, beschreibung, labels);
  }

  private Karte paket(int nummer, Karte plan, String status, String... labels) {
    return karte(nummer, "Paket", status, plan, true, "Paket", labels);
  }

  private void akt(Karte k, String typ, Instant zeit) {
    aktivitaeten.add(new Aktivitaet(k.id(), typ, zeit, "TOKEN", TOKEN, AGENT));
  }

  /** Eine Aktivität mit Laufkennung und Status danach (Issue #1429). */
  private void akt(
      Karte k, String typ, Instant zeit, @Nullable Instant laufStart, @Nullable String danach) {
    aktivitaeten.add(new Aktivitaet(k.id(), typ, zeit, "TOKEN", TOKEN, AGENT, laufStart, danach));
  }

  private void angelegt(Karte k, long minute) {
    akt(k, "CREATED", um(minute));
  }

  private void bewegt(Karte k, long minute) {
    akt(k, "MOVED", um(minute));
  }

  /** Ein Laufstand mit Laufkennung (Issue #1429). */
  private void standVon(Karte k, @Nullable Instant laufStart, String... zeilen) {
    laufstaende.add(
        new Laufstand(k.id(), "## Laufstand\n\n" + String.join("\n", zeilen), laufStart));
  }

  private void stand(Karte k, String... zeilen) {
    laufstaende.add(new Laufstand(k.id(), "## Laufstand\n\n" + String.join("\n", zeilen)));
  }

  private static String eintrag(String stufe, String was, Karte ziel, Instant zeit) {
    return stufe + " " + was + " für #" + ziel.number() + " um " + zeit;
  }

  private static String begonnen(String stufe, Karte ziel, long minute) {
    return "zuletzt begonnen: " + eintrag(stufe, "begonnen", ziel, um(minute));
  }

  private static String fertig(String stufe, Karte ziel, long minute) {
    return "zuletzt abgeschlossen: " + eintrag(stufe, "fertig", ziel, um(minute));
  }

  private NightRunProgress ermittle(NightRunMode mode) {
    return ermittle(lauf(mode, TOKEN, false));
  }

  private NightRunProgress ermittle(NightRun lauf) {
    return FortschrittErmittlung.ermittle(
        lauf, FENSTER, fremdeFenster, aktivitaeten, karten, laufstaende);
  }

  private ChainProgress eineKette() {
    NightRunProgress p = ermittle(NightRunMode.CHAIN);
    assertThat(p.ketten()).hasSize(1);
    return p.ketten().get(0);
  }

  private static Map<ProgressStage, StageState> stufen(ChainProgress k) {
    Map<ProgressStage, StageState> m = new LinkedHashMap<>();
    k.stufen().forEach(s -> m.put(s.stufe(), s.zustand()));
    return m;
  }

  private static CardRef ref(Karte k) {
    return new CardRef(k.number(), k.title(), k.boardId());
  }

  private static List<Integer> nummern(List<PackageProgress> pakete) {
    return pakete.stream().map(p -> p.karte().number()).toList();
  }

  private static List<Integer> refNummern(List<CardRef> refs) {
    return refs.stream().map(CardRef::number).toList();
  }

  // --- Plan --------------------------------------------------------------------------------------

  @Test
  void planAngelegtIstErreichtUndDiePruefungIstDieAktuelleStelle() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.zuordnung()).isEqualTo(ProgressAssignment.OK);
    assertThat(fortschritt.unbekannt()).isEmpty();
    assertThat(fortschritt.offeneFragen()).isEmpty();
    assertThat(fortschritt.pakete()).isEmpty();
    ChainProgress kette = fortschritt.ketten().get(0);
    assertThat(kette.anforderung()).isEqualTo(ref(f));
    assertThat(kette.plan()).isEqualTo(new CardRef(501, "[Plan] Plan 501", 3L));
    assertThat(kette.pakete()).isEmpty();
    assertThat(stufen(kette))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StageState.ERREICHT),
            Map.entry(ProgressStage.REVIEW, StageState.LAEUFT),
            Map.entry(ProgressStage.PAKETE, StageState.OFFEN),
            Map.entry(ProgressStage.ABDECKUNG, StageState.OFFEN));
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.REVIEW);
    assertThat(kette.endeErreicht()).isFalse();
  }

  @Test
  void vonMehrerenAngelegtenPlaenenGiltDerJuengste() {
    Karte f = anforderung(500);
    angelegt(plan(503, f), 20);
    angelegt(plan(501, f), 10);
    angelegt(plan(502, f), 15);

    assertThat(eineKette().plan()).isNotNull().extracting(CardRef::number).isEqualTo(503);
  }

  @Test
  void einAngelegterPlanOhneHerkunftBildetKeineKette() {
    angelegt(plan(501, null), 10);

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.ketten()).isEmpty();
    assertThat(fortschritt.unbekannt()).isEmpty();
  }

  @Test
  void einAngelegterPlanMitNichtGelieferterAnforderungIstUnbekannt() {
    Karte fremd = new Karte(9990L, 999, "fremd", 3L, null, List.of(), null, false, null);
    Karte p = plan(501, fremd);
    angelegt(p, 10);

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.ketten()).isEmpty();
    assertThat(fortschritt.unbekannt()).containsExactly(ref(p));
  }

  @Test
  void eineVomLaufNurBewegteKarteGiltNichtAlsAngelegterPlan() {
    Karte f = anforderung(500);
    bewegt(plan(501, f), 10);

    assertThat(ermittle(NightRunMode.CHAIN).ketten()).isEmpty();
  }

  @Test
  void eineAngelegteKarteOhnePlanPraefixIstKeinPlan() {
    Karte f = anforderung(500);
    angelegt(karte(501, "Plan ohne Klammer", "BACKLOG", f, false, null), 10);

    assertThat(ermittle(NightRunMode.CHAIN).ketten()).isEmpty();
  }

  @Test
  void dasPlanPraefixIstUnabhaengigVonGrossschreibungUndFuehrendenLeerzeichen() {
    Karte f = anforderung(500);
    angelegt(karte(501, "  [PLAN] gross", "BACKLOG", f, false, null), 10);

    assertThat(eineKette().plan()).isNotNull().extracting(CardRef::number).isEqualTo(501);
  }

  // --- Prüfung -----------------------------------------------------------------------------------

  @Test
  void planGeprueftMitWertInDerZeilePlanReview() {
    Karte f = anforderung(500);
    angelegt(planMitText(501, f, "## Kontext\n  Plan-Review: fable, gpt-astra (2026-10-04)\n"), 10);

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).containsEntry(ProgressStage.REVIEW, StageState.ERREICHT);
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.PAKETE);
  }

  @Test
  void planReviewOhneLeerzeichenNachDemDoppelpunktGilt() {
    Karte f = anforderung(500);
    angelegt(planMitText(501, f, "Plan-Review:fable"), 10);

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.REVIEW, StageState.ERREICHT);
  }

  @Test
  void leereZeilePlanReviewGiltNichtAlsGeprueft() {
    Karte f = anforderung(500);
    angelegt(planMitText(501, f, "## Kontext\nPlan-Review:   \r\n"), 10);

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.REVIEW, StageState.LAEUFT);
  }

  @Test
  void andereReviewZeileGiltNichtAlsPlanReview() {
    Karte f = anforderung(500);
    angelegt(planMitText(501, f, "Fachplan-Review: fable\nKein Plan-Review: x"), 10);

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.REVIEW, StageState.LAEUFT);
  }

  @Test
  void planOhneBeschreibungIstNichtGeprueft() {
    Karte f = anforderung(500);
    angelegt(karte(501, "[Plan] Plan", "BACKLOG", f, false, null), 10);

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.REVIEW, StageState.LAEUFT);
  }

  // --- Pakete ------------------------------------------------------------------------------------

  @Test
  void angelegtePaketeStehenInDerListeAuchOhnePaketeFertig() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    Karte a = paket(503, p, "BACKLOG");
    Karte b = paket(502, p, "BACKLOG");
    angelegt(a, 31);
    angelegt(b, 30);
    stand(f, begonnen("pakete", p, 29), fertig("review", p, 28));

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    ChainProgress kette = fortschritt.ketten().get(0);
    assertThat(nummern(kette.pakete())).containsExactly(502, 503);
    assertThat(kette.pakete())
        .extracting(PackageProgress::zustand)
        .containsOnly(PackageState.ANGELEGT);
    assertThat(stufen(kette))
        .containsEntry(ProgressStage.REVIEW, StageState.ERREICHT)
        .containsEntry(ProgressStage.PAKETE, StageState.LAEUFT);
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.PAKETE);
    assertThat(nummern(fortschritt.pakete())).containsExactly(502, 503);
  }

  @Test
  void paketeFertigFuerDenPlanErreichtDieStufeUndDieAktuelleStelleRueckt() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, begonnen("pakete", p, 29), fertig("pakete", p, 40));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).containsEntry(ProgressStage.PAKETE, StageState.ERREICHT);
    // Die begonnene Stufe ist erreicht — die aktuelle Stelle ist die erste offene.
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.ABDECKUNG);
    assertThat(stufen(kette)).containsEntry(ProgressStage.ABDECKUNG, StageState.LAEUFT);
  }

  @Test
  void abdeckungFertigFuerDenPlanErreichtAuchDiePakete() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, "zuletzt abgeschlossen: " + eintrag("abdeckung", "fertig", p, um(50)));

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.PAKETE, StageState.ERREICHT);
  }

  @Test
  void paketeFertigFuerEinenAnderenPlanZaehltNicht() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    Karte anderer = plan(400, f);
    angelegt(p, 10);
    stand(f, fertig("pakete", anderer, 40), fertig("abdeckung", anderer, 41));

    Map<ProgressStage, StageState> stufen = stufen(eineKette());

    assertThat(stufen).containsEntry(ProgressStage.PAKETE, StageState.OFFEN);
    assertThat(stufen).containsEntry(ProgressStage.ABDECKUNG, StageState.OFFEN);
  }

  @Test
  void nurVomLaufAngelegtePaketeDesPlansGehoerenZurKette() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    Karte anderer = plan(400, null);
    angelegt(p, 10);
    angelegt(paket(502, p, "BACKLOG"), 30);
    bewegt(paket(503, p, "READY"), 31);
    angelegt(paket(504, anderer, "BACKLOG"), 32);
    angelegt(karte(505, "Ohne Herkunft", "BACKLOG", null, true, null), 33);

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(nummern(fortschritt.ketten().get(0).pakete())).containsExactly(502);
    assertThat(nummern(fortschritt.pakete())).containsExactly(502, 503, 504, 505);
  }

  // --- Abdeckung und Variante A ------------------------------------------------------------------

  @Test
  void abdeckungFertigErreichtOhneDurchziehenDasEndeDesWegs() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, begonnen("abdeckung", p, 45), fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StageState.ERREICHT),
            Map.entry(ProgressStage.REVIEW, StageState.ERREICHT),
            Map.entry(ProgressStage.PAKETE, StageState.ERREICHT),
            Map.entry(ProgressStage.ABDECKUNG, StageState.ERREICHT));
    assertThat(kette.aktuelleStufe()).isNull();
    assertThat(kette.endeErreicht()).isTrue();
  }

  @Test
  void stufeneintragMitZerlegtemUmlautWirdErkannt() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, "zuletzt abgeschlossen: abdeckung fertig für #" + p.number() + " um " + um(50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).containsEntry(ProgressStage.ABDECKUNG, StageState.ERREICHT);
    assertThat(kette.endeErreicht()).isTrue();
  }

  @Test
  void durchziehenNurAmVomLaufAngelegtenPlanBleibtVarianteA() {
    Karte f = anforderung(500);
    Karte p = plan(501, f, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    angelegt(p, 10);
    stand(f, fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).doesNotContainKey(ProgressStage.UMSETZUNG);
    assertThat(kette.endeErreicht()).isTrue();
  }

  @Test
  void begonneneUmsetzungAusserhalbDesWegsAendertDasEndeNicht() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, begonnen("umsetzung", p, 55), fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(kette.aktuelleStufe()).isNull();
    assertThat(kette.endeErreicht()).isTrue();
  }

  @Test
  void begonneneUmsetzungOhneDurchziehenErreichtAlleStufenDavor() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, begonnen("umsetzung", p, 55));

    ChainProgress kette = eineKette();

    // Eine spätere Stufe hat begonnen — alle Stufen des Wegs gelten als erreicht.
    assertThat(kette.endeErreicht()).isTrue();
    assertThat(kette.aktuelleStufe()).isNull();
  }

  // --- Variante B --------------------------------------------------------------------------------

  @Test
  void durchziehenZeigtDieUmsetzungAlsAktuelleStelle() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    Karte p = plan(501, f);
    angelegt(p, 10);
    Karte a = paket(502, p, "IN_PROGRESS");
    angelegt(a, 30);
    bewegt(a, 60);
    stand(f, begonnen("umsetzung", p, 55), fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StageState.ERREICHT),
            Map.entry(ProgressStage.REVIEW, StageState.ERREICHT),
            Map.entry(ProgressStage.PAKETE, StageState.ERREICHT),
            Map.entry(ProgressStage.ABDECKUNG, StageState.ERREICHT),
            Map.entry(ProgressStage.UMSETZUNG, StageState.LAEUFT));
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(kette.endeErreicht()).isFalse();
    assertThat(kette.pakete())
        .containsExactly(new PackageProgress(ref(a), PackageState.IN_UMSETZUNG));
  }

  @Test
  void durchziehenIstAmEndeWennAllePaketeFertigSind() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    Karte p = plan(501, f);
    angelegt(p, 10);
    Karte a = paket(502, p, "IN_REVIEW");
    Karte b = paket(503, p, "DONE");
    angelegt(a, 30);
    angelegt(b, 30);
    bewegt(a, 60);
    bewegt(b, 70);
    stand(f, begonnen("umsetzung", p, 55), fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).containsEntry(ProgressStage.UMSETZUNG, StageState.ERREICHT);
    assertThat(kette.aktuelleStufe()).isNull();
    assertThat(kette.endeErreicht()).isTrue();
  }

  @Test
  void durchziehenMitEinemOffenenPaketIstNichtAmEnde() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    Karte p = plan(501, f);
    angelegt(p, 10);
    Karte a = paket(502, p, "IN_REVIEW");
    Karte b = paket(503, p, "READY");
    angelegt(a, 30);
    angelegt(b, 30);
    bewegt(a, 60);
    bewegt(b, 70);
    stand(f, begonnen("umsetzung", p, 55));

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.UMSETZUNG, StageState.LAEUFT);
  }

  @Test
  void durchziehenOhnePaketeErreichtDieUmsetzungUeberDieFertigZeile() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, fertig("umsetzung", p, 80));

    // Die fertig-Zeile der Umsetzung erreicht die Stufe auch ohne Pakete in der Liste.
    assertThat(stufen(eineKette())).containsEntry(ProgressStage.UMSETZUNG, StageState.ERREICHT);
  }

  @Test
  void durchziehenOhnePaketeUndOhneZeileIstDieUmsetzungOffen() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).containsEntry(ProgressStage.UMSETZUNG, StageState.LAEUFT);
    assertThat(kette.endeErreicht()).isFalse();
  }

  // --- Ziel aus dem Laufstand (Issue #1529) ------------------------------------------------------

  @Test
  void zielUmsetzungOhneDurchziehenZeigtDieUmsetzung() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, "Ziel: umsetzung", begonnen("umsetzung", p, 55), fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StageState.ERREICHT),
            Map.entry(ProgressStage.REVIEW, StageState.ERREICHT),
            Map.entry(ProgressStage.PAKETE, StageState.ERREICHT),
            Map.entry(ProgressStage.ABDECKUNG, StageState.ERREICHT),
            Map.entry(ProgressStage.UMSETZUNG, StageState.LAEUFT));
    assertThat(kette.endeErreicht()).isFalse();
  }

  @Test
  void zielPushVorbereitetZeigtDieUmsetzung() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, "Ziel: push-vorbereitet", fertig("abdeckung", p, 50));

    assertThat(stufen(eineKette())).containsKey(ProgressStage.UMSETZUNG);
  }

  @Test
  void zielUmsetzungImAusgewiesenenLaufGiltAuchOhneKennungImLaufstand() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    akt(p, "CREATED", um(10), START, null);
    standVon(f, null, "Ziel: umsetzung", begonnen("umsetzung", p, 55));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).containsKey(ProgressStage.UMSETZUNG);
    assertThat(kette.endeErreicht()).isFalse();
  }

  @Test
  void zielPaketeOhneDurchziehenEndetBeiDerAbdeckung() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, "Ziel: pakete", fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).doesNotContainKey(ProgressStage.UMSETZUNG);
    assertThat(kette.endeErreicht()).isTrue();
  }

  @Test
  void ohneZielzeileGiltWeiterDurchziehen() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, "Prüfer: 1", fertig("abdeckung", p, 50));

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.UMSETZUNG, StageState.LAEUFT);
  }

  // --- Paketzustände -----------------------------------------------------------------------------

  private PackageState zustandNachBewegung(@Nullable String status, String typ) {
    karten.clear();
    aktivitaeten.clear();
    Karte p = plan(501, null);
    Karte a = paket(502, p, "BACKLOG");
    karten.set(1, new Karte(a.id(), 502, a.title(), 3L, status, List.of(), p.id(), true, null));
    akt(a, typ, um(40));
    return ermittle(NightRunMode.IMPLEMENTATION).pakete().get(0).zustand();
  }

  @Test
  void paketGezogenNachReady() {
    assertThat(zustandNachBewegung("READY", "MOVED")).isEqualTo(PackageState.GEZOGEN);
  }

  @Test
  void paketInUmsetzung() {
    assertThat(zustandNachBewegung("IN_PROGRESS", "MOVED")).isEqualTo(PackageState.IN_UMSETZUNG);
  }

  @Test
  void paketFertigInReviewUndDone() {
    assertThat(zustandNachBewegung("IN_REVIEW", "MOVED")).isEqualTo(PackageState.FERTIG);
    assertThat(zustandNachBewegung("DONE", "STATUS_CHANGED")).isEqualTo(PackageState.FERTIG);
  }

  @Test
  void paketZurueckgestellt() {
    assertThat(zustandNachBewegung("BACKLOG", "STATUS_CHANGED"))
        .isEqualTo(PackageState.ZURUECKGESTELLT);
  }

  @Test
  void paketOhneStatusOderMitFremdemStatusGiltAlsAngelegt() {
    assertThat(zustandNachBewegung(null, "MOVED")).isEqualTo(PackageState.ANGELEGT);
    assertThat(zustandNachBewegung("ARCHIVIERT", "MOVED")).isEqualTo(PackageState.ANGELEGT);
  }

  @Test
  void paketNurAngelegtOderBearbeitetGiltAlsAngelegt() {
    assertThat(zustandNachBewegung("READY", "CREATED")).isEqualTo(PackageState.ANGELEGT);
    assertThat(zustandNachBewegung("IN_REVIEW", "UPDATED")).isEqualTo(PackageState.ANGELEGT);
  }

  // --- Modi --------------------------------------------------------------------------------------

  @Test
  void umsetzungsnachtZeigtNurPaketeOhneKette() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    Karte a = paket(503, p, "IN_REVIEW");
    Karte b = paket(502, p, "IN_PROGRESS");
    bewegt(a, 10);
    bewegt(b, 50);
    bewegt(p, 5);
    stand(f, begonnen("plan", f, 1));

    NightRunProgress fortschritt = ermittle(NightRunMode.IMPLEMENTATION);

    assertThat(fortschritt.zuordnung()).isEqualTo(ProgressAssignment.OK);
    assertThat(fortschritt.ketten()).isEmpty();
    assertThat(fortschritt.pakete())
        .containsExactly(
            new PackageProgress(ref(b), PackageState.IN_UMSETZUNG),
            new PackageProgress(ref(a), PackageState.FERTIG));
  }

  @Test
  void pruefLaufUndInteraktiveSitzungBleibenLeer() {
    Karte f = anforderung(500);
    angelegt(plan(501, f), 10);
    angelegt(paket(502, plan(400, null), "READY", FortschrittErmittlung.LABEL_KLAEREN), 10);

    for (NightRunMode mode : List.of(NightRunMode.REVIEW, NightRunMode.INTERACTIVE)) {
      NightRunProgress fortschritt = ermittle(mode);

      assertThat(fortschritt).isEqualTo(NightRunProgress.leer());
      assertThat(fortschritt.zuordnung()).isEqualTo(ProgressAssignment.OK);
      assertThat(fortschritt.ketten()).isEmpty();
      assertThat(fortschritt.pakete()).isEmpty();
      assertThat(fortschritt.unbekannt()).isEmpty();
      assertThat(fortschritt.offeneFragen()).isEmpty();
    }
  }

  // --- Zuordnung (E2) ----------------------------------------------------------------------------

  @Test
  void fremdeKartenZaehlenNicht() {
    Karte p = plan(400, null);
    Karte mensch = paket(501, p, "IN_REVIEW");
    Karte ohneAgent = paket(502, p, "IN_REVIEW");
    Karte anderesToken = paket(503, p, "IN_REVIEW");
    Karte vorher = paket(504, p, "DONE");
    Karte nachher = paket(505, p, "DONE");
    Karte sitzung = paket(506, p, "DONE");
    aktivitaeten.add(new Aktivitaet(mensch.id(), "MOVED", um(10), "SESSION", null, null));
    aktivitaeten.add(new Aktivitaet(sitzung.id(), "MOVED", um(10), "SESSION", TOKEN, AGENT));
    aktivitaeten.add(new Aktivitaet(ohneAgent.id(), "MOVED", um(10), "TOKEN", TOKEN, null));
    aktivitaeten.add(new Aktivitaet(anderesToken.id(), "MOVED", um(10), "TOKEN", "tag", AGENT));
    akt(vorher, "MOVED", START.minusMillis(1));
    akt(nachher, "MOVED", ENDE.plusMillis(1));

    NightRunProgress fortschritt = ermittle(NightRunMode.IMPLEMENTATION);

    assertThat(fortschritt.pakete()).isEmpty();
    assertThat(fortschritt.unbekannt()).isEmpty();
  }

  @Test
  void dieFenstergrenzenGehoerenZumFenster() {
    Karte p = plan(400, null);
    Karte amAnfang = paket(501, p, "IN_REVIEW");
    Karte amEnde = paket(502, p, "IN_REVIEW");
    akt(amAnfang, "MOVED", START);
    akt(amEnde, "MOVED", ENDE);

    assertThat(nummern(ermittle(NightRunMode.IMPLEMENTATION).pakete())).containsExactly(501, 502);
  }

  @Test
  void aktivitaetEinerNichtGeliefertenKarteWirdUebergangen() {
    aktivitaeten.add(new Aktivitaet(4242L, "MOVED", um(10), "TOKEN", TOKEN, AGENT));

    NightRunProgress fortschritt = ermittle(NightRunMode.IMPLEMENTATION);

    assertThat(fortschritt.pakete()).isEmpty();
    assertThat(fortschritt.unbekannt()).isEmpty();
  }

  @Test
  void umsetzungsnachtZaehltNurArbeitspakete() {
    Karte f = anforderung(500);
    bewegt(f, 10);

    assertThat(ermittle(NightRunMode.IMPLEMENTATION).pakete()).isEmpty();
  }

  // --- Unbekannt (E3) ----------------------------------------------------------------------------

  @Test
  void ueberlappenderLaufMachtKartenImUeberlappungsbereichUnbekannt() {
    fremdeFenster.add(new Zeitfenster(um(60), um(180)));
    Karte p = plan(400, null);
    Karte davor = paket(501, p, "IN_REVIEW");
    Karte amRand = paket(502, p, "IN_REVIEW");
    Karte drin = paket(503, p, "IN_REVIEW");
    bewegt(davor, 59);
    bewegt(amRand, 60);
    bewegt(drin, 90);
    aktivitaeten.add(new Aktivitaet(4242L, "MOVED", um(90), "TOKEN", TOKEN, AGENT));

    NightRunProgress fortschritt = ermittle(NightRunMode.IMPLEMENTATION);

    assertThat(nummern(fortschritt.pakete())).containsExactly(501);
    assertThat(fortschritt.unbekannt()).containsExactly(ref(amRand), ref(drin));
  }

  @Test
  void fremdesFensterVorDerAktivitaetMachtNichtsUnbekannt() {
    fremdeFenster.add(new Zeitfenster(START.minus(Duration.ofHours(3)), um(5)));
    Karte p = plan(400, null);
    Karte a = paket(501, p, "IN_REVIEW");
    bewegt(a, 6);

    NightRunProgress fortschritt = ermittle(NightRunMode.IMPLEMENTATION);

    assertThat(nummern(fortschritt.pakete())).containsExactly(501);
    assertThat(fortschritt.unbekannt()).isEmpty();
  }

  @Test
  void einImUeberlappungsbereichAngelegterPlanBildetKeineKette() {
    fremdeFenster.add(new Zeitfenster(um(0), um(30)));
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.ketten()).isEmpty();
    assertThat(fortschritt.unbekannt()).containsExactly(ref(p));
  }

  @Test
  void ohneTokenNamenIstDerGanzeFortschrittUnbekannt() {
    Karte f = anforderung(500);
    angelegt(plan(501, f), 10);

    NightRunProgress fortschritt = ermittle(lauf(NightRunMode.CHAIN, null, false));

    assertThat(fortschritt).isEqualTo(NightRunProgress.zuordnungUnbekannt());
    assertThat(fortschritt.zuordnung()).isEqualTo(ProgressAssignment.UNBEKANNT);
    assertThat(fortschritt.ketten()).isEmpty();
    assertThat(fortschritt.pakete()).isEmpty();
    assertThat(fortschritt.unbekannt()).isEmpty();
    assertThat(fortschritt.offeneFragen()).isEmpty();
  }

  @Test
  void ketteAnEinemPlanOhneZuordenbareAnforderungIstUnbekannt() {
    Karte p = plan(501, null);
    stand(p, begonnen("review", p, 10));

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.ketten()).isEmpty();
    assertThat(fortschritt.unbekannt()).containsExactly(ref(p));
  }

  // --- Tragende Karte (E5) -----------------------------------------------------------------------

  @Test
  void arbeitspaketMitEigenemLaufstandWirdKeineKette() {
    Karte p = plan(400, null);
    Karte a = paket(501, p, "IN_PROGRESS", "lauf:laeuft");
    stand(a, begonnen("plan", a, 10), fertig("pakete", p, 12));

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.ketten()).isEmpty();
    assertThat(fortschritt.unbekannt()).isEmpty();
  }

  @Test
  void laufstandMitStufenzeileImFensterErkenntDieKette() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    stand(f, begonnen("review", p, 10));

    ChainProgress kette = eineKette();

    assertThat(kette.anforderung()).isEqualTo(ref(f));
    assertThat(kette.plan()).isEqualTo(ref(p));
    assertThat(stufen(kette))
        .containsEntry(ProgressStage.PLAN, StageState.ERREICHT)
        .containsEntry(ProgressStage.REVIEW, StageState.LAEUFT);
  }

  @Test
  void jedeStufenzeileMachtDieKarteZurTragendenKarte() {
    for (String stufe : List.of("plan", "review", "pakete", "abdeckung")) {
      for (String was : List.of("begonnen", "fertig")) {
        karten.clear();
        laufstaende.clear();
        Karte f = anforderung(500);
        stand(f, eintrag(stufe, was, f, um(10)));

        assertThat(ermittle(NightRunMode.CHAIN).ketten()).as(stufe + " " + was).hasSize(1);
      }
    }
  }

  @Test
  void eineUmsetzungszeileAlleinMachtKeineTragendeKarte() {
    Karte f = anforderung(500);
    stand(f, begonnen("umsetzung", f, 10));

    assertThat(ermittle(NightRunMode.CHAIN).ketten()).isEmpty();
  }

  @Test
  void laufstandEinerNichtGeliefertenKarteWirdUebergangen() {
    laufstaende.add(new Laufstand(4242L, "zuletzt begonnen: plan begonnen für #1 um " + um(5)));

    assertThat(ermittle(NightRunMode.CHAIN).ketten()).isEmpty();
  }

  @Test
  void ketteAnEinemVorhandenenPlanFindetDieAnforderungUeberDieHerkunft() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    stand(p, begonnen("pakete", p, 10));

    ChainProgress kette = eineKette();

    assertThat(kette.anforderung()).isEqualTo(ref(f));
    assertThat(kette.plan()).isEqualTo(ref(p));
    assertThat(stufen(kette)).containsEntry(ProgressStage.PLAN, StageState.ERREICHT);
  }

  @Test
  void ketteAnEinemVorhandenenPlanMitNichtGelieferterAnforderungIstUnbekannt() {
    Karte fremd = new Karte(9990L, 999, "fremd", 3L, null, List.of(), null, false, null);
    Karte p = plan(501, fremd);
    stand(p, begonnen("pakete", p, 10));

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.ketten()).isEmpty();
    assertThat(fortschritt.unbekannt()).containsExactly(ref(p));
  }

  @Test
  void planFertigOhneBekanntenPlanErreichtDiePlanstufe() {
    Karte f = anforderung(500);
    Karte anderer = anforderung(400);
    stand(f, fertig("plan", f, 10), fertig("review", anderer, 11));

    ChainProgress kette = eineKette();

    assertThat(kette.plan()).isNull();
    assertThat(stufen(kette))
        .containsEntry(ProgressStage.PLAN, StageState.ERREICHT)
        .containsEntry(ProgressStage.REVIEW, StageState.LAEUFT);
  }

  @Test
  void eineKetteOhnePlanHatKeinePaketeAuchWennDerLaufKartenHat() {
    Karte f = anforderung(500);
    angelegt(paket(502, plan(400, null), "BACKLOG"), 10);
    stand(f, begonnen("plan", f, 5));

    ChainProgress kette = eineKette();

    assertThat(kette.plan()).isNull();
    assertThat(kette.pakete()).isEmpty();
  }

  @Test
  void ketteAnEinemVorhandenenPlanMitPlanzeileHatDiePlanstufeErreicht() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    stand(p, begonnen("plan", f, 5));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette))
        .containsEntry(ProgressStage.PLAN, StageState.ERREICHT)
        .containsEntry(ProgressStage.REVIEW, StageState.LAEUFT);
  }

  @Test
  void planFertigFuerEineAndereAnforderungErreichtDiePlanstufeNicht() {
    Karte f = anforderung(500);
    Karte anderer = anforderung(400);
    stand(f, fertig("plan", anderer, 10));

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.PLAN, StageState.LAEUFT);
  }

  @Test
  void derPlanAusDemLaufstandMussGeliefertSein() {
    Karte f = anforderung(500);
    Karte nichtGeliefert =
        new Karte(9990L, 999, "[Plan] fremd", 3L, null, List.of(), f.id(), false, null);
    stand(f, begonnen("pakete", nichtGeliefert, 10));

    assertThat(eineKette().plan()).isNull();
  }

  @Test
  void derVomLaufAngelegtePlanGehtDemPlanAusDemLaufstandVor() {
    Karte f = anforderung(500);
    Karte alt = plan(400, f);
    Karte neu = plan(501, f);
    angelegt(neu, 10);
    stand(f, begonnen("review", alt, 5));

    assertThat(eineKette().plan()).isEqualTo(ref(neu));
  }

  @Test
  void einePlanzeileNenntKeinenPlan() {
    Karte f = anforderung(500);
    plan(501, f);
    stand(f, begonnen("plan", f, 5));

    ChainProgress kette = eineKette();

    assertThat(kette.plan()).isNull();
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.PLAN);
  }

  @Test
  void mehrereLaufstaendeDerselbenKarteWerdenZusammengelesen() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    stand(f, begonnen("review", p, 10));
    stand(f, fertig("pakete", p, 20));

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.PAKETE, StageState.ERREICHT);
  }

  // --- Offene Fragen (E9) ------------------------------------------------------------------------

  @Test
  void klaerenBleibtNachAbgeschlossenemLaufStehen() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_WARTET);
    Karte p = plan(501, f);
    angelegt(p, 10);
    Karte a = paket(503, p, "BACKLOG", FortschrittErmittlung.LABEL_KLAEREN);
    angelegt(a, 30);
    bewegt(a, 40);
    paket(504, p, "BACKLOG", FortschrittErmittlung.LABEL_KLAEREN);

    NightRunProgress fortschritt = ermittle(lauf(NightRunMode.CHAIN, TOKEN, true));

    assertThat(fortschritt.offeneFragen()).containsExactly(ref(f), ref(a));
  }

  @Test
  void klaerenAnDerTragendenKarteIstEineOffeneFrage() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_KLAEREN);
    stand(f, begonnen("plan", f, 10));

    assertThat(ermittle(NightRunMode.CHAIN).offeneFragen()).containsExactly(ref(f));
  }

  @Test
  void wartetAnEinerKarteDesLaufsDieNichtTraegtIstKeineFrage() {
    Karte p = plan(400, null);
    Karte a = paket(501, p, "BACKLOG", FortschrittErmittlung.LABEL_WARTET);
    bewegt(a, 10);

    assertThat(ermittle(NightRunMode.IMPLEMENTATION).offeneFragen()).isEmpty();
  }

  @Test
  void klaerenInDerUmsetzungsnacht() {
    Karte p = plan(400, null);
    Karte a = paket(501, p, "BACKLOG", FortschrittErmittlung.LABEL_KLAEREN);
    bewegt(a, 10);

    assertThat(ermittle(NightRunMode.IMPLEMENTATION).offeneFragen()).containsExactly(ref(a));
  }

  @Test
  void dieLabelnamenSindDieVorgabenDesKits() {
    assertThat(FortschrittErmittlung.LABEL_WARTET).isEqualTo("lauf:wartet");
    assertThat(FortschrittErmittlung.LABEL_DURCHZIEHEN).isEqualTo("kit:durchziehen");
    assertThat(FortschrittErmittlung.LABEL_KLAEREN).isEqualTo("kit:klaeren");
  }

  // --- Stufen und Laufstand-Zeilen (E4) ----------------------------------------------------------

  @Test
  void eineStufeGiltAlsErreichtWennEineSpaetereBegonnenHat() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    stand(f, begonnen("abdeckung", p, 45));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StageState.ERREICHT),
            Map.entry(ProgressStage.REVIEW, StageState.ERREICHT),
            Map.entry(ProgressStage.PAKETE, StageState.ERREICHT),
            Map.entry(ProgressStage.ABDECKUNG, StageState.LAEUFT));
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.ABDECKUNG);
  }

  @Test
  void dieGleicheStufeBegonnenErreichtSichNichtSelbst() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    stand(f, begonnen("review", p, 45));

    assertThat(stufen(eineKette())).containsEntry(ProgressStage.REVIEW, StageState.LAEUFT);
  }

  @Test
  void einBegonnenEintragOhneZuletztZaehltEbenso() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 5);
    stand(f, eintrag("pakete", "begonnen", p, um(45)));

    ChainProgress kette = eineKette();

    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.PAKETE);
    assertThat(stufen(kette)).containsEntry(ProgressStage.REVIEW, StageState.ERREICHT);
  }

  @Test
  void zeileMitZeitstempelAusserhalbDesFenstersWirdIgnoriert() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 5);
    stand(
        f,
        "zuletzt abgeschlossen: " + eintrag("abdeckung", "fertig", p, START.minusMillis(1)),
        "zuletzt begonnen: " + eintrag("pakete", "begonnen", p, ENDE.plusMillis(1)));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette))
        .containsEntry(ProgressStage.PAKETE, StageState.OFFEN)
        .containsEntry(ProgressStage.ABDECKUNG, StageState.OFFEN);
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.REVIEW);
  }

  @Test
  void nurZeilenAusserhalbDesFenstersMachenKeineTragendeKarte() {
    Karte f = anforderung(500);
    stand(f, "zuletzt begonnen: " + eintrag("plan", "begonnen", f, START.minusSeconds(60)));

    assertThat(ermittle(NightRunMode.CHAIN).ketten()).isEmpty();
  }

  @Test
  void zeilenAnDenFenstergrenzenZaehlen() {
    Karte f = anforderung(500);
    Karte g = anforderung(600);
    stand(f, "zuletzt begonnen: " + eintrag("plan", "begonnen", f, START));
    stand(g, "zuletzt begonnen: " + eintrag("plan", "begonnen", g, ENDE));

    assertThat(ermittle(NightRunMode.CHAIN).ketten()).hasSize(2);
  }

  @Test
  void unbekannteZeilenWerdenIgnoriert() {
    Karte f = anforderung(500);
    stand(
        f,
        "Halt: Frage wartet auf den Menschen",
        "zuletzt begonnen: entwurf begonnen für #500 um " + um(10),
        "zuletzt begonnen: plan begonnen für #500 um gestern",
        "zuletzt begonnen: plan begonnen für #500",
        "zuletzt begonnen: plan gestartet für #500 um " + um(10),
        "Protokoll: .claude/protokolle/1.log");

    assertThat(ermittle(NightRunMode.CHAIN).ketten()).isEmpty();
  }

  @Test
  void eineStufeMussAlsWortBeginnen() {
    Karte f = anforderung(500);
    stand(f, "zuletzt begonnen: vorplan begonnen für #500 um " + um(10));

    assertThat(ermittle(NightRunMode.CHAIN).ketten()).isEmpty();
  }

  @Test
  void eineZeileMitWindowsZeilenendeWirdGelesen() {
    Karte f = anforderung(500);
    laufstaende.add(
        new Laufstand(f.id(), "## Laufstand\r\n\r\n" + begonnen("plan", f, 10) + "\r\n"));

    assertThat(eineKette().aktuelleStufe()).isEqualTo(ProgressStage.PLAN);
  }

  // --- Mehrere Ketten ----------------------------------------------------------------------------

  @Test
  void mehrereKettenInEinemLauf() {
    Karte f2 = anforderung(600);
    Karte p2 = plan(601, f2);
    stand(f2, begonnen("pakete", p2, 70));
    Karte f1 = anforderung(500);
    Karte p1 = plan(501, f1);
    angelegt(p1, 10);
    angelegt(paket(502, p1, "BACKLOG"), 20);
    stand(f1, begonnen("pakete", p1, 15));

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.ketten())
        .extracting(ChainProgress::anforderung)
        .containsExactly(ref(f1), ref(f2));
    assertThat(fortschritt.ketten())
        .extracting(ChainProgress::plan)
        .containsExactly(ref(p1), ref(p2));
    assertThat(refNummern(fortschritt.ketten().stream().map(ChainProgress::anforderung).toList()))
        .containsExactly(500, 600);
  }

  @Test
  void einePlanKetteUndIhreAnforderungErgebenEineKette() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    angelegt(p, 10);
    stand(f, begonnen("review", p, 12));
    stand(p, begonnen("review", p, 12));

    // Zwei tragende Kandidaten — die Anforderung und der Plan — beschreiben dieselbe Kette.
    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.ketten()).hasSize(1);
  }

  // --- Laufkennung (Issue #1429, Plan #1423 A1, A6–A10) ------------------------------------------

  private NightRunProgress ermittleA(NightRunMode mode) {
    fremdeFenster.clear();
    fremdeFenster.add(FENSTER_B);
    return FortschrittErmittlung.ermittle(
        lauf(mode, START, TOKEN, false), FENSTER, fremdeFenster, aktivitaeten, karten, laufstaende);
  }

  private NightRunProgress ermittleB(NightRunMode mode) {
    fremdeFenster.clear();
    fremdeFenster.add(FENSTER);
    return FortschrittErmittlung.ermittle(
        lauf(mode, START_B, TOKEN, false),
        FENSTER_B,
        fremdeFenster,
        aktivitaeten,
        karten,
        laufstaende);
  }

  /** Fall 1: A legt an, B setzt um — jeder zeigt nur seine Karten, nichts ist unbekannt. */
  @Test
  void zweiAusgewieseneParalleleLaeufeZeigenNurIhreKarten() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);
    Karte a = paket(502, p, "IN_PROGRESS");
    Karte b = paket(503, p, "BACKLOG");
    Karte fremd = paket(504, plan(400, null), "IN_REVIEW");
    akt(p, "CREATED", um(40), START, null);
    akt(a, "CREATED", um(45), START, null);
    akt(b, "CREATED", um(46), START, null);
    akt(fremd, "MOVED", um(50), START_B, "IN_REVIEW");
    akt(a, "MOVED", um(60), START_B, "IN_PROGRESS");
    standVon(f, START, begonnen("pakete", p, 44));

    NightRunProgress beiA = ermittleA(NightRunMode.CHAIN);
    NightRunProgress beiB = ermittleB(NightRunMode.IMPLEMENTATION);

    assertThat(beiA.unbekannt()).isEmpty();
    assertThat(beiA.unbekanntOhneAusweis()).isFalse();
    assertThat(beiA.ketten()).singleElement().extracting(ChainProgress::plan).isEqualTo(ref(p));
    assertThat(beiA.pakete())
        .containsExactly(
            new PackageProgress(ref(a), PackageState.ANGELEGT),
            new PackageProgress(ref(b), PackageState.ANGELEGT));
    assertThat(beiB.unbekannt()).isEmpty();
    assertThat(beiB.unbekanntOhneAusweis()).isFalse();
    assertThat(beiB.pakete())
        .containsExactly(
            new PackageProgress(ref(a), PackageState.IN_UMSETZUNG),
            new PackageProgress(ref(fremd), PackageState.FERTIG));
  }

  /** Fall 2: A zieht nach Ready, B schließt ab — jeder sieht den Zustand seiner Bewegung. */
  @Test
  void jederLaufZeigtDenZustandSeinerLetztenBewegung() {
    Karte a = paket(502, plan(400, null), "IN_REVIEW");
    akt(a, "MOVED", um(40), START_B, "IN_PROGRESS");
    akt(a, "MOVED", um(10), START, "READY");
    akt(a, "MOVED", um(70), START_B, "IN_REVIEW");
    akt(a, "STATUS_CHANGED", um(60), START_B, "BACKLOG");

    assertThat(ermittleA(NightRunMode.IMPLEMENTATION).pakete())
        .containsExactly(new PackageProgress(ref(a), PackageState.GEZOGEN));
    assertThat(ermittleB(NightRunMode.IMPLEMENTATION).pakete())
        .containsExactly(new PackageProgress(ref(a), PackageState.FERTIG));
  }

  /**
   * Fall 3: A weist sich aus, B nicht — nur B zeigt seine Karten unter „unbekannt" samt Flag, A
   * zeigt keine Karte von B.
   */
  @Test
  void mischlageZeigtUnbekanntNurBeimNichtAusgewiesenenLauf() {
    Karte p = plan(400, null);
    Karte vonA = paket(501, p, "IN_REVIEW");
    Karte vonB = paket(502, p, "IN_REVIEW");
    akt(vonA, "MOVED", um(40), START, "IN_REVIEW");
    akt(vonB, "MOVED", um(50), null, "IN_REVIEW");

    NightRunProgress beiA = ermittleA(NightRunMode.IMPLEMENTATION);
    NightRunProgress beiB = ermittleB(NightRunMode.IMPLEMENTATION);

    assertThat(nummern(beiA.pakete())).containsExactly(501);
    assertThat(beiA.unbekannt()).isEmpty();
    assertThat(beiA.unbekanntOhneAusweis()).isFalse();
    assertThat(beiB.pakete()).isEmpty();
    assertThat(beiB.unbekannt()).containsExactly(ref(vonB));
    assertThat(beiB.unbekanntOhneAusweis()).isTrue();
  }

  /**
   * Fall 4: Eine Kette ohne zuordenbare Anforderung ist unbekannt, aber nicht wegen des Ausweises.
   */
  @Test
  void unbekannteKetteSetztDasFlagNicht() {
    Karte p = plan(501, null);
    stand(p, begonnen("review", p, 10));

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.unbekannt()).containsExactly(ref(p));
    assertThat(fortschritt.unbekanntOhneAusweis()).isFalse();
  }

  /**
   * Fall 5: Der Laufstand eines fremden Laufs erscheint nicht in der Stufenleiste — auch einer ohne
   * Kennung nicht, sobald der Lauf ausgewiesen ist.
   */
  @Test
  void laufstandEinesFremdenLaufsErscheintNicht() {
    Karte f1 = anforderung(500);
    Karte p1 = plan(501, f1);
    akt(p1, "CREATED", um(40), START, null);
    Karte f2 = anforderung(600);
    standVon(f2, START_B, begonnen("plan", f2, 45));
    Karte f3 = anforderung(700);
    standVon(f3, null, begonnen("plan", f3, 46));

    NightRunProgress beiA = ermittleA(NightRunMode.CHAIN);

    assertThat(beiA.ketten()).extracting(ChainProgress::anforderung).containsExactly(ref(f1));
    assertThat(beiA.unbekannt()).isEmpty();
  }

  /** Ein nicht ausgewiesener Lauf sieht keinen Laufstand mit Kennung. */
  @Test
  void nichtAusgewiesenerLaufSiehtKeinenLaufstandMitKennung() {
    Karte f = anforderung(600);
    standVon(f, START_B, begonnen("plan", f, 45));

    assertThat(ermittleA(NightRunMode.CHAIN).ketten()).isEmpty();
  }

  /** Fall 6: Eine Spur mit eigener Kennung nach dem Fensterende zählt nicht. */
  @Test
  void eigeneSpurNachDemFensterendeZaehltNicht() {
    Karte p = plan(400, null);
    Karte drin = paket(501, p, "IN_REVIEW");
    Karte danach = paket(502, p, "IN_REVIEW");
    akt(drin, "MOVED", um(10), START, "IN_REVIEW");
    akt(danach, "MOVED", ENDE.plusMillis(1), START, "IN_REVIEW");

    assertThat(nummern(ermittleA(NightRunMode.IMPLEMENTATION).pakete())).containsExactly(501);
  }

  /** Ein Alt-Eintrag ohne {@code statusAfter} fällt auf den heutigen Kartenstatus zurück (A8). */
  @Test
  void altEintragOhneStatusAfterFaelltAufDenKartenstatusZurueck() {
    Karte a = paket(502, plan(400, null), "IN_REVIEW");
    akt(a, "STATUS_CHANGED", um(10), START, null);

    assertThat(ermittleA(NightRunMode.IMPLEMENTATION).pakete())
        .containsExactly(new PackageProgress(ref(a), PackageState.FERTIG));
  }

  /** Ohne Status danach und ohne Kartenstatus gilt das Paket als angelegt. */
  @Test
  void ohneStatusAfterUndOhneKartenstatusGiltAngelegt() {
    Karte a = paket(502, plan(400, null), null);
    akt(a, "MOVED", um(10), START, null);

    assertThat(ermittleA(NightRunMode.IMPLEMENTATION).pakete())
        .containsExactly(new PackageProgress(ref(a), PackageState.ANGELEGT));
  }

  /** Lauf und Spuren tragen Mikrosekunden — beide Seiten werden auf Millisekunden gekürzt (A10). */
  @Test
  void kennungMitMikrosekundenPasstZumLauf() {
    Karte f = anforderung(500);
    Karte a = paket(502, plan(400, null), "IN_REVIEW");
    akt(a, "MOVED", um(10), START.plusNanos(789_000), "READY");
    standVon(f, START.plusNanos(456_000), begonnen("plan", f, 12));

    NightRunProgress fortschritt =
        FortschrittErmittlung.ermittle(
            lauf(NightRunMode.CHAIN, START.plusNanos(123_456), TOKEN, false),
            FENSTER,
            fremdeFenster,
            aktivitaeten,
            karten,
            laufstaende);

    assertThat(fortschritt.pakete())
        .containsExactly(new PackageProgress(ref(a), PackageState.GEZOGEN));
    assertThat(fortschritt.ketten()).extracting(ChainProgress::anforderung).containsExactly(ref(f));
  }

  /**
   * Ein Laufstand mit eigener Kennung weist den Lauf allein aus: Aktivitäten ohne Kennung gehören
   * ihm dann nicht mehr.
   */
  @Test
  void einLaufstandMitEigenerKennungWeistDenLaufAus() {
    Karte f = anforderung(500);
    Karte a = paket(502, plan(400, null), "IN_REVIEW");
    bewegt(a, 10);
    standVon(f, START, begonnen("plan", f, 12));

    NightRunProgress fortschritt = ermittle(NightRunMode.CHAIN);

    assertThat(fortschritt.pakete()).isEmpty();
    assertThat(fortschritt.ketten()).extracting(ChainProgress::anforderung).containsExactly(ref(f));
  }

  /**
   * Eine Vorbereitungszeile ändert den Weg der Laufseite nicht (Issue #1451): Die Umsetzung bleibt
   * die aktuelle Stelle, und die Stufe {@code VORBEREITUNG} erscheint nicht.
   */
  @Test
  void eineVorbereitungszeileAendertDenWegDerLaufseiteNicht() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    Karte p = plan(501, f);
    angelegt(p, 10);
    Karte a = paket(502, p, "IN_PROGRESS");
    angelegt(a, 30);
    bewegt(a, 60);
    stand(f, begonnen("vorbereitung", f, 70), fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette))
        .containsEntry(ProgressStage.UMSETZUNG, StageState.LAEUFT)
        .doesNotContainKey(ProgressStage.VORBEREITUNG);
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.UMSETZUNG);
  }

  /**
   * Issue #1475: Nennt die Vorbereitungszeile den Plan, gehört sie zur Kette — als spätere Stufe
   * machte sie die Umsetzung erreicht. Erst der Filter beim Einlesen hält sie aus dem Weg.
   */
  @Test
  void eineVorbereitungszeileZumPlanAendertDenWegDerLaufseiteNicht() {
    Karte f = anforderung(500, FortschrittErmittlung.LABEL_DURCHZIEHEN);
    Karte p = plan(501, f);
    angelegt(p, 10);
    Karte a = paket(502, p, "IN_PROGRESS");
    angelegt(a, 30);
    bewegt(a, 60);
    stand(f, begonnen("vorbereitung", p, 70), fertig("abdeckung", p, 50));

    ChainProgress kette = eineKette();

    assertThat(stufen(kette)).containsEntry(ProgressStage.UMSETZUNG, StageState.LAEUFT);
    assertThat(kette.aktuelleStufe()).isEqualTo(ProgressStage.UMSETZUNG);
  }

  // --- planReviewVorhanden (Issue #1475) ---------------------------------------------------------

  @Test
  void planReviewVorhanden_nurAnDerAnforderungMitGeprueftemPlan() {
    Karte f = anforderung(500);
    Karte geprueft = planMitText(501, f, "Plan-Modell: x\nPlan-Review: fable (2026-10-06)");

    assertThat(FortschrittErmittlung.planReviewVorhanden(f, List.of(geprueft))).isTrue();
  }

  @Test
  void planReviewVorhanden_nichtOhneZeilePlanReview() {
    Karte f = anforderung(500);

    assertThat(FortschrittErmittlung.planReviewVorhanden(f, List.of(plan(501, f)))).isFalse();
  }

  @Test
  void planReviewVorhanden_nichtBeiEinerAbgeleitetenKarteDieKeinPlanIst() {
    Karte f = anforderung(500);
    Karte keinPlan = karte(502, "Paket", "BACKLOG", f, true, "Plan-Review: fable (2026-10-06)");

    assertThat(FortschrittErmittlung.planReviewVorhanden(f, List.of(keinPlan))).isFalse();
  }

  @Test
  void planReviewVorhanden_nichtAnEinerKarteOhneFachlich() {
    Karte p = plan(501, null);
    Karte geprueft = planMitText(502, p, "Plan-Review: fable (2026-10-06)");

    assertThat(FortschrittErmittlung.planReviewVorhanden(p, List.of(geprueft))).isFalse();
  }

  // --- Kettenstand an der Karte (Issue #1451, Plan #1447 E5, E10, E13) -------------------------

  private static final String GRENZE_WARTET =
      "wartet: Übergang abdeckung→umsetzung im Projekt nicht freigegeben — weiter mit kit:night";

  private static KettenStand kettenStand(Karte k, String... zeilen) {
    return FortschrittErmittlung.kettenStand(
        k, new Laufstand(k.id(), "## Laufstand\n\n" + String.join("\n", zeilen)));
  }

  private static Map<ProgressStage, StationsZustand> zustaende(KettenStand stand) {
    Map<ProgressStage, StationsZustand> m = new LinkedHashMap<>();
    stand.stationen().forEach(s -> m.put(s.station(), s.zustand()));
    return m;
  }

  private static StationStand station(KettenStand stand, ProgressStage stufe) {
    return stand.stationen().stream().filter(s -> s.station() == stufe).findFirst().orElseThrow();
  }

  @Test
  void ohneLaufstandStehenDieStationenBisZurAbdeckungAus() {
    Karte f = anforderung(500);

    KettenStand stand = FortschrittErmittlung.kettenStand(f, null);

    assertThat(stand.ziel()).isNull();
    assertThat(stand.pruefer()).isNull();
    assertThat(stand.zielErreicht()).isFalse();
    assertThat(stand.projektgrenze()).isNull();
    assertThat(zustaende(stand))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StationsZustand.STEHT_AUS),
            Map.entry(ProgressStage.REVIEW, StationsZustand.STEHT_AUS),
            Map.entry(ProgressStage.PAKETE, StationsZustand.STEHT_AUS),
            Map.entry(ProgressStage.ABDECKUNG, StationsZustand.STEHT_AUS),
            Map.entry(ProgressStage.UMSETZUNG, StationsZustand.NICHT_VORGESEHEN),
            Map.entry(ProgressStage.VORBEREITUNG, StationsZustand.NICHT_VORGESEHEN));
    assertThat(station(stand, ProgressStage.PLAN))
        .isEqualTo(
            new StationStand(ProgressStage.PLAN, StationsZustand.STEHT_AUS, "steht aus", null));
    assertThat(station(stand, ProgressStage.UMSETZUNG).text()).isEqualTo("nicht vorgesehen");
  }

  @Test
  void ohneZeileZielEndetDieKetteNachDerAbdeckung() {
    Karte f = anforderung(500, "lauf:laeuft");
    Karte p = plan(501, f);

    KettenStand stand = kettenStand(f, begonnen("pakete", p, 20), fertig("review", p, 18));

    assertThat(stand.ziel()).isNull();
    assertThat(zustaende(stand))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StationsZustand.ERLEDIGT),
            Map.entry(ProgressStage.REVIEW, StationsZustand.ERLEDIGT),
            Map.entry(ProgressStage.PAKETE, StationsZustand.LAEUFT),
            Map.entry(ProgressStage.ABDECKUNG, StationsZustand.STEHT_AUS),
            Map.entry(ProgressStage.UMSETZUNG, StationsZustand.NICHT_VORGESEHEN),
            Map.entry(ProgressStage.VORBEREITUNG, StationsZustand.NICHT_VORGESEHEN));
    assertThat(station(stand, ProgressStage.REVIEW).text()).isEqualTo("erledigt");
    assertThat(station(stand, ProgressStage.PAKETE).text()).isEqualTo("läuft");
  }

  @Test
  void zielPlanLaesstAllesHinterDemPlanNichtVorgesehen() {
    Karte f = anforderung(500, "lauf:laeuft");

    KettenStand stand = kettenStand(f, "Ziel: plan", begonnen("plan", f, 5));

    assertThat(stand.ziel()).isEqualTo(ProgressStage.PLAN);
    assertThat(zustaende(stand))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StationsZustand.LAEUFT),
            Map.entry(ProgressStage.REVIEW, StationsZustand.NICHT_VORGESEHEN),
            Map.entry(ProgressStage.PAKETE, StationsZustand.NICHT_VORGESEHEN),
            Map.entry(ProgressStage.ABDECKUNG, StationsZustand.NICHT_VORGESEHEN),
            Map.entry(ProgressStage.UMSETZUNG, StationsZustand.NICHT_VORGESEHEN),
            Map.entry(ProgressStage.VORBEREITUNG, StationsZustand.NICHT_VORGESEHEN));
  }

  @Test
  void zielPaketeSchliesstDieAbdeckungEin() {
    Karte f = anforderung(500, "lauf:laeuft");
    Karte p = plan(501, f);

    KettenStand stand = kettenStand(f, "Ziel: pakete", begonnen("abdeckung", p, 40));

    assertThat(stand.ziel()).isEqualTo(ProgressStage.PAKETE);
    assertThat(zustaende(stand))
        .containsEntry(ProgressStage.PAKETE, StationsZustand.ERLEDIGT)
        .containsEntry(ProgressStage.ABDECKUNG, StationsZustand.LAEUFT)
        .containsEntry(ProgressStage.UMSETZUNG, StationsZustand.NICHT_VORGESEHEN);
  }

  @Test
  void zielUmsetzungSiehtDieUmsetzungVorAberNichtDieVorbereitung() {
    Karte f = anforderung(500, "lauf:laeuft");
    Karte p = plan(501, f);

    KettenStand stand = kettenStand(f, "Ziel: umsetzung", fertig("abdeckung", p, 50));

    assertThat(stand.ziel()).isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(zustaende(stand))
        .containsEntry(ProgressStage.ABDECKUNG, StationsZustand.ERLEDIGT)
        .containsEntry(ProgressStage.UMSETZUNG, StationsZustand.LAEUFT)
        .containsEntry(ProgressStage.VORBEREITUNG, StationsZustand.NICHT_VORGESEHEN);
  }

  @Test
  void einUnbekanntesZielZaehltWieKeines() {
    Karte f = anforderung(500);

    KettenStand stand = kettenStand(f, "Ziel: mond");

    assertThat(stand.ziel()).isNull();
    assertThat(zustaende(stand))
        .containsEntry(ProgressStage.ABDECKUNG, StationsZustand.STEHT_AUS)
        .containsEntry(ProgressStage.UMSETZUNG, StationsZustand.NICHT_VORGESEHEN);
  }

  @Test
  void vorbereitungszeilenFuehrenDieStationVorbereitung() {
    Karte f = anforderung(500, "lauf:laeuft");

    KettenStand begonnen =
        kettenStand(f, "Ziel: push-vorbereitet", begonnen("vorbereitung", f, 90));

    assertThat(begonnen.ziel()).isEqualTo(ProgressStage.VORBEREITUNG);
    assertThat(zustaende(begonnen))
        .containsEntry(ProgressStage.UMSETZUNG, StationsZustand.ERLEDIGT)
        .containsEntry(ProgressStage.VORBEREITUNG, StationsZustand.LAEUFT);

    KettenStand fertig =
        kettenStand(
            f,
            "Ziel: push-vorbereitet",
            begonnen("vorbereitung", f, 90),
            fertig("vorbereitung", f, 100));

    assertThat(zustaende(fertig))
        .containsEntry(ProgressStage.VORBEREITUNG, StationsZustand.ERLEDIGT);
  }

  @Test
  void fertigBisZielSetztZielErreichtUndMarkiertDieZielstation() {
    Karte f = anforderung(500, "lauf:fertig");
    Karte p = plan(501, f);

    KettenStand stand =
        kettenStand(
            f,
            "fertig bis pakete",
            "Als Nächstes: Pakete nach Ready ziehen",
            "",
            "Ziel: pakete",
            fertig("abdeckung", p, 50));

    assertThat(stand.zielErreicht()).isTrue();
    assertThat(station(stand, ProgressStage.PAKETE))
        .isEqualTo(
            new StationStand(
                ProgressStage.PAKETE, StationsZustand.ERLEDIGT, "Ziel erreicht", null));
    assertThat(station(stand, ProgressStage.ABDECKUNG).text()).isEqualTo("erledigt");
    assertThat(zustaende(stand))
        .containsEntry(ProgressStage.PLAN, StationsZustand.ERLEDIGT)
        .containsEntry(ProgressStage.UMSETZUNG, StationsZustand.NICHT_VORGESEHEN);
  }

  @Test
  void fertigBisZielGiltAuchOhneStufenzeilenBisZumEnde() {
    Karte f = anforderung(500, "lauf:fertig");

    KettenStand stand = kettenStand(f, "fertig bis plan", "Ziel: plan");

    assertThat(stand.zielErreicht()).isTrue();
    assertThat(station(stand, ProgressStage.PLAN).text()).isEqualTo("Ziel erreicht");
    assertThat(zustaende(stand))
        .containsEntry(ProgressStage.REVIEW, StationsZustand.NICHT_VORGESEHEN);
  }

  @Test
  void ohneFertigBisIstDasZielNichtErreicht() {
    Karte f = anforderung(500, "lauf:fertig");
    Karte p = plan(501, f);

    KettenStand stand = kettenStand(f, "Ziel: pakete", fertig("abdeckung", p, 50));

    assertThat(stand.zielErreicht()).isFalse();
    assertThat(station(stand, ProgressStage.PAKETE).text()).isEqualTo("erledigt");
  }

  @Test
  void einPruefer() {
    Karte f = anforderung(500, "lauf:laeuft");
    Karte p = plan(501, f);

    KettenStand stand = kettenStand(f, "Ziel: pakete", "Prüfer: 1", begonnen("review", p, 15));

    assertThat(stand.pruefer()).isEqualTo(1);
    assertThat(station(stand, ProgressStage.REVIEW))
        .isEqualTo(
            new StationStand(
                ProgressStage.REVIEW, StationsZustand.LAEUFT, "läuft (1 Prüfer)", null));
  }

  @Test
  void zweiPrueferUndErledigtErstMitReviewFertig() {
    Karte f = anforderung(500, "lauf:laeuft");
    Karte p = plan(501, f);

    KettenStand laeuft = kettenStand(f, "Prüfer: 2", begonnen("review", p, 15));
    KettenStand fertig =
        kettenStand(f, "Prüfer: 2", begonnen("review", p, 15), fertig("review", p, 30));

    assertThat(laeuft.pruefer()).isEqualTo(2);
    assertThat(station(laeuft, ProgressStage.REVIEW).text()).isEqualTo("läuft (2 Prüfer)");
    assertThat(station(fertig, ProgressStage.REVIEW).zustand()).isEqualTo(StationsZustand.ERLEDIGT);
    assertThat(station(fertig, ProgressStage.REVIEW).text()).isEqualTo("erledigt");
  }

  @Test
  void ohnePrueferZeileLaeuftDiePruefungNur() {
    Karte f = anforderung(500, "lauf:laeuft");
    Karte p = plan(501, f);

    KettenStand stand = kettenStand(f, begonnen("review", p, 15));

    assertThat(stand.pruefer()).isNull();
    assertThat(station(stand, ProgressStage.REVIEW).text()).isEqualTo("läuft");
  }

  @Test
  void einePrueferzahlAusserhalbVonEinsUndZweiZaehltNicht() {
    Karte f = anforderung(500);

    assertThat(kettenStand(f, "Prüfer: 3").pruefer()).isNull();
  }

  @Test
  void grenzeMitWartetextErgibtDieProjektgrenze() {
    Karte f = anforderung(500, "lauf:wartet");
    Karte p = plan(501, f);

    KettenStand stand =
        kettenStand(
            f,
            GRENZE_WARTET,
            "",
            "Ziel: umsetzung",
            "Grenze: abdeckung",
            fertig("abdeckung", p, 50));

    assertThat(stand.projektgrenze())
        .isEqualTo(new KettenStand.Projektgrenze(ProgressStage.ABDECKUNG, GRENZE_WARTET));
    assertThat(stand.zielErreicht()).isFalse();
    assertThat(station(stand, ProgressStage.ABDECKUNG).zustand())
        .isEqualTo(StationsZustand.ERLEDIGT);
    assertThat(station(stand, ProgressStage.UMSETZUNG))
        .isEqualTo(
            new StationStand(
                ProgressStage.UMSETZUNG, StationsZustand.WARTET, "Projektgrenze", GRENZE_WARTET));
    assertThat(station(stand, ProgressStage.VORBEREITUNG).zustand())
        .isEqualTo(StationsZustand.NICHT_VORGESEHEN);
  }

  @Test
  void grenzeOhneWartetextHatKeinenGrund() {
    Karte f = anforderung(500, "lauf:laeuft");
    Karte p = plan(501, f);

    KettenStand stand =
        kettenStand(f, "Ziel: umsetzung", "Grenze: abdeckung", begonnen("pakete", p, 30));

    assertThat(stand.projektgrenze())
        .isEqualTo(new KettenStand.Projektgrenze(ProgressStage.ABDECKUNG, null));
    assertThat(station(stand, ProgressStage.PAKETE).zustand()).isEqualTo(StationsZustand.LAEUFT);
    assertThat(station(stand, ProgressStage.UMSETZUNG).zustand())
        .isEqualTo(StationsZustand.STEHT_AUS);
  }

  @Test
  void eineUnbekannteGrenzeZaehltNicht() {
    Karte f = anforderung(500);

    assertThat(kettenStand(f, "Grenze: mond").projektgrenze()).isNull();
  }

  @Test
  void eineWartendeStufeNenntIhrenGrundWoertlich() {
    Karte f = anforderung(500, "lauf:wartet");
    Karte p = plan(501, f);
    String grund = "Halt: Frage wartet auf den Menschen — siehe `## Kette angehalten`";

    KettenStand stand = kettenStand(f, grund, "", begonnen("review", p, 15), fertig("plan", f, 12));

    assertThat(station(stand, ProgressStage.REVIEW))
        .isEqualTo(new StationStand(ProgressStage.REVIEW, StationsZustand.WARTET, "wartet", grund));
    assertThat(station(stand, ProgressStage.PAKETE).zustand()).isEqualTo(StationsZustand.STEHT_AUS);
  }

  @Test
  void wartetVorDerGrenzeIstKeineProjektgrenze() {
    Karte f = anforderung(500, "lauf:wartet");
    Karte p = plan(501, f);

    KettenStand stand =
        kettenStand(f, "Halt: Frage", "Grenze: abdeckung", begonnen("review", p, 15));

    assertThat(station(stand, ProgressStage.REVIEW))
        .isEqualTo(
            new StationStand(
                ProgressStage.REVIEW, StationsZustand.WARTET, "wartet", "Halt: Frage"));
  }

  @Test
  void einAbbruchNenntDieAbgebrocheneStationMitGrund() {
    Karte f = anforderung(500, "lauf:abgebrochen");
    Karte p = plan(501, f);
    String grund = "abgebrochen: Stufe pakete: kein Paket entstanden";

    KettenStand stand =
        kettenStand(
            f,
            grund,
            "",
            begonnen("pakete", p, 30),
            fertig("review", p, 25),
            "",
            "Protokoll: .claude/protokolle/x.log");

    assertThat(station(stand, ProgressStage.PAKETE))
        .isEqualTo(
            new StationStand(
                ProgressStage.PAKETE, StationsZustand.ABGEBROCHEN, "abgebrochen", grund));
    assertThat(station(stand, ProgressStage.REVIEW).zustand()).isEqualTo(StationsZustand.ERLEDIGT);
    assertThat(station(stand, ProgressStage.ABDECKUNG).zustand())
        .isEqualTo(StationsZustand.STEHT_AUS);
  }

  @Test
  void einAbbruchOhneKopfHatKeinenGrund() {
    Karte f = anforderung(500, "lauf:abgebrochen");

    KettenStand stand = kettenStand(f, "Protokoll: x.log");

    assertThat(station(stand, ProgressStage.PLAN))
        .isEqualTo(
            new StationStand(ProgressStage.PLAN, StationsZustand.ABGEBROCHEN, "abgebrochen", null));
  }

  @Test
  void einPlanAlsStartkarteBringtPlanUndPruefungVorDemLaufMit() {
    Karte p = plan(501, null, "lauf:laeuft");

    KettenStand stand = kettenStand(p, "Ziel: umsetzung", begonnen("pakete", p, 5));

    assertThat(zustaende(stand))
        .containsExactly(
            Map.entry(ProgressStage.PLAN, StationsZustand.VOR_DEM_LAUF_ERBRACHT),
            Map.entry(ProgressStage.REVIEW, StationsZustand.VOR_DEM_LAUF_ERBRACHT),
            Map.entry(ProgressStage.PAKETE, StationsZustand.LAEUFT),
            Map.entry(ProgressStage.ABDECKUNG, StationsZustand.STEHT_AUS),
            Map.entry(ProgressStage.UMSETZUNG, StationsZustand.STEHT_AUS),
            Map.entry(ProgressStage.VORBEREITUNG, StationsZustand.NICHT_VORGESEHEN));
    assertThat(station(stand, ProgressStage.REVIEW).text()).isEqualTo("vor dem Lauf erbracht");
  }

  @Test
  void einPlanAlsStartkarteOhneStufenzeileLaeuftAbDenPaketen() {
    Karte p = plan(501, null, "lauf:laeuft");

    KettenStand stand = kettenStand(p);

    assertThat(station(stand, ProgressStage.PAKETE).zustand()).isEqualTo(StationsZustand.LAEUFT);
  }

  @Test
  void eineZeileMitUnlesbaremZeitstempelZaehltNicht() {
    Karte f = anforderung(500, "lauf:laeuft");

    KettenStand stand = kettenStand(f, "zuletzt begonnen: pakete begonnen für #501 um gestern");

    assertThat(station(stand, ProgressStage.PLAN).zustand()).isEqualTo(StationsZustand.LAEUFT);
  }

  @Test
  void dieZeilenumbruecheVonWindowsStoerenNicht() {
    Karte f = anforderung(500, "lauf:laeuft");
    Karte p = plan(501, f);

    KettenStand stand =
        FortschrittErmittlung.kettenStand(
            f,
            new Laufstand(
                f.id(),
                "## Laufstand\r\n\r\nZiel: umsetzung\r\nPrüfer: 2\r\n" + begonnen("review", p, 5)));

    assertThat(stand.ziel()).isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(stand.pruefer()).isEqualTo(2);
    assertThat(station(stand, ProgressStage.REVIEW).text()).isEqualTo("läuft (2 Prüfer)");
  }

  @Test
  void ohneLaufLabelStehtDieAktuelleStationAus() {
    Karte f = anforderung(500);
    Karte p = plan(501, f);

    KettenStand stand = kettenStand(f, fertig("review", p, 20));

    assertThat(station(stand, ProgressStage.PAKETE).zustand()).isEqualTo(StationsZustand.STEHT_AUS);
  }
}
