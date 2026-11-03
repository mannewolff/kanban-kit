package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.ABDECKUNG;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.PAKETE;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.PLAN;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.REVIEW;
import static org.mwolff.manban.nightrun.domain.NightRunAttemptStage.UMSETZUNG;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Art;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Eingaben;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Ergebnis;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Grund;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.GrundlageVorhaben;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Paket;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Paketstufe;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Preise;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Rechnung;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Voraussage;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.Vorhaben;
import org.mwolff.manban.nightrun.domain.KostenVoraussage.ZuWenigDaten;

/**
 * Die Rechnung der Kostenvoraussage (Issue #2003, Plan #1990 E3, E4, E6, E7, E12; fachlich #1954 AK
 * 4 bis 7): beide Stufen, der Abzug des Angefallenen, die Ersatzpreise und die Datenlücken.
 */
@SuppressWarnings("PMD.TooManyMethods")
class KostenVoraussageTest {

  /** Je Stufe genau ein Vorgang — eine Grundlage, die jede Dokumentstufe trägt. */
  private static final Map<NightRunAttemptStage, Integer> JE_STUFE_EINER =
      Map.of(PLAN, 1, REVIEW, 1, PAKETE, 1, ABDECKUNG, 1);

  private static BigDecimal usd(String betrag) {
    return new BigDecimal(betrag);
  }

  private static List<BigDecimal> preise(String... betraege) {
    return Arrays.stream(betraege).map(BigDecimal::new).toList();
  }

  private static GrundlageVorhaben grundlage(int paketzahl) {
    return new GrundlageVorhaben(JE_STUFE_EINER, paketzahl);
  }

  /** Drei Grundlage-Vorhaben mit je einem Vorgang je Stufe und sechs Paketen. */
  private static List<GrundlageVorhaben> dreiMitSechs() {
    return List.of(grundlage(6), grundlage(6), grundlage(6));
  }

  /** Je Stufe 1 $, Plan 2 $ — zusammen 5 $; jedes Paket 1,50 $, ohne Preis je Aufgabenstufe. */
  private static Preise preiseFuenfUndAnderthalb() {
    return new Preise(
        Map.of(PLAN, preise("2"), REVIEW, preise("1"), PAKETE, preise("1"), ABDECKUNG, preise("1")),
        Map.of(),
        preise("1.50"));
  }

  private static Vorhaben nurAnforderung() {
    return new Vorhaben(true, true, Map.of(), List.of(), BigDecimal.ZERO, false);
  }

  private static Vorhaben geschnitten(
      Map<NightRunAttemptStage, BigDecimal> angefallen, List<Paket> pakete, String bisher) {
    return new Vorhaben(true, false, angefallen, pakete, usd(bisher), false);
  }

  private static Paket paket(int nummer, @Nullable Paketstufe stufe) {
    return new Paket(nummer, false, stufe, BigDecimal.ZERO);
  }

  private static Voraussage voraussage(Ergebnis ergebnis) {
    assertThat(ergebnis).isInstanceOf(Voraussage.class);
    return (Voraussage) ergebnis;
  }

