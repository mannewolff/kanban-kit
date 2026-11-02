package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.Aufgabenstufe;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.DokumentStandView;
import org.mwolff.manban.card.application.CardRunQueryService.PaketStandView;
import org.mwolff.manban.card.application.CardRunQueryService.VorhabenStandView;
import org.mwolff.manban.card.application.VorhabenRef;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.ItemKostenJeKarte;
import org.mwolff.manban.nightrun.application.NightRunUsageRepository.RetainedByKind;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.FestgehalteneVoraussage;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.Stand;
import org.mwolff.manban.nightrun.application.VorhabenKostenRepository.VorhabenKosten;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.AbschlussView;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.LaufendView;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.VergleichView;
import org.mwolff.manban.nightrun.application.VorhabenKostenService.VorhabenKostenView;
import org.mwolff.manban.nightrun.domain.KostenVergleich;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Art;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Ergebnis;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Grund;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Voraussage;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.ZuWenigDaten;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung.Vorgang;
import org.mwolff.manban.nightrun.domain.NightRunAttempt;
import org.mwolff.manban.nightrun.domain.NightRunAttemptStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunUsage;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/**
 * Der Dienst der Kostenvoraussage (Issue #2003, Plan #1990 E3, E6, E7): Er trägt Kartenlage,
 * Vorgänge, Kosten je Karte samt verwaister Items und die Aufbewahrungsgrenze richtig zusammen und
 * schließt unvollständige Vorhaben aus der Grundlage aus.
 *
 * <p>Die Grundlage im Normalfall: drei abgeschlossene Vorhaben mit je einer Anforderung (5 $ an je
 * einem Vorgang Plan 2 $, Prüfung, Schnitt und Abdeckung zu je 1 $) und einem mittleren Paket zu
 * 1,50 $. Ein Vorhaben mit nur seiner Anforderung kostet damit typisch 5 $ + 1 × 1,50 $ = 6,50 $.
 */
// PMD.CyclomaticComplexity: die Summe vieler kleiner Fälle (keiner über 2), mit Issue #2005 kommen
// die Aufrufe der Kostenzeile dazu — dieselbe Ursache wie PMD.TooManyMethods.
@SuppressWarnings({
  "PMD.CouplingBetweenObjects",
  "PMD.CyclomaticComplexity",
  "PMD.ExcessiveImports",
  "PMD.TooManyMethods"
})
class VorhabenKostenServiceTest {

  private static final long PROJECT = 42L;
  private static final Instant JETZT = Instant.parse("2026-11-02T06:00:00Z");
  private static final Instant FENSTER = JETZT.minus(Duration.ofDays(84));
  private static final Instant VOR_EINER_WOCHE = JETZT.minus(Duration.ofDays(7));
  private static final Instant LAUF = JETZT.minus(Duration.ofDays(6));
  private static final Instant SPAETER = JETZT.plus(Duration.ofHours(1));
  private static final Instant ABSCHLUSS = JETZT.minus(Duration.ofDays(1));
  private static final String OPUS = "claude-opus-5-5";
  private static final String SONNET = "claude-sonnet-5-5";

  private static final long ZIEL = 400L;
  private static final long USER = 7L;
  private static final long BOARD = 1L;

  private final CardRunQueryService cards = mock(CardRunQueryService.class);
  private final NightRunRepository runs = mock(NightRunRepository.class);
  private final NightRunUsageRepository usage = mock(NightRunUsageRepository.class);
  private final BoardService boards = mock(BoardService.class);
  private final PermissionChecker permissions = mock(PermissionChecker.class);
  private final FesteKosten festgehalten = new FesteKosten();
  private final VorhabenKostenService service =
      new VorhabenKostenService(
          cards,
          runs,
          usage,
          festgehalten,
          new NightRunProperties(3, null, 2, null, null),
          boards,
          permissions);

  private final List<VorhabenStandView> staende = new ArrayList<>();
  private final List<Vorgang> vorgaenge = new ArrayList<>();
  private final Map<Integer, ItemKostenJeKarte> kosten = new HashMap<>();
  private final Map<Integer, ItemKostenJeKarte> kostenBisAbschluss = new HashMap<>();
  private final List<RetainedByKind> aufbewahrt = new ArrayList<>();

