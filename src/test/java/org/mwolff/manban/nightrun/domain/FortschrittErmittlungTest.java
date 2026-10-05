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
// Dateien, die sich dieselben Vorrichtungen teilen müssten.
@SuppressWarnings({"PMD.TooManyMethods", "PMD.GodClass", "PMD.CyclomaticComplexity"})
class FortschrittErmittlungTest {

  private static final Instant START = Instant.parse("2026-10-03T13:01:00Z");
  private static final Instant ENDE = START.plus(Duration.ofHours(2));
  private static final Zeitfenster FENSTER = new Zeitfenster(START, ENDE);
  private static final String TOKEN = "nacht";
  private static final String AGENT = "claude-opus-5-5";

  private final List<Karte> karten = new ArrayList<>();
  private final List<Aktivitaet> aktivitaeten = new ArrayList<>();
  private final List<Laufstand> laufstaende = new ArrayList<>();
  private final List<Zeitfenster> fremdeFenster = new ArrayList<>();

  // --- Aufbau ----------------------------------------------------------------------------------

  private static Instant um(long minuten) {
    return START.plus(Duration.ofMinutes(minuten));
  }

  private static NightRun lauf(NightRunMode mode, @Nullable String tokenName, boolean complete) {
    return new NightRun(
        1L,
        7L,
        START,
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

  private void angelegt(Karte k, long minute) {
    akt(k, "CREATED", um(minute));
  }

  private void bewegt(Karte k, long minute) {
    akt(k, "MOVED", um(minute));
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
}