  @Test
  @DisplayName("Szenario: Voraussage aus der Anforderung")
  void szenarioVoraussageAusDerAnforderung() {
    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG,
                    nurAnforderung(),
                    dreiMitSechs(),
                    preiseFuenfUndAnderthalb())));

    assertThat(v.art()).isEqualTo(Art.ANFORDERUNG);
    assertThat(v.typisch()).isEqualByComparingTo("14");
    assertThat(v.bisher()).isEqualByComparingTo("0");
    assertThat(v.paketzahl()).isEqualTo(6);
    assertThat(v.ohneStufe()).isZero();
    assertThat(v.unvollstaendig()).isFalse();
  }

  @Test
  @DisplayName("Szenario: Voraussage aus den Paketen")
  void szenarioVoraussageAusDenPaketen() {
    List<Paket> acht =
        List.of(1, 2, 3, 4, 5, 6, 7, 8).stream().map(n -> paket(n, Paketstufe.MITTEL)).toList();
    Preise preise =
        new Preise(
            Map.of(
                PLAN,
                preise("2"),
                REVIEW,
                preise("1"),
                PAKETE,
                preise("1"),
                ABDECKUNG,
                preise("1")),
            Map.of(Paketstufe.MITTEL, preise("2")),
            preise("1.50"));

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.PAKETE,
                    geschnitten(
                        Map.of(PLAN, usd("1"), REVIEW, usd("2"), PAKETE, usd("1")), acht, "4"),
                    dreiMitSechs(),
                    preise)));

    // Plan und Prüfung sind durchlaufen, der Schnitt ist mit 1 $ voll angefallen, die Abdeckung
    // steht aus (1 $), acht mittlere Pakete zu je 2 $ — dazu die bisherigen 4 $.
    assertThat(v.art()).isEqualTo(Art.PAKETE);
    assertThat(v.paketzahl()).isEqualTo(8);
    assertThat(v.bisher()).isEqualByComparingTo("4");
    assertThat(v.typisch()).isEqualByComparingTo("21");
  }

  @Test
  @DisplayName("Szenario: Zu wenig Daten")
  void szenarioZuWenigDaten() {
    Ergebnis ergebnis =
        KostenVoraussage.rechne(
            new Eingaben(
                Rechnung.ANFORDERUNG, nurAnforderung(), List.of(), preiseFuenfUndAnderthalb()));

    assertThat(ergebnis).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("AK 4: Die Spanne rechnet je Grundlage mit ihrem 15- und 85-%-Wert")
  void spanneJeGrundlage() {
    Preise preise =
        new Preise(
            Map.of(
                PLAN,
                preise("1", "2", "3"),
                REVIEW,
                preise("1"),
                PAKETE,
                preise("1"),
                ABDECKUNG,
                preise("1")),
            Map.of(),
            preise("1", "1.5", "2"));

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG,
                    nurAnforderung(),
                    List.of(grundlage(4), grundlage(6), grundlage(8)),
                    preise)));

    // Plan 1,3 / 2 / 2,7; die übrigen Stufen je 1; Pakete 4,6 × 1,15 / 6 × 1,5 / 7,4 × 1,85.
    assertThat(v.unten()).isEqualByComparingTo("9.59");
    assertThat(v.typisch()).isEqualByComparingTo("14");
    assertThat(v.oben()).isEqualByComparingTo("19.39");
    assertThat(v.paketzahl()).isEqualTo(6);
  }

  @Test
  @DisplayName("AK 4: Die Beträge sind auf Cent gerundet, kaufmännisch")
  void aufCentGerundet() {
    Preise preise =
        new Preise(
            Map.of(
                PLAN,
                preise("0.005"),
                REVIEW,
                preise("0"),
                PAKETE,
                preise("0"),
                ABDECKUNG,
                preise("0")),
            Map.of(),
            preise("0"));

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG,
                    new Vorhaben(true, true, Map.of(), List.of(), usd("0.004"), false),
                    dreiMitSechs(),
                    preise)));

    assertThat(v.typisch()).isEqualTo(usd("0.01"));
    assertThat(v.bisher()).isEqualTo(usd("0.00"));
  }

  @Test
  @DisplayName("AK 4: Ohne Anforderung heißt die erste Stufe „vor dem Schnitt“")
  void ohneAnforderungVorDemSchnitt() {
    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG,
                    new Vorhaben(false, true, Map.of(), List.of(), BigDecimal.ZERO, false),
                    dreiMitSechs(),
                    preiseFuenfUndAnderthalb())));

    assertThat(v.art()).isEqualTo(Art.VOR_DEM_SCHNITT);
    assertThat(v.typisch()).isEqualByComparingTo("14");
  }

  @Test
  @DisplayName("AK 4: Mit bekannten Paketen gilt die typische Paketzahl, mindestens die bekannte")
  void ersteStufeMitBekanntenPaketen() {
    // Zwei bekannte Pakete: eines in Done (nur bisher), eines schwer zu 3 $; dazu vier erwartete
    // zu je 1,50 $.
    Preise preise =
        new Preise(
            preiseFuenfUndAnderthalb().jeStufe(),
            Map.of(Paketstufe.SCHWER, preise("3")),
            preise("1.50"));
    Vorhaben vorhaben =
        new Vorhaben(
            true,
            true,
            Map.of(),
            List.of(new Paket(1, true, Paketstufe.SCHWER, usd("2")), paket(2, Paketstufe.SCHWER)),
            usd("2"),
            false);

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.ANFORDERUNG, vorhaben, dreiMitSechs(), preise)));

    assertThat(v.typisch()).isEqualByComparingTo("16");
    assertThat(v.paketzahl()).isEqualTo(6);
  }

  @Test
  @DisplayName("AK 4: Hat das Vorhaben mehr Pakete als typisch, zählen nur die bekannten")
  void ersteStufeMehrBekannteAlsTypisch() {
    List<Paket> acht =
        List.of(1, 2, 3, 4, 5, 6, 7, 8).stream().map(n -> paket(n, Paketstufe.LEICHT)).toList();
    Preise preise =
        new Preise(
            preiseFuenfUndAnderthalb().jeStufe(),
            Map.of(Paketstufe.LEICHT, preise("1")),
            preise("1.50"));

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG,
                    new Vorhaben(true, true, Map.of(), acht, BigDecimal.ZERO, false),
                    dreiMitSechs(),
                    preise)));

    assertThat(v.typisch()).isEqualByComparingTo("13");
    assertThat(v.paketzahl()).isEqualTo(8);
  }

  @Test
  @DisplayName("AK 4: Eine halbe typische Paketzahl rundet in der Zeile auf")
  void paketzahlGerundet() {
    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG,
                    nurAnforderung(),
                    List.of(grundlage(5), grundlage(6), grundlage(5), grundlage(6)),
                    preiseFuenfUndAnderthalb())));

    // Typisch 5,5 Pakete zu 1,50 $ sind 8,25 $ — gerechnet wird ungerundet.
    assertThat(v.typisch()).isEqualByComparingTo("13.25");
    assertThat(v.paketzahl()).isEqualTo(6);
  }

  @Test
  @DisplayName("AK 6: Eine begonnene Stufe schätzt den Vergleichspreis abzüglich des Angefallenen")
  void begonneneStufeAbzueglich() {
    Vorhaben vorhaben =
        new Vorhaben(true, true, Map.of(PLAN, usd("0.50")), List.of(), usd("0.50"), false);

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG, vorhaben, dreiMitSechs(), preiseFuenfUndAnderthalb())));

    // Plan 2 $ − 0,50 $ = 1,50 $, die Voraussage nennt das ganze Vorhaben: 14 $.
    assertThat(v.typisch()).isEqualByComparingTo("14");
    assertThat(v.bisher()).isEqualByComparingTo("0.50");
  }

  @Test
  @DisplayName("AK 6: Eine begonnene Stufe, die teurer wurde als ihr Vergleichspreis, schätzt 0")
  void begonneneStufeTeurerAlsVergleich() {
    Vorhaben vorhaben =
        new Vorhaben(true, true, Map.of(PLAN, usd("3")), List.of(), usd("3"), false);

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG, vorhaben, dreiMitSechs(), preiseFuenfUndAnderthalb())));

    assertThat(v.typisch()).isEqualByComparingTo("15");
  }

  @Test
  @DisplayName("E7: Solange ein Dokument ungeschnitten offen ist, ist keine Stufe durchlaufen")
  void ungeschnittenOffenKeineStufeDurchlaufen() {
    Vorhaben vorhaben =
        new Vorhaben(
            true,
            true,
            Map.of(PLAN, usd("2"), REVIEW, usd("1"), PAKETE, usd("1")),
            List.of(),
            usd("4"),
            false);

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG, vorhaben, dreiMitSechs(), preiseFuenfUndAnderthalb())));

    // Plan, Prüfung, Schnitt voll angefallen (je 0), Abdeckung 1 $, 6 × 1,50 $, bisher 4 $.
    assertThat(v.typisch()).isEqualByComparingTo("14");
  }

  @Test
  @DisplayName("E7: Eine durchlaufene Stufe braucht keinen Preis — die begonnene Umsetzung zählt")
  void durchlaufeneStufeOhnePreis() {
    Preise nurPakete = new Preise(Map.of(), Map.of(), preise("1.50"));
    Vorhaben vorhaben =
        geschnitten(
            Map.of(PLAN, usd("1"), UMSETZUNG, usd("1")),
            List.of(new Paket(1, true, null, usd("1")), paket(2, null)),
            "2");

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), nurPakete)));

    assertThat(v.typisch()).isEqualByComparingTo("3.50");
    assertThat(v.ohneStufe()).isEqualTo(1);
  }

  @Test
  @DisplayName("E7: Ungeschnitten offen bleibt eine Stufe offen, auch wenn eine spätere begann")
  void ungeschnittenOffenSpaetereStufeBegonnen() {
    Vorhaben vorhaben =
        new Vorhaben(true, true, Map.of(REVIEW, usd("1")), List.of(), usd("1"), false);

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG, vorhaben, dreiMitSechs(), preiseFuenfUndAnderthalb())));

    // Der Plan zählt trotz begonnener Prüfung mit 2 $: 2 + 0 + 1 + 1 + 6 × 1,50 $, bisher 1 $.
    assertThat(v.typisch()).isEqualByComparingTo("14");
  }

  @Test
  @DisplayName("E7: Eine begonnene Stufe ohne spätere ist nicht durchlaufen")
  void begonneneStufeOhneSpaetereNichtDurchlaufen() {
    Vorhaben vorhaben = geschnitten(Map.of(PLAN, usd("0.50")), List.of(), "0.50");

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG, vorhaben, dreiMitSechs(), preiseFuenfUndAnderthalb())));

    // Plan 2 $ − 0,50 $ = 1,50 $, dazu 1 + 1 + 1 + 6 × 1,50 $, bisher 0,50 $.
    assertThat(v.typisch()).isEqualByComparingTo("14");
  }

  @Test
  @DisplayName("AK 5: Pakete in Done stehen nur in den bisherigen Kosten")
  void zweiteStufeDoneNurBisher() {
    Preise preise = new Preise(Map.of(), Map.of(Paketstufe.LEICHT, preise("1")), preise("5"));
    Vorhaben vorhaben =
        geschnitten(
            Map.of(ABDECKUNG, usd("1"), UMSETZUNG, usd("2")),
            List.of(new Paket(1, true, Paketstufe.LEICHT, usd("2")), paket(2, Paketstufe.LEICHT)),
            "3");

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), preise)));

    // Abdeckung durchlaufen (Umsetzung begonnen); nur das offene leichte Paket zu 1 $ kommt dazu.
    assertThat(v.typisch()).isEqualByComparingTo("4");
    assertThat(v.paketzahl()).isEqualTo(2);
  }

  @Test
  @DisplayName("AK 6: Ein begonnenes Paket, teurer als sein Vergleichspreis, schätzt 0")
  void begonnenesPaketTeurerAlsVergleich() {
    Preise preise = new Preise(Map.of(), Map.of(Paketstufe.MITTEL, preise("2")), preise("2"));
    Vorhaben vorhaben =
        geschnitten(
            Map.of(UMSETZUNG, usd("3")),
            List.of(new Paket(1, false, Paketstufe.MITTEL, usd("3"))),
            "3");

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), preise)));

    assertThat(v.typisch()).isEqualByComparingTo("3");
    assertThat(v.unten()).isEqualByComparingTo("3");
    assertThat(v.oben()).isEqualByComparingTo("3");
  }

  @Test
  @DisplayName("AK 6: Ein begonnenes Paket schätzt seinen Vergleichspreis abzüglich")
  void begonnenesPaketAbzueglich() {
    Preise preise = new Preise(Map.of(), Map.of(Paketstufe.MITTEL, preise("2")), preise("2"));
    Vorhaben vorhaben =
        geschnitten(
            Map.of(UMSETZUNG, usd("0.50")),
            List.of(new Paket(1, false, Paketstufe.MITTEL, usd("0.50"))),
            "0.50");

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), preise)));

    assertThat(v.typisch()).isEqualByComparingTo("2");
  }

  @Test
  @DisplayName("AK 7: Ein Paket ohne Aufgabenstufe rechnet mit dem Preis über alle Stufen")
  void paketOhneStufe() {
    Preise preise = new Preise(Map.of(), Map.of(Paketstufe.SCHWER, preise("4")), preise("1.50"));
    Vorhaben vorhaben =
        geschnitten(
            Map.of(UMSETZUNG, BigDecimal.ZERO),
            List.of(paket(1, null), paket(2, Paketstufe.SCHWER)),
            "0");

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), preise)));

    assertThat(v.typisch()).isEqualByComparingTo("5.50");
    assertThat(v.ohneStufe()).isEqualTo(1);
  }

  @Test
  @DisplayName("AK 7: Fehlt nur der Preis einer Aufgabenstufe, gilt der Preis über alle Stufen")
  void stufeOhnePaketpreis() {
    Preise preise = new Preise(Map.of(), Map.of(Paketstufe.SCHWER, preise("4")), preise("1.50"));
    Vorhaben vorhaben =
        geschnitten(Map.of(UMSETZUNG, BigDecimal.ZERO), List.of(paket(1, Paketstufe.LEICHT)), "0");

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), preise)));

    assertThat(v.typisch()).isEqualByComparingTo("1.50");
    assertThat(v.ohneStufe()).isEqualTo(1);
  }

  @Test
  @DisplayName("AK 7: Zwei Grundlage-Vorhaben reichen nicht")
  void zweiGrundlageVorhaben() {
    Ergebnis ergebnis =
        KostenVoraussage.rechne(
            new Eingaben(
                Rechnung.ANFORDERUNG,
                nurAnforderung(),
                List.of(grundlage(6), grundlage(6)),
                preiseFuenfUndAnderthalb()));

    assertThat(ergebnis).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("E3: Ein Grundlage-Vorhaben ohne Vorgang in einer Stufe zählt nicht")
  void grundlageOhneVorgangInEinerStufe() {
    Map<NightRunAttemptStage, Integer> ohneAbdeckung = new EnumMap<>(JE_STUFE_EINER);
    ohneAbdeckung.remove(ABDECKUNG);
    Map<NightRunAttemptStage, Integer> abdeckungNull = new EnumMap<>(JE_STUFE_EINER);
    abdeckungNull.put(ABDECKUNG, 0);
    List<GrundlageVorhaben> grundlage =
        List.of(
            grundlage(6),
            grundlage(6),
            new GrundlageVorhaben(ohneAbdeckung, 6),
            new GrundlageVorhaben(abdeckungNull, 6));

    Ergebnis ergebnis =
        KostenVoraussage.rechne(
            new Eingaben(
                Rechnung.ANFORDERUNG, nurAnforderung(), grundlage, preiseFuenfUndAnderthalb()));

    assertThat(ergebnis).isEqualTo(new ZuWenigDaten(Grund.ZU_WENIG_VORHABEN));
  }

  @Test
  @DisplayName("AK 7: Die Vorgangszahl einer Stufe ist das Quantil über die Grundlage")
  void vorgangszahlJeStufe() {
    Map<NightRunAttemptStage, Integer> dreiPruefungen = new EnumMap<>(JE_STUFE_EINER);
    dreiPruefungen.put(REVIEW, 3);
    List<GrundlageVorhaben> grundlage =
        List.of(
            new GrundlageVorhaben(dreiPruefungen, 6),
            new GrundlageVorhaben(dreiPruefungen, 6),
            grundlage(6));

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(
                    Rechnung.ANFORDERUNG,
                    nurAnforderung(),
                    grundlage,
                    preiseFuenfUndAnderthalb())));

    // Typisch drei Prüfungen zu je 1 $ statt einer.
    assertThat(v.typisch()).isEqualByComparingTo("16");
  }

  @Test
  @DisplayName("AK 7: Modellwechsel ohne Vorgang — die Stufe hat keinen Preis")
  void modellwechselOhneVorgang() {
    Preise ohnePruefung =
        new Preise(
            Map.of(
                PLAN, preise("2"), REVIEW, List.of(), PAKETE, preise("1"), ABDECKUNG, preise("1")),
            Map.of(),
            preise("1.50"));

    Ergebnis ergebnis =
        KostenVoraussage.rechne(
            new Eingaben(Rechnung.ANFORDERUNG, nurAnforderung(), dreiMitSechs(), ohnePruefung));

    assertThat(ergebnis).isEqualTo(new ZuWenigDaten(Grund.STUFE_OHNE_PREIS));
  }

  @Test
  @DisplayName("AK 7: Ohne jeden Paketpreis gibt es keine Voraussage")
  void ohnePaketpreise() {
    Preise ohnePakete = new Preise(preiseFuenfUndAnderthalb().jeStufe(), Map.of(), List.of());

    Ergebnis ergebnis =
        KostenVoraussage.rechne(
            new Eingaben(Rechnung.ANFORDERUNG, nurAnforderung(), dreiMitSechs(), ohnePakete));

    assertThat(ergebnis).isEqualTo(new ZuWenigDaten(Grund.KEINE_PAKETPREISE));
  }

  @Test
  @DisplayName("AK 7: Ein offenes Paket ohne Paketpreise gibt keine Voraussage")
  void offenesPaketOhnePaketpreise() {
    Preise ohnePakete = new Preise(Map.of(), Map.of(Paketstufe.LEICHT, preise("1")), List.of());
    Vorhaben vorhaben =
        geschnitten(Map.of(UMSETZUNG, BigDecimal.ZERO), List.of(paket(1, Paketstufe.LEICHT)), "0");

    Ergebnis ergebnis =
        KostenVoraussage.rechne(
            new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), ohnePakete));

    assertThat(ergebnis).isEqualTo(new ZuWenigDaten(Grund.KEINE_PAKETPREISE));
  }

  @Test
  @DisplayName("AK 7: Was nicht mehr geschätzt wird, braucht keine Grundlage")
  void nichtsMehrZuSchaetzen() {
    Preise leer = new Preise(Map.of(), Map.of(), List.of());
    Vorhaben vorhaben =
        new Vorhaben(
            true,
            false,
            Map.of(UMSETZUNG, usd("2")),
            List.of(new Paket(1, true, null, usd("2"))),
            usd("7"),
            true);

    Voraussage v =
        voraussage(
            KostenVoraussage.rechne(new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), leer)));

    assertThat(v.typisch()).isEqualByComparingTo("7");
    assertThat(v.unten()).isEqualByComparingTo("7");
    assertThat(v.oben()).isEqualByComparingTo("7");
    assertThat(v.ohneStufe()).isZero();
    assertThat(v.unvollstaendig()).isTrue();
  }

  @Test
  @DisplayName("E12: Die Art ist eine Eingabe — derselbe Stand rechnet beide Stufen")
  void artAlsEingabe() {
    Preise preise =
        new Preise(
            preiseFuenfUndAnderthalb().jeStufe(),
            Map.of(Paketstufe.MITTEL, preise("2")),
            preise("1.50"));
    Vorhaben vorhaben =
        geschnitten(
            Map.of(PLAN, usd("2"), REVIEW, usd("1"), PAKETE, usd("1")),
            List.of(paket(1, Paketstufe.MITTEL), paket(2, Paketstufe.MITTEL)),
            "4");

    Voraussage anforderung =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.ANFORDERUNG, vorhaben, dreiMitSechs(), preise)));
    Voraussage pakete =
        voraussage(
            KostenVoraussage.rechne(
                new Eingaben(Rechnung.PAKETE, vorhaben, dreiMitSechs(), preise)));

    // Beide: bisher 4 $, Schnitt 0, Abdeckung 1 $, zwei mittlere Pakete 4 $; die erste Stufe
    // erwartet dazu vier weitere Pakete zu 1,50 $.
    assertThat(anforderung.art()).isEqualTo(Art.ANFORDERUNG);
    assertThat(anforderung.typisch()).isEqualByComparingTo("15");
    assertThat(anforderung.paketzahl()).isEqualTo(6);
    assertThat(pakete.art()).isEqualTo(Art.PAKETE);
    assertThat(pakete.typisch()).isEqualByComparingTo("9");
    assertThat(pakete.paketzahl()).isEqualTo(2);
  }
}