  @BeforeEach
  void verdrahten() {
    when(cards.vorhabenStaende(PROJECT)).thenReturn(staende);
    when(boards.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(cards.requireProjectId(anyLong())).thenReturn(PROJECT);
    when(runs.findAttemptsSince(eq(PROJECT), any())).thenReturn(vorgaenge);
    when(usage.retentionBoundary(PROJECT)).thenReturn(aufbewahrt);
    when(usage.itemKostenJeKarte(anyLong(), anyCollection(), isNull()))
        .thenAnswer(
            a -> {
              List<ItemKostenJeKarte> zeilen = new ArrayList<>();
              a.<Collection<Integer>>getArgument(1).stream()
                  .map(kosten::get)
                  .filter(k -> k != null)
                  .forEach(zeilen::add);
              return zeilen;
            });
    when(usage.itemKostenJeKarte(anyLong(), anyCollection(), any(Instant.class)))
        .thenAnswer(
            a -> {
              List<ItemKostenJeKarte> zeilen = new ArrayList<>();
              a.<Collection<Integer>>getArgument(1).stream()
                  .map(kostenBisAbschluss::get)
                  .filter(k -> k != null)
                  .forEach(zeilen::add);
              return zeilen;
            });
  }

  // --- Bausteine -------------------------------------------------------------------------------

  private static VorhabenRef ref(long id, int nummer) {
    return new VorhabenRef(id, nummer, null, "Vorhaben " + nummer, false);
  }

  private static PaketStandView paket(
      int nummer, @Nullable Aufgabenstufe stufe, @Nullable Instant done) {
    return new PaketStandView(nummer, done != null, stufe, VOR_EINER_WOCHE, done);
  }

  private static NightRunUsage kostet(@Nullable String betrag) {
    return new NightRunUsage(
        betrag == null ? null : new BigDecimal(betrag), null, null, null, null, null);
  }

  private void vorgang(
      NightRunAttemptStage stufe, int karte, @Nullable String modell, @Nullable String betrag) {
    vorgang(stufe, karte, modell, betrag == null ? null : kostet(betrag), NightRunKind.NIGHT, true);
  }

  private void vorgang(
      NightRunAttemptStage stufe,
      int karte,
      @Nullable String modell,
      @Nullable NightRunUsage verbrauch,
      NightRunKind gattung,
      boolean abgeschlossen) {
    vorgaenge.add(
        new Vorgang(
            LAUF,
            gattung,
            abgeschlossen,
            vorgaenge.size(),
            new NightRunAttempt(stufe, karte, modell, null, true, verbrauch, List.of())));
  }

  private void misst(int karte, String betrag) {
    kosten.put(karte, new ItemKostenJeKarte(karte, new BigDecimal(betrag), 0L, VOR_EINER_WOCHE));
  }

  /**
   * Ein abgeschlossenes Grundlage-Vorhaben: Anforderung {@code nummer + 1} mit je einem Vorgang je
   * Dokumentstufe, ein mittleres Paket {@code nummer + 2}, das vor fünf Tagen nach Done kam.
   */
  private void grundlage(int nummer) {
    grundlage(nummer, JETZT.minus(Duration.ofDays(5)), VOR_EINER_WOCHE);
  }

  private void grundlage(int nummer, @Nullable Instant done, Instant fruehester) {
    int anforderung = nummer + 1;
    int paket = nummer + 2;
    staende.add(
        new VorhabenStandView(
            ref(nummer * 10L, nummer),
            1L,
            List.of(paket(paket, Aufgabenstufe.MITTEL, done)),
            List.of(new DokumentStandView(anforderung, "FACHLICH", false, true)),
            true,
            true,
            true,
            List.of(anforderung, paket),
            fruehester));
    vorgang(NightRunAttemptStage.PLAN, anforderung, OPUS, "2");
    vorgang(NightRunAttemptStage.REVIEW, anforderung, OPUS, "1");
    vorgang(NightRunAttemptStage.PAKETE, anforderung, OPUS, "1");
    vorgang(NightRunAttemptStage.ABDECKUNG, anforderung, OPUS, "1");
    vorgang(NightRunAttemptStage.UMSETZUNG, paket, OPUS, "1.50");
    misst(anforderung, "5");
    misst(paket, "1.50");
    if (done != null) {
      festgehalten.speichere(
          VorhabenKosten.leer(nummer * 10L, PROJECT)
              .mitAbschluss(done, new BigDecimal("6.50"), false, List.of(anforderung, paket)));
    }
  }

  /** Das Ziel „Export“ mit nur seiner ungeschnittenen Anforderung 401. */
  private void zielNurAnforderung() {
    zielNurAnforderung(JETZT.minus(Duration.ofDays(1)));
  }

  private void zielNurAnforderung(Instant fruehester) {
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400),
            1L,
            List.of(),
            List.of(new DokumentStandView(401, "FACHLICH", true, false)),
            true,
            true,
            false,
            List.of(401),
            fruehester));
  }

  private Ergebnis rechne() {
    return service.aktuelleVoraussage(PROJECT, ZIEL, JETZT).orElseThrow();
  }

  private Voraussage voraussage() {
    Ergebnis ergebnis = rechne();
    assertThat(ergebnis).isInstanceOf(Voraussage.class);
    return (Voraussage) ergebnis;
  }

  // --- Zusammentragen --------------------------------------------------------------------------

  @Test
  @DisplayName("Die Eingaben werden aus Kartenlage, Vorgängen und Kosten je Karte zusammengetragen")
  void eingabenZusammengetragen() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();

    Voraussage v = voraussage();

    assertThat(v.art()).isEqualTo(Art.ANFORDERUNG);
    assertThat(v.typisch()).isEqualByComparingTo("6.50");
    assertThat(v.paketzahl()).isEqualTo(1);
    assertThat(v.bisher()).isEqualByComparingTo("0");
    assertThat(v.unvollstaendig()).isFalse();
    verify(runs).findAttemptsSince(PROJECT, FENSTER);
    verify(usage).itemKostenJeKarte(PROJECT, Set.of(11, 12, 21, 22, 31, 32, 401), null);
  }

  @Test
  @DisplayName("Fehlt das Vorhaben oder hat es weder Dokument noch Paket, gibt es keine Zeile")
  void ohneVorhabenKeineZeile() {
    grundlage(10);
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400), 1L, List.of(), List.of(), false, false, false, List.of(), null));

    assertThat(service.aktuelleVoraussage(PROJECT, ZIEL, JETZT)).isEmpty();
    assertThat(service.aktuelleVoraussage(PROJECT, 999L, JETZT)).isEmpty();
  }

  @Test
  @DisplayName("Verwaiste Items zählen in den bisherigen Kosten mit, je Stufe ihr Angefallenes")
  void verwaisteItemsUndAngefallenes() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    // 0,50 $ aus Items, deren Lauf schon verdrängt ist — nur itemKostenJeKarte kennt sie.
    misst(401, "0.50");
    vorgang(NightRunAttemptStage.PLAN, 401, OPUS, "0.50");

    Voraussage v = voraussage();

    // Plan 2 $ − 0,50 $, die übrigen Stufen 3 $, ein Paket 1,50 $, bisher 0,50 $.
    assertThat(v.bisher()).isEqualByComparingTo("0.50");
    assertThat(v.typisch()).isEqualByComparingTo("6.50");
  }

  @Test
  @DisplayName("Ein Item ohne Kostenangabe am Vorhaben macht bisher und Voraussage unvollständig")
  void zielMitLuecke() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    kosten.put(401, new ItemKostenJeKarte(401, null, 1L, VOR_EINER_WOCHE));
    vorgang(NightRunAttemptStage.PLAN, 401, OPUS, kostet(null), NightRunKind.NIGHT, false);

    Voraussage v = voraussage();

    assertThat(v.unvollstaendig()).isTrue();
    assertThat(v.bisher()).isEqualByComparingTo("0");
    assertThat(v.typisch()).isEqualByComparingTo("6.50");
  }

  @Test
  @DisplayName("Liegt der Beginn des Vorhabens vor der Aufbewahrungsgrenze, ist es unvollständig")
  void zielVorDerGrenze() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung(VOR_EINER_WOCHE.minusSeconds(2));
    aufbewahrt.add(new RetainedByKind(NightRunKind.INTERACTIVE, 9L, JETZT));
    aufbewahrt.add(new RetainedByKind(NightRunKind.NIGHT, 3L, VOR_EINER_WOCHE.minusSeconds(1)));

    assertThat(voraussage().unvollstaendig()).isTrue();
  }

  @Test
  @DisplayName("Mit Paketen und ohne ungeschnittenes Dokument rechnet die zweite Stufe")
  void zweiteStufe() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400),
            1L,
            List.of(
                paket(402, Aufgabenstufe.MITTEL, null),
                paket(403, null, null),
                paket(404, Aufgabenstufe.LEICHT, JETZT)),
            List.of(new DokumentStandView(401, "FACHLICH", true, true)),
            true,
            true,
            false,
            List.of(401, 402, 403, 404),
            JETZT.minus(Duration.ofDays(1))));
    misst(401, "4");
    misst(402, "0.50");
    misst(404, "1");
    vorgang(NightRunAttemptStage.PLAN, 401, OPUS, "2");
    vorgang(NightRunAttemptStage.REVIEW, 401, OPUS, "1");
    vorgang(NightRunAttemptStage.PAKETE, 401, OPUS, "1");
    vorgang(NightRunAttemptStage.UMSETZUNG, 402, OPUS, "0.50");
    // Eine interaktive Sitzung zählt nicht: sonst wäre die Abdeckung begonnen.
    vorgang(NightRunAttemptStage.ABDECKUNG, 401, OPUS, kostet("9"), NightRunKind.INTERACTIVE, true);

    Voraussage v = voraussage();

    // Alle Dokumentstufen durchlaufen (Umsetzung begonnen); 402 mittel 1,50 $ − 0,50 $, 403 ohne
    // Stufe 1,50 $, 404 in Done; bisher 4 + 0,50 + 1 = 5,50 $.
    assertThat(v.art()).isEqualTo(Art.PAKETE);
    assertThat(v.paketzahl()).isEqualTo(3);
    assertThat(v.ohneStufe()).isEqualTo(1);
    assertThat(v.bisher()).isEqualByComparingTo("5.50");
    assertThat(v.typisch()).isEqualByComparingTo("8");
  }

  @Test
  @DisplayName("Ein Vorhaben ohne Paket rechnet die erste Stufe, auch ohne offenes Dokument")
  void ohnePaketErsteStufe() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400),
            1L,
            List.of(),
            List.of(new DokumentStandView(401, "PLAN", false, false)),
            false,
            true,
            false,
            List.of(401),
            null));

    assertThat(voraussage().art()).isEqualTo(Art.VOR_DEM_SCHNITT);
  }

  // --- Preise ----------------------------------------------------------------------------------

  @Test
  @DisplayName("Der Preis einer Stufe gilt beim Modell ihres jüngsten Vorgangs")
  void preisBeimJuengstenModell() {
    vorgang(NightRunAttemptStage.PLAN, 11, SONNET, "100");
    vorgang(NightRunAttemptStage.PLAN, 11, null, "100");
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    // Ein abgeschlossener Vorgang ohne Modell und Kosten nach dem jüngsten zählt im Preis nicht.
    vorgang(NightRunAttemptStage.REVIEW, 999, null, null);
    // Ein Vorgang eines nicht abgeschlossenen Laufs zählt im Preis nicht.
    vorgang(NightRunAttemptStage.ABDECKUNG, 999, SONNET, kostet("100"), NightRunKind.NIGHT, false);

    // Vorhaben 10 hat drei Plan-Vorgänge, typisch bleibt einer.
    assertThat(voraussage().typisch()).isEqualByComparingTo("6.50");
  }

  @Test
  @DisplayName("Ein Vorgang vor den zwölf Wochenfenstern zählt nur in der Vorgangszahl")
  void vorgangVorDemFenster() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    vorgaenge.add(
        new Vorgang(
            FENSTER.minusSeconds(1),
            NightRunKind.NIGHT,
            true,
            vorgaenge.size(),
            new NightRunAttempt(
                NightRunAttemptStage.PLAN, 999, SONNET, null, true, kostet("100"), List.of())));

    assertThat(voraussage().typisch()).isEqualByComparingTo("6.50");
  }

  @Test
  @DisplayName("Fehlt einer Stufe jeder Vorgang mit Modell, fehlt ihr Preis")
  void stufeOhneVorgangMitModell() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    vorgaenge.removeIf(v -> v.attempt().stage() == NightRunAttemptStage.ABDECKUNG);
    vorgang(NightRunAttemptStage.ABDECKUNG, 11, null, "1");
    vorgang(NightRunAttemptStage.ABDECKUNG, 21, null, "1");
    vorgang(NightRunAttemptStage.ABDECKUNG, 31, null, "1");

    assertThat(rechne()).isEqualTo(new ZuWenigDaten(Grund.STUFE_OHNE_PREIS));
  }

  @Test
  @DisplayName("Paketpreise: nur Pakete in Done, vollständig, zuletzt mit dem umsetzenden Modell")
  void paketpreise() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    // Ein viertes Vorhaben, noch offen, liefert Pakete, die nicht zählen dürfen.
    Instant vorDemFenster = FENSTER.minusSeconds(1);
    staende.add(
        new VorhabenStandView(
            ref(500L, 50),
            1L,
            List.of(
                paket(51, Aufgabenstufe.MITTEL, null),
                paket(52, Aufgabenstufe.MITTEL, JETZT),
                paket(53, Aufgabenstufe.MITTEL, vorDemFenster),
                paket(54, Aufgabenstufe.MITTEL, JETZT),
                paket(55, Aufgabenstufe.MITTEL, JETZT),
                paket(56, Aufgabenstufe.MITTEL, JETZT),
                paket(57, null, JETZT)),
            List.of(),
            false,
            false,
            false,
            List.of(),
            null));
    misst(51, "100");
    misst(52, "100"); // letzter Umsetzungsvorgang mit einem anderen Modell
    misst(53, "100"); // vor den zwölf Wochenfenstern nach Done
    kosten.put(54, new ItemKostenJeKarte(54, new BigDecimal("100"), 1L, VOR_EINER_WOCHE));
    kosten.put(55, new ItemKostenJeKarte(55, null, 0L, VOR_EINER_WOCHE));
    // 56 ohne Messung
    misst(57, "3"); // ohne Aufgabenstufe: nur im Preis über alle Stufen
    vorgang(NightRunAttemptStage.UMSETZUNG, 51, OPUS, "100");
    vorgang(NightRunAttemptStage.UMSETZUNG, 52, SONNET, "100");
    vorgang(NightRunAttemptStage.UMSETZUNG, 53, OPUS, "100");
    vorgang(NightRunAttemptStage.UMSETZUNG, 54, OPUS, "100");
    vorgang(NightRunAttemptStage.UMSETZUNG, 55, OPUS, "100");
    vorgang(NightRunAttemptStage.UMSETZUNG, 56, OPUS, "100");
    vorgang(NightRunAttemptStage.UMSETZUNG, 57, OPUS, "3");
    vorgang(NightRunAttemptStage.UMSETZUNG, 57, null, "3");
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400),
            1L,
            List.of(paket(401, Aufgabenstufe.MITTEL, null), paket(402, null, null)),
            List.of(),
            false,
            false,
            false,
            List.of(401, 402),
            null));

    Voraussage v = voraussage();

    // Stufen 5 $; 401 mittel aus den drei Grundlage-Paketen (1,50 $); 402 ohne Stufe aus diesen
    // und 57 — typisch 1,50 $, oben 1,50 $ + 0,55 × 1,50 $. Ein 100-$-Paket höbe den oberen Wert.
    assertThat(v.typisch()).isEqualByComparingTo("8");
    assertThat(v.oben()).isEqualByComparingTo("8.83");
  }

  @Test
  @DisplayName("Ein Paket vor der Aufbewahrungsgrenze liefert keinen Preis")
  void paketVorDerGrenze() {
    aufbewahrt.add(new RetainedByKind(NightRunKind.NIGHT, 3L, VOR_EINER_WOCHE.minusSeconds(1)));
    grundlage(10);
    grundlage(20);
    grundlage(30);
    staende.add(
        new VorhabenStandView(
            ref(500L, 50),
            1L,
            List.of(
                new PaketStandView(
                    51, true, Aufgabenstufe.LEICHT, VOR_EINER_WOCHE.minusSeconds(2), JETZT)),
            List.of(),
            false,
            false,
            false,
            List.of(51),
            null));
    misst(51, "100");
    vorgang(NightRunAttemptStage.UMSETZUNG, 51, OPUS, "100");
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400),
            1L,
            List.of(paket(401, Aufgabenstufe.LEICHT, null)),
            List.of(),
            false,
            false,
            false,
            List.of(401),
            null));

    Voraussage v = voraussage();

    // Ohne leichten Preis gilt der über alle Stufen: 5 $ + 1,50 $.
    assertThat(v.typisch()).isEqualByComparingTo("6.50");
    assertThat(v.ohneStufe()).isEqualTo(1);
  }

  // --- Grundlage -------------------------------------------------------------------------------

  @Test
  @DisplayName("Ein Vorhaben mit einem Item ohne Kostenangabe zählt nicht zur Grundlage")
  void grundlageMitItemOhneKosten() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    kosten.put(31, new ItemKostenJeKarte(31, new BigDecimal("5"), 1L, VOR_EINER_WOCHE));

    assertThat(rechne()).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("Ein Vorhaben mit einem Vorgang ohne Kostenangabe zählt nicht zur Grundlage")
  void grundlageMitVorgangOhneKosten() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    vorgang(NightRunAttemptStage.REVIEW, 31, OPUS, kostet(null), NightRunKind.NIGHT, true);

    assertThat(rechne()).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("Ein Vorhaben mit einem Vorgang ohne Verbrauch zählt nicht zur Grundlage")
  void grundlageMitVorgangOhneVerbrauch() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    vorgang(NightRunAttemptStage.REVIEW, 31, OPUS, null, NightRunKind.NIGHT, true);

    assertThat(rechne()).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("Ein Vorhaben, das vor der Aufbewahrungsgrenze begann, zählt nicht zur Grundlage")
  void grundlageVorDerGrenze() {
    aufbewahrt.add(new RetainedByKind(NightRunKind.NIGHT, 3L, VOR_EINER_WOCHE));
    grundlage(10);
    grundlage(20);
    grundlage(30, JETZT.minus(Duration.ofDays(5)), VOR_EINER_WOCHE.minusSeconds(1));
    zielNurAnforderung();

    assertThat(rechne()).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("Ohne vollen Ringpuffer gibt es keine Grenze, auch ein altes Vorhaben zählt")
  void ohneVollenPufferKeineGrenze() {
    Instant alt = FENSTER.minus(Duration.ofDays(30));
    aufbewahrt.add(new RetainedByKind(NightRunKind.NIGHT, 2L, alt));
    grundlage(10);
    grundlage(20);
    grundlage(30, JETZT.minus(Duration.ofDays(5)), alt);
    zielNurAnforderung();

    assertThat(voraussage().typisch()).isEqualByComparingTo("6.50");
    // Die Vorgänge werden ab dem frühesten Beginn der Grundlage gelesen.
    verify(runs).findAttemptsSince(PROJECT, alt);
  }

  @Test
  @DisplayName("Ein Vorhaben, das vor den zwölf Wochenfenstern abgeschlossen wurde, zählt nicht")
  void grundlageVorDemFenster() {
    grundlage(10);
    grundlage(20);
    grundlage(30, FENSTER.minusSeconds(1), FENSTER.minus(Duration.ofDays(3)));
    zielNurAnforderung();

    assertThat(rechne()).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("Ein Vorhaben, dessen Abschluss genau am Beginn des Fensters liegt, zählt")
  void grundlageAmFensterbeginn() {
    grundlage(10);
    grundlage(20);
    grundlage(30, FENSTER, VOR_EINER_WOCHE);
    zielNurAnforderung();

    assertThat(rechne()).isInstanceOf(Voraussage.class);
  }

  @Test
  @DisplayName("Ein Vorhaben mit unbekanntem Done eines Pakets zählt nicht zur Grundlage")
  void grundlageMitUnbekanntemDone() {
    grundlage(10);
    grundlage(20);
    staende.add(
        new VorhabenStandView(
            ref(300L, 30),
            1L,
            List.of(
                new PaketStandView(32, true, Aufgabenstufe.MITTEL, VOR_EINER_WOCHE, null),
                paket(33, Aufgabenstufe.MITTEL, JETZT)),
            List.of(new DokumentStandView(31, "FACHLICH", false, true)),
            true,
            true,
            true,
            List.of(31, 32, 33),
            VOR_EINER_WOCHE));
    for (NightRunAttemptStage stufe : NightRunAttemptStage.values()) {
      vorgang(stufe, 31, OPUS, "1");
    }
    zielNurAnforderung();

    assertThat(rechne()).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("Offene Vorhaben und das Vorhaben selbst zählen nicht zur Grundlage")
  void offeneUndZielNichtInGrundlage() {
    grundlage(10);
    grundlage(20);
    staende.add(
        new VorhabenStandView(
            ref(300L, 30),
            1L,
            List.of(paket(32, Aufgabenstufe.MITTEL, JETZT)),
            List.of(new DokumentStandView(31, "PLAN", true, false)),
            false,
            true,
            false,
            List.of(31, 32),
            VOR_EINER_WOCHE));
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400),
            1L,
            List.of(paket(402, Aufgabenstufe.MITTEL, JETZT)),
            List.of(new DokumentStandView(401, "FACHLICH", false, true)),
            true,
            true,
            true,
            List.of(401, 402),
            VOR_EINER_WOCHE));
    for (NightRunAttemptStage stufe : NightRunAttemptStage.values()) {
      vorgang(stufe, 31, OPUS, "1");
      vorgang(stufe, 401, OPUS, "1");
    }

    assertThat(service.aktuelleVoraussage(PROJECT, ZIEL, JETZT))
        .isEqualTo(Optional.of(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN)));
  }

  // --- Grundlage aus den festgehaltenen Abschlüssen (Issue #2004) ------------------------------

  @Test
  @DisplayName("Ohne festgehaltenen Abschluss zählt ein Vorhaben nicht zur Grundlage")
  void grundlageNurMitFestgehaltenemAbschluss() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    festgehalten.zeilen.remove(300L);
    zielNurAnforderung();

    assertThat(rechne()).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("Maßgeblich ist der festgehaltene Abschluss, nicht das späteste Done der Pakete")
  void grundlageMitFestgehaltenemZeitpunkt() {
    grundlage(10);
    grundlage(20);
    grundlage(30, FENSTER.minusSeconds(1), VOR_EINER_WOCHE);
    festgehalten.speichere(
        festgehalten
            .zeilen
            .get(300L)
            .mitAbschluss(FENSTER, BigDecimal.ONE, false, List.of(31, 32)));
    zielNurAnforderung();

    assertThat(rechne()).isInstanceOf(Voraussage.class);
  }

  // --- Festhalten (Issue #2004, Plan #1990 E8 bis E10, E12) ------------------------------------

  private static DokumentStandView anforderung(boolean offen, boolean geschnitten) {
    return new DokumentStandView(401, "FACHLICH", offen, geschnitten);
  }

  private void zielStand(
      List<PaketStandView> pakete,
      List<DokumentStandView> dokumente,
      boolean abgeschlossen,
      List<Integer> kostenkarten) {
    staende.removeIf(s -> s.vorhaben().id() == ZIEL);
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400),
            1L,
            pakete,
            dokumente,
            dokumente.stream().anyMatch(d -> "FACHLICH".equals(d.art())),
            !dokumente.isEmpty(),
            abgeschlossen,
            kostenkarten,
            VOR_EINER_WOCHE));
  }

  private void eingefuehrt() {
    festgehalten.einfuehren(PROJECT, VOR_EINER_WOCHE);
  }

  private void kostetBisAbschluss(int karte, String betrag) {
    kostenBisAbschluss.put(
        karte, new ItemKostenJeKarte(karte, new BigDecimal(betrag), 0L, VOR_EINER_WOCHE));
  }

  private @Nullable VorhabenKosten zeile() {
    return festgehalten.zeilen.get(ZIEL);
  }

  private static FestgehalteneVoraussage beziffert(Art art, String typisch) {
    BigDecimal betrag = new BigDecimal(typisch);
    return new FestgehalteneVoraussage(
        Stand.BEZIFFERT, art, betrag, betrag, betrag, VOR_EINER_WOCHE);
  }

  /** Ein schon festgehaltenes, abgeschlossenes Vorhaben mit 16 $ und 20 $ Voraussage und 18 $. */
  private void festgehaltenAbgeschlossen() {
    festgehalten.speichere(
        VorhabenKosten.leer(ZIEL, PROJECT)
            .mitAnforderung(beziffert(Art.ANFORDERUNG, "16"))
            .mitPaketen(beziffert(Art.PAKETE, "20"))
            .mitAbschluss(ABSCHLUSS, new BigDecimal("18"), false, List.of(401, 402)));
  }

  @Test
  @DisplayName("Szenario: Voraussage und tatsächliche Kosten")
  void szenarioVoraussageUndTatsaechlicheKosten() {
    // Angenommen: für „Export“ wurden aus der Anforderung typisch 16 $ und aus den Paketen typisch
    // 20 $ festgehalten, und das Vorhaben ist eben abgeschlossen und hat 18 $ gekostet.
    eingefuehrt();
    festgehalten.speichere(
        VorhabenKosten.leer(ZIEL, PROJECT)
            .mitAnforderung(beziffert(Art.ANFORDERUNG, "16"))
            .mitPaketen(beziffert(Art.PAKETE, "20")));
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));
    kostetBisAbschluss(401, "10");
    kostetBisAbschluss(402, "8");

    service.festhalten(PROJECT, JETZT);

    VorhabenKosten zeile = zeile();
    assertThat(zeile).isNotNull();
    assertThat(zeile.tatsaechlichUsd()).isEqualByComparingTo("18");
    assertThat(zeile.anforderung().typischUsd()).isEqualByComparingTo("16");
    assertThat(zeile.pakete().typischUsd()).isEqualByComparingTo("20");
    assertThat(
            KostenVergleich.abweichung(
                zeile.tatsaechlichUsd(),
                zeile.tatsaechlichUnvollstaendig(),
                zeile.anforderung().typischUsd()))
        .hasValue(13);
    assertThat(
            KostenVergleich.abweichung(
                zeile.tatsaechlichUsd(),
                zeile.tatsaechlichUnvollstaendig(),
                zeile.pakete().typischUsd()))
        .hasValue(-10);
  }

  @Test
  @DisplayName("Das erste Paket hält die Voraussage aus der Anforderung fest")
  void erstesPaketHaeltAnforderungFest() {
    eingefuehrt();
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null)),
        List.of(anforderung(true, false)),
        false,
        List.of(401, 402));

    service.festhalten(PROJECT, JETZT);

    FestgehalteneVoraussage anforderung = zeile().anforderung();
    assertThat(anforderung.stand()).isEqualTo(Stand.BEZIFFERT);
    assertThat(anforderung.art()).isEqualTo(Art.ANFORDERUNG);
    assertThat(anforderung.typischUsd()).isEqualByComparingTo("6.50");
    assertThat(anforderung.festgehaltenAt()).isEqualTo(JETZT);
    assertThat(zeile().pakete()).as("ein Dokument ist noch ungeschnitten offen").isNull();
    assertThat(zeile().abgeschlossenAt()).isNull();
  }

  @Test
  @DisplayName("Ohne Anforderung heißt die erste Voraussage „vor dem Schnitt“")
  void ohneAnforderungVorDemSchnitt() {
    eingefuehrt();
    grundlage(10);
    grundlage(20);
    grundlage(30);
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400),
            1L,
            List.of(paket(402, Aufgabenstufe.MITTEL, null)),
            List.of(new DokumentStandView(403, "PLAN", true, false)),
            false,
            true,
            false,
            List.of(402, 403),
            VOR_EINER_WOCHE));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().anforderung().art()).isEqualTo(Art.VOR_DEM_SCHNITT);
  }

  @Test
  @DisplayName("Schnitt in einem Zug: zuerst die Voraussage aus der Anforderung, dann die Pakete")
  void beideUebergaengeImSelbenLauf() {
    eingefuehrt();
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null)),
        List.of(anforderung(true, true)),
        false,
        List.of(401, 402));
    Voraussage ausPaketen = (Voraussage) service.aktuelleVoraussage(PROJECT, ZIEL, JETZT).get();

    service.festhalten(PROJECT, JETZT);

    VorhabenKosten zeile = zeile();
    assertThat(zeile.anforderung().art()).isEqualTo(Art.ANFORDERUNG);
    assertThat(zeile.anforderung().typischUsd()).isEqualByComparingTo("6.50");
    assertThat(zeile.pakete().stand()).isEqualTo(Stand.BEZIFFERT);
    assertThat(zeile.pakete().art()).isEqualTo(Art.PAKETE);
    assertThat(zeile.pakete().typischUsd()).isEqualByComparingTo(ausPaketen.typisch());
    assertThat(zeile.pakete().untenUsd()).isEqualByComparingTo(ausPaketen.unten());
    assertThat(zeile.pakete().obenUsd()).isEqualByComparingTo(ausPaketen.oben());
  }

  @Test
  @DisplayName(
      "Gab es nie ein ungeschnittenes Dokument, steht „keine Voraussage“ aus der Anforderung")
  void nieUngeschnittenKeineAnforderung() {
    eingefuehrt();
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielStand(List.of(paket(402, Aufgabenstufe.MITTEL, null)), List.of(), false, List.of(402));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().anforderung()).isEqualTo(FestgehalteneVoraussage.keine(JETZT));
    assertThat(zeile().pakete().stand()).isEqualTo(Stand.BEZIFFERT);
  }

  @Test
  @DisplayName("Liefert die Rechnung beim Festhalten zu wenig Daten, steht „keine Voraussage“")
  void zuWenigDatenBeimFesthalten() {
    eingefuehrt();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null)),
        List.of(anforderung(true, true)),
        false,
        List.of(401, 402));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().anforderung()).isEqualTo(FestgehalteneVoraussage.keine(JETZT));
    assertThat(zeile().pakete()).isEqualTo(FestgehalteneVoraussage.keine(JETZT));
  }

  @Test
  @DisplayName("Festgehaltene Voraussagen ändern sich danach nicht mehr")
  void voraussagenBleiben() {
    eingefuehrt();
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null)),
        List.of(anforderung(true, true)),
        false,
        List.of(401, 402));
    service.festhalten(PROJECT, JETZT);
    VorhabenKosten vorher = zeile();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null), paket(403, Aufgabenstufe.SCHWER, null)),
        List.of(anforderung(true, true)),
        false,
        List.of(401, 402, 403));

    service.festhalten(PROJECT, SPAETER);

    assertThat(zeile()).isEqualTo(vorher);
  }

  @Test
  @DisplayName("Ohne Übergang schreibt ein Lauf nichts")
  void ohneUebergangKeinSchreiben() {
    eingefuehrt();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null)),
        List.of(anforderung(true, true)),
        false,
        List.of(401, 402));
    service.festhalten(PROJECT, JETZT);
    int geschrieben = festgehalten.gespeichert;

    service.festhalten(PROJECT, SPAETER);

    assertThat(festgehalten.gespeichert).isEqualTo(geschrieben);
  }

  @Test
  @DisplayName("Ein Vorhaben mit Dokument, aber ohne Paket bekommt noch keine Zeile")
  void ohnePaketKeineZeile() {
    eingefuehrt();
    zielStand(List.of(), List.of(anforderung(true, false)), false, List.of(401));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile()).isNull();
  }

  @Test
  @DisplayName("Hat ein festgehaltenes Vorhaben kein Paket mehr, bleibt seine Zeile unverändert")
  void festgehaltenOhnePaketBleibt() {
    eingefuehrt();
    festgehalten.speichere(
        VorhabenKosten.leer(ZIEL, PROJECT).mitAnforderung(beziffert(Art.ANFORDERUNG, "16")));
    VorhabenKosten vorher = zeile();
    int geschrieben = festgehalten.gespeichert;
    zielStand(List.of(), List.of(anforderung(true, false)), false, List.of(401));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile()).isEqualTo(vorher);
    assertThat(festgehalten.gespeichert).isEqualTo(geschrieben);
  }

  @Test
  @DisplayName("Ein festgehaltener Abschluss ohne tatsächliche Kosten wird nachgerechnet")
  void abschlussOhneTatsaechlicheKosten() {
    eingefuehrt();
    festgehalten.speichere(
        new VorhabenKosten(ZIEL, PROJECT, null, null, ABSCHLUSS, null, false, List.of(401, 402)));
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));
    kostetBisAbschluss(401, "10");
    kostetBisAbschluss(402, "8");

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().abgeschlossenAt()).isEqualTo(ABSCHLUSS);
    assertThat(zeile().tatsaechlichUsd()).isEqualByComparingTo("18");
  }

  @Test
  @DisplayName("Der Abschluss hält Zeitpunkt, tatsächliche Kosten bis dahin und Kostenkarten fest")
  void abschlussHaeltFest() {
    eingefuehrt();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));
    kostetBisAbschluss(401, "10");
    kostetBisAbschluss(402, "8");

    service.festhalten(PROJECT, JETZT);

    VorhabenKosten zeile = zeile();
    assertThat(zeile.abgeschlossenAt()).isEqualTo(JETZT);
    assertThat(zeile.tatsaechlichUsd()).isEqualByComparingTo("18");
    assertThat(zeile.tatsaechlichUnvollstaendig()).isFalse();
    assertThat(zeile.kostenKarten()).containsExactly(401, 402);
    verify(usage).itemKostenJeKarte(PROJECT, List.of(401, 402), JETZT);
  }

  @Test
  @DisplayName(
      "Fehlt beim Abschluss eine Kostenangabe, sind die tatsächlichen Kosten unvollständig")
  void abschlussMitFehlenderKostenangabe() {
    eingefuehrt();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));
    kostetBisAbschluss(401, "10");
    kostenBisAbschluss.put(
        402, new ItemKostenJeKarte(402, new BigDecimal("8"), 1L, VOR_EINER_WOCHE));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().tatsaechlichUnvollstaendig()).isTrue();
  }

  @Test
  @DisplayName("Begann das Vorhaben vor der Aufbewahrungsgrenze, sind die Kosten unvollständig")
  void abschlussVorDerGrenze() {
    eingefuehrt();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));
    kostetBisAbschluss(401, "10");
    aufbewahrt.add(new RetainedByKind(NightRunKind.NIGHT, 3L, VOR_EINER_WOCHE.plusSeconds(1)));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().tatsaechlichUnvollstaendig()).isTrue();
    assertThat(zeile().tatsaechlichUsd()).isEqualByComparingTo("10");
  }

  @Test
  @DisplayName("Öffnet sich das Vorhaben wieder, entfällt der Abschluss, die Voraussagen bleiben")
  void wiederOffen() {
    eingefuehrt();
    festgehaltenAbgeschlossen();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS), paket(403, null, null)),
        List.of(anforderung(false, true)),
        false,
        List.of(401, 402, 403));

    service.festhalten(PROJECT, JETZT);

    VorhabenKosten zeile = zeile();
    assertThat(zeile.abgeschlossenAt()).isNull();
    assertThat(zeile.tatsaechlichUsd()).isNull();
    assertThat(zeile.kostenKarten()).isEmpty();
    assertThat(zeile.anforderung()).isEqualTo(beziffert(Art.ANFORDERUNG, "16"));
    assertThat(zeile.pakete()).isEqualTo(beziffert(Art.PAKETE, "20"));
  }

  @Test
  @DisplayName("Der nächste Abschluss nach dem Wiederöffnen hält die Kosten neu fest")
  void wiederAbgeschlossen() {
    eingefuehrt();
    festgehaltenAbgeschlossen();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS), paket(403, null, null)),
        List.of(anforderung(false, true)),
        false,
        List.of(401, 402, 403));
    service.festhalten(PROJECT, JETZT);
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS), paket(403, null, JETZT)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402, 403));
    kostetBisAbschluss(401, "10");
    kostetBisAbschluss(402, "8");
    kostetBisAbschluss(403, "4");

    service.festhalten(PROJECT, SPAETER);

    VorhabenKosten zeile = zeile();
    assertThat(zeile.abgeschlossenAt()).isEqualTo(SPAETER);
    assertThat(zeile.tatsaechlichUsd()).isEqualByComparingTo("22");
    assertThat(zeile.kostenKarten()).containsExactly(401, 402, 403);
    assertThat(zeile.anforderung()).isEqualTo(beziffert(Art.ANFORDERUNG, "16"));
  }

  @Test
  @DisplayName(
      "Eine späte Meldung ergänzt die tatsächlichen Kosten, Abschluss und Voraussagen bleiben")
  void spaeteMeldung() {
    eingefuehrt();
    festgehaltenAbgeschlossen();
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));
    kostetBisAbschluss(401, "12");
    kostetBisAbschluss(402, "8");

    service.festhalten(PROJECT, JETZT);

    VorhabenKosten zeile = zeile();
    assertThat(zeile.tatsaechlichUsd()).isEqualByComparingTo("20");
    assertThat(zeile.abgeschlossenAt()).isEqualTo(ABSCHLUSS);
    assertThat(zeile.anforderung()).isEqualTo(beziffert(Art.ANFORDERUNG, "16"));
    verify(usage).itemKostenJeKarte(PROJECT, List.of(401, 402), ABSCHLUSS);
  }

  @Test
  @DisplayName("Umzuordnung und verdrängte Messungen nach dem Abschluss senken den Betrag nie")
  void umzuordnungNachAbschluss() {
    eingefuehrt();
    festgehaltenAbgeschlossen();
    // 402 gehört inzwischen zu einem anderen Vorhaben, 405 ist neu zugeordnet.
    zielStand(
        List.of(paket(405, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 405));
    kostetBisAbschluss(401, "10");
    kostetBisAbschluss(405, "50");

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().tatsaechlichUsd()).isEqualByComparingTo("18");
    assertThat(zeile().kostenKarten()).containsExactly(401, 402);
    verify(usage).itemKostenJeKarte(PROJECT, List.of(401, 402), ABSCHLUSS);
  }

  @Test
  @DisplayName(
      "Einführung: Bei laufenden Vorhaben ergeben vergangene Zeitpunkte „keine Voraussage“")
  void einfuehrungLaufend() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null)),
        List.of(anforderung(true, false)),
        false,
        List.of(401, 402));
    staende.add(
        new VorhabenStandView(
            ref(500L, 50),
            1L,
            List.of(paket(502, Aufgabenstufe.MITTEL, null)),
            List.of(new DokumentStandView(501, "FACHLICH", true, true)),
            true,
            true,
            false,
            List.of(501, 502),
            VOR_EINER_WOCHE));
    staende.add(
        new VorhabenStandView(
            ref(600L, 60),
            1L,
            List.of(),
            List.of(new DokumentStandView(601, "FACHLICH", true, false)),
            true,
            true,
            false,
            List.of(601),
            VOR_EINER_WOCHE));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().anforderung()).isEqualTo(FestgehalteneVoraussage.keine(JETZT));
    assertThat(zeile().pakete()).as("der Schnitt steht noch aus").isNull();
    assertThat(festgehalten.zeilen.get(500L).anforderung())
        .isEqualTo(FestgehalteneVoraussage.keine(JETZT));
    assertThat(festgehalten.zeilen.get(500L).pakete())
        .isEqualTo(FestgehalteneVoraussage.keine(JETZT));
    assertThat(festgehalten.zeilen).doesNotContainKey(600L);
    assertThat(festgehalten.eingefuehrtAm(PROJECT)).hasValue(JETZT);
  }

  @Test
  @DisplayName("Nach der Einführung wird ein laufendes Vorhaben ganz normal behandelt")
  void nachDerEinfuehrungNormal() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null)),
        List.of(anforderung(true, false)),
        false,
        List.of(401, 402));
    service.festhalten(PROJECT, JETZT);
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, null)),
        List.of(anforderung(true, true)),
        false,
        List.of(401, 402));

    service.festhalten(PROJECT, SPAETER);

    assertThat(zeile().anforderung()).isEqualTo(FestgehalteneVoraussage.keine(JETZT));
    assertThat(zeile().pakete().stand()).isEqualTo(Stand.BEZIFFERT);
    assertThat(zeile().pakete().festgehaltenAt()).isEqualTo(SPAETER);
    assertThat(festgehalten.eingefuehrtAm(PROJECT)).hasValue(JETZT);
  }

  @Test
  @DisplayName("Einführung: Ein abgeschlossenes Vorhaben gilt beim spätesten Done seiner Pakete")
  void einfuehrungAbgeschlossen() {
    Instant frueh = JETZT.minus(Duration.ofDays(20));
    Instant spaet = JETZT.minus(Duration.ofDays(10));
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, spaet), paket(403, Aufgabenstufe.MITTEL, frueh)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402, 403));
    kostetBisAbschluss(401, "5");
    kostetBisAbschluss(402, "4");
    kostetBisAbschluss(403, "3");

    service.festhalten(PROJECT, JETZT);

    VorhabenKosten zeile = zeile();
    assertThat(zeile.abgeschlossenAt()).isEqualTo(spaet);
    assertThat(zeile.tatsaechlichUsd()).isEqualByComparingTo("12");
    assertThat(zeile.anforderung()).isEqualTo(FestgehalteneVoraussage.keine(JETZT));
    assertThat(zeile.pakete()).isEqualTo(FestgehalteneVoraussage.keine(JETZT));
    verify(usage).itemKostenJeKarte(PROJECT, List.of(401, 402, 403), spaet);
  }

  @Test
  @DisplayName("Einführung: Das späteste Done zählt auch, wenn es das letzte Paket trägt")
  void einfuehrungSpaetestesZuletzt() {
    Instant frueh = JETZT.minus(Duration.ofDays(20));
    Instant spaet = JETZT.minus(Duration.ofDays(10));
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, frueh), paket(403, Aufgabenstufe.MITTEL, spaet)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402, 403));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().abgeschlossenAt()).isEqualTo(spaet);
  }

  @Test
  @DisplayName("Einführung: Fehlt ein Done-Zeitpunkt, gilt der Zeitpunkt des Laufs")
  void einfuehrungOhneDoneZeitpunkt() {
    zielStand(
        List.of(new PaketStandView(402, true, Aufgabenstufe.MITTEL, VOR_EINER_WOCHE, null)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));

    service.festhalten(PROJECT, JETZT);

    assertThat(zeile().abgeschlossenAt()).isEqualTo(JETZT);
  }

  @Test
  @DisplayName("Die Projekte mit Vorhaben kommen aus der Kartenfassade")
  void projekteMitVorhaben() {
    when(cards.projekteMitVorhaben()).thenReturn(List.of(3L, 7L));

    assertThat(service.projekteMitVorhaben()).containsExactly(3L, 7L);
  }

  // --- Aufrufe der Kostenzeile (Issue #2005) ---------------------------------------------------

  @Test
  @DisplayName("Je Board: alle Vorhaben dieses Boards, archivierte eingeschlossen, fremde nicht")
  void jeBoardAlleVorhabenDesBoards() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    staende.add(
        new VorhabenStandView(
            new VorhabenRef(500L, 500, "ARCH", "Archiviert", true),
            BOARD,
            List.of(),
            List.of(),
            false,
            false,
            false,
            List.of(),
            null));
    staende.add(
        new VorhabenStandView(
            ref(600L, 600), 2L, List.of(), List.of(), false, false, false, List.of(), null));

    List<VorhabenKostenView> views = service.jeBoard(USER, BOARD, JETZT);

    assertThat(views)
        .extracting(VorhabenKostenView::epicId)
        .containsExactly(100L, 200L, 300L, ZIEL, 500L);
    VorhabenKostenView archiviert = views.getLast();
    assertThat(archiviert.archiviert()).isTrue();
    assertThat(archiviert.number()).isEqualTo(500);
    verify(permissions).requireNightRunAccess(USER, PROJECT);
  }

  @Test
  @DisplayName("Je Board: Ein Mitglied ohne Recht auf den Verbrauch bekommt 403, ohne Abfrage")
  void jeBoardMitgliedOhneVerbrauchsrecht() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.jeBoard(USER, BOARD, JETZT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cards, never()).vorhabenStaende(anyLong());
  }

  @Test
  @DisplayName("Je Board: Ein Nichtmitglied bekommt 404")
  void jeBoardNichtmitglied() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.jeBoard(USER, BOARD, JETZT))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(cards, never()).vorhabenStaende(anyLong());
  }

  @Test
  @DisplayName(
      "Je Vorhaben: Owner sieht die laufende Voraussage samt bisherigen Kosten und Hinweis")
  void jeVorhabenLaufend() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielNurAnforderung();
    // 0,50 $ aus Items, deren Lauf schon verdrängt ist: bisher 0,50 $, keine Stufe begonnen.
    misst(401, "0.50");

    VorhabenKostenView view = service.jeVorhaben(USER, ZIEL, JETZT);

    assertThat(view.epicId()).isEqualTo(ZIEL);
    assertThat(view.number()).isEqualTo(400);
    assertThat(view.archiviert()).isFalse();
    assertThat(view.abschluss()).isNull();
    LaufendView laufend = view.laufend();
    assertThat(laufend).isNotNull();
    assertThat(laufend.hatDokumentOderPaket()).isTrue();
    assertThat(laufend.bisher()).isEqualByComparingTo("0.50");
    assertThat(laufend.unvollstaendig()).isFalse();
    assertThat(laufend.zuWenigDaten()).isNull();
    assertThat(laufend.voraussage()).isNotNull();
    assertThat(laufend.voraussage().art()).isEqualTo(Art.ANFORDERUNG);
    assertThat(laufend.voraussage().typisch()).isEqualByComparingTo("7");
    assertThat(laufend.voraussage().unten()).isEqualByComparingTo("7");
    assertThat(laufend.voraussage().oben()).isEqualByComparingTo("7");
    assertThat(laufend.voraussage().paketzahl()).isEqualTo(1);
    assertThat(laufend.voraussage().ohneStufe()).isZero();
    verify(cards).requireProjectId(ZIEL);
    verify(permissions).requireNightRunAccess(USER, PROJECT);
  }

  @Test
  @DisplayName("Je Vorhaben: Zu wenig Daten nennt den Grund, die bisherigen Kosten bleiben")
  void jeVorhabenZuWenigDaten() {
    grundlage(10);
    zielNurAnforderung();
    kosten.put(401, new ItemKostenJeKarte(401, new BigDecimal("0.75"), 1L, VOR_EINER_WOCHE));

    LaufendView laufend = service.jeVorhaben(USER, ZIEL, JETZT).laufend();

    assertThat(laufend).isNotNull();
    assertThat(laufend.voraussage()).isNull();
    assertThat(laufend.zuWenigDaten()).isEqualTo(Grund.ZU_WENIG_VORHABEN);
    assertThat(laufend.bisher()).isEqualByComparingTo("0.75");
    assertThat(laufend.unvollstaendig()).isTrue();
  }

  @Test
  @DisplayName("Je Vorhaben: Ein Paket ohne Dokument genügt für die Voraussage")
  void jeVorhabenNurPaket() {
    grundlage(10);
    grundlage(20);
    grundlage(30);
    zielStand(List.of(paket(402, Aufgabenstufe.MITTEL, null)), List.of(), false, List.of(402));

    LaufendView laufend = service.jeVorhaben(USER, ZIEL, JETZT).laufend();

    assertThat(laufend).isNotNull();
    assertThat(laufend.hatDokumentOderPaket()).isTrue();
    assertThat(laufend.voraussage()).isNotNull();
  }

  @Test
  @DisplayName("Je Vorhaben: Ohne Dokument und Paket gibt es weder Voraussage noch Grund")
  void jeVorhabenOhneDokumentUndPaket() {
    staende.add(
        new VorhabenStandView(
            ref(ZIEL, 400), BOARD, List.of(), List.of(), false, false, false, List.of(), null));

    LaufendView laufend = service.jeVorhaben(USER, ZIEL, JETZT).laufend();

    assertThat(laufend).isNotNull();
    assertThat(laufend.hatDokumentOderPaket()).isFalse();
    assertThat(laufend.voraussage()).isNull();
    assertThat(laufend.zuWenigDaten()).isNull();
    assertThat(laufend.bisher()).isEqualByComparingTo("0");
  }

  @Test
  @DisplayName("Je Vorhaben: Abgeschlossen trägt es die tatsächlichen Kosten und beide Vergleiche")
  void jeVorhabenAbgeschlossen() {
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));
    festgehalten.speichere(
        VorhabenKosten.leer(ZIEL, PROJECT)
            .mitAnforderung(beziffert(Art.ANFORDERUNG, "16"))
            .mitPaketen(FestgehalteneVoraussage.keine(VOR_EINER_WOCHE))
            .mitAbschluss(ABSCHLUSS, new BigDecimal("18"), false, List.of(401, 402)));

    VorhabenKostenView view = service.jeVorhaben(USER, ZIEL, JETZT);

    assertThat(view.laufend()).isNull();
    AbschlussView abschluss = view.abschluss();
    assertThat(abschluss).isNotNull();
    assertThat(abschluss.abgeschlossenAt()).isEqualTo(ABSCHLUSS);
    assertThat(abschluss.tatsaechlich()).isEqualByComparingTo("18");
    assertThat(abschluss.unvollstaendig()).isFalse();
    VergleichView anforderung = abschluss.anforderung();
    assertThat(anforderung.beziffert()).isTrue();
    assertThat(anforderung.art()).isEqualTo(Art.ANFORDERUNG);
    assertThat(anforderung.typisch()).isEqualByComparingTo("16");
    assertThat(anforderung.unten()).isEqualByComparingTo("16");
    assertThat(anforderung.oben()).isEqualByComparingTo("16");
    assertThat(anforderung.abweichungProzent()).isEqualTo(13);
    VergleichView pakete = abschluss.pakete();
    assertThat(pakete.beziffert()).isFalse();
    assertThat(pakete.art()).isNull();
    assertThat(pakete.typisch()).isNull();
    assertThat(pakete.abweichungProzent()).isNull();
  }

  @Test
  @DisplayName("Je Vorhaben: Unvollständige tatsächliche Kosten tragen keine Abweichung")
  void jeVorhabenAbgeschlossenUnvollstaendig() {
    zielStand(
        List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
        List.of(anforderung(false, true)),
        true,
        List.of(401, 402));
    festgehalten.speichere(
        VorhabenKosten.leer(ZIEL, PROJECT)
            .mitAbschluss(ABSCHLUSS, new BigDecimal("18"), true, List.of(401, 402)));

    AbschlussView abschluss = service.jeVorhaben(USER, ZIEL, JETZT).abschluss();

    assertThat(abschluss).isNotNull();
    assertThat(abschluss.unvollstaendig()).isTrue();
    // Nie festgehaltene Voraussagen stehen als „keine Voraussage“ da.
    assertThat(abschluss.anforderung().beziffert()).isFalse();
    assertThat(abschluss.pakete().beziffert()).isFalse();
    assertThat(abschluss.anforderung().abweichungProzent()).isNull();
  }

  @Test
  @DisplayName("Je Vorhaben: Ein archiviertes abgeschlossenes Vorhaben zeigt seinen Vergleich")
  void jeVorhabenArchiviert() {
    staende.add(
        new VorhabenStandView(
            new VorhabenRef(ZIEL, 400, null, "Export", true),
            BOARD,
            List.of(paket(402, Aufgabenstufe.MITTEL, ABSCHLUSS)),
            List.of(anforderung(false, true)),
            true,
            true,
            true,
            List.of(401, 402),
            VOR_EINER_WOCHE));
    festgehalten.speichere(
        VorhabenKosten.leer(ZIEL, PROJECT)
            .mitPaketen(beziffert(Art.PAKETE, "20"))
            .mitAbschluss(ABSCHLUSS, new BigDecimal("18"), false, List.of(401, 402)));

    VorhabenKostenView view = service.jeVorhaben(USER, ZIEL, JETZT);

    assertThat(view.archiviert()).isTrue();
    assertThat(view.abschluss()).isNotNull();
    assertThat(view.abschluss().pakete().abweichungProzent()).isEqualTo(-10);
  }

  @Test
  @DisplayName("Je Vorhaben: Eine festgehaltene Zeile ohne Abschluss zeigt die laufende Voraussage")
  void jeVorhabenFestgehaltenOhneAbschluss() {
    zielNurAnforderung();
    festgehalten.speichere(
        VorhabenKosten.leer(ZIEL, PROJECT).mitAnforderung(beziffert(Art.ANFORDERUNG, "16")));

    VorhabenKostenView view = service.jeVorhaben(USER, ZIEL, JETZT);

    assertThat(view.abschluss()).isNull();
    assertThat(view.laufend()).isNotNull();
    // Ein Abschluss ohne tatsächliche Kosten gilt ebenso wenig als Vergleich.
    festgehalten.speichere(
        new VorhabenKosten(ZIEL, PROJECT, null, null, ABSCHLUSS, null, false, List.of()));

    assertThat(service.jeVorhaben(USER, ZIEL, JETZT).abschluss()).isNull();
  }

  @Test
  @DisplayName("Je Vorhaben: Eine Karte, die kein Vorhaben ist, ergibt 404")
  void jeVorhabenKeinVorhaben() {
    zielNurAnforderung();

    assertThatThrownBy(() -> service.jeVorhaben(USER, 401L, JETZT))
        .isInstanceOf(VorhabenNotFoundException.class);
  }

  @Test
  @DisplayName("Je Vorhaben: Mitglied ohne Verbrauchsrecht 403, Nichtmitglied 404, ohne Abfrage")
  void jeVorhabenRechte() {
    doThrow(new ProjectAccessDeniedException())
        .doThrow(new ProjectNotFoundException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.jeVorhaben(USER, ZIEL, JETZT))
        .isInstanceOf(ProjectAccessDeniedException.class);
    assertThatThrownBy(() -> service.jeVorhaben(USER, ZIEL, JETZT))
        .isInstanceOf(ProjectNotFoundException.class);
    verify(cards, never()).vorhabenStaende(anyLong());
  }

  /** Ein Speicher im Arbeitsspeicher für ein einziges Projekt. */
  private static final class FesteKosten implements VorhabenKostenRepository {

    private final Map<Long, VorhabenKosten> zeilen = new TreeMap<>();
    private @Nullable Instant eingefuehrt;
    private int gespeichert;

    @Override
    public List<VorhabenKosten> findByProjectId(long projectId) {
      return zeilen.values().stream().filter(z -> z.projectId() == projectId).toList();
    }

    @Override
    public void speichere(VorhabenKosten kosten) {
      gespeichert++;
      zeilen.put(kosten.epicId(), kosten);
    }

    @Override
    public Optional<Instant> eingefuehrtAm(long projectId) {
      return Optional.ofNullable(eingefuehrt);
    }

    @Override
    public void einfuehren(long projectId, Instant am) {
      if (eingefuehrt == null) {
        eingefuehrt = am;
      }
    }
  }
}
