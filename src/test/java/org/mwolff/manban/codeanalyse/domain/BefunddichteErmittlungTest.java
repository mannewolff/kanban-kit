package org.mwolff.manban.codeanalyse.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.NichtZaehlend;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.Summe;
import org.mwolff.manban.codeanalyse.domain.Wochenwerte.Woche;

/**
 * Die Wochenwerte der Platte „Codeanalyse“ mit den Zahlen des Fachplans (Issue #2273, Plan #2272
 * E6, E7, E9; Fachplan #2271 AK 2–7).
 */
class BefunddichteErmittlungTest {

  /** Ein Mittwoch; die laufende Woche beginnt am Montag 2026-11-23. */
  private static final Instant JETZT = Instant.parse("2026-11-25T12:00:00Z");

  private static final Instant DIESE_WOCHE = Instant.parse("2026-11-24T09:00:00Z");
  private static final int LETZTE = Kalenderwochen.ANZAHL - 1;

  /** Die Eingaben einer Lage. */
  private static final class Lage {
    private final List<Messung> messungen = new ArrayList<>();

    Lage zaehlend(Instant publishedAt, int karten, Zustand vorher, Zustand endzustand) {
      messungen.add(
          new Messung(
              "pub-" + messungen.size(),
              publishedAt,
              "base",
              "1.0.0",
              true,
              null,
              karten,
              vorher,
              endzustand));
      return this;
    }

    Lage nichtZaehlend(Instant publishedAt, String grund) {
      messungen.add(
          new Messung(
              "pub-" + messungen.size(),
              publishedAt,
              "base",
              "1.0.0",
              false,
              grund,
              0,
              null,
              null));
      return this;
    }

    Wochenwerte ermittle() {
      return BefunddichteErmittlung.ermittle(messungen, JETZT);
    }
  }

  private static Lage lage() {
    return new Lage();
  }

  /** Ein Zustand mit allen Funden als Fehler. */
  private static Zustand funde(int funde, int zeilen) {
    return new Zustand(funde, 0, 0, zeilen);
  }

  private static Woche laufende(Wochenwerte werte) {
    return werte.wochen().get(LETZTE);
  }

  @Test
  void zahlenDesFachplans_dichteVorherUndEndzustand_undRueckgang() {
    Woche woche =
        laufende(lage().zaehlend(DIESE_WOCHE, 3, funde(20, 2000), funde(4, 2200)).ermittle());

    assertThat(woche.vorher().funde()).isEqualTo(20);
    assertThat(woche.vorher().zeilen()).isEqualTo(2000);
    assertThat(woche.vorher().dichte()).isEqualTo(10.0);
    assertThat(woche.endzustand().funde()).isEqualTo(4);
    assertThat(woche.endzustand().zeilen()).isEqualTo(2200);
    assertThat(woche.endzustand().dichte()).isCloseTo(1.8, within(0.05));
    assertThat(woche.rueckgangProzent()).isEqualTo(80.0);
    assertThat(woche.zaehlendeMessungen()).isEqualTo(1);
    assertThat(woche.karten()).isEqualTo(3);
  }

  @Test
  void dichte_jeArt_ausDenWochensummen() {
    Woche woche =
        laufende(
            lage()
                .zaehlend(DIESE_WOCHE, 1, new Zustand(1, 2, 3, 500), new Zustand(0, 1, 2, 500))
                .zaehlend(DIESE_WOCHE, 1, new Zustand(1, 0, 1, 500), new Zustand(0, 0, 0, 500))
                .ermittle());

    Summe vorher = woche.vorher();
    assertThat(vorher.fehler()).isEqualTo(2);
    assertThat(vorher.sicherheitsluecken()).isEqualTo(2);
    assertThat(vorher.wartbarkeitsmaengel()).isEqualTo(4);
    assertThat(vorher.funde()).isEqualTo(8);
    assertThat(vorher.zeilen()).isEqualTo(1000);
    assertThat(vorher.dichteFehler()).isEqualTo(2.0);
    assertThat(vorher.dichteSicherheitsluecken()).isEqualTo(2.0);
    assertThat(vorher.dichteWartbarkeitsmaengel()).isEqualTo(4.0);
    assertThat(vorher.dichte()).isEqualTo(8.0);
    Summe endzustand = woche.endzustand();
    assertThat(endzustand.fehler()).isZero();
    assertThat(endzustand.sicherheitsluecken()).isEqualTo(1);
    assertThat(endzustand.wartbarkeitsmaengel()).isEqualTo(2);
    assertThat(endzustand.dichte()).isEqualTo(3.0);
    assertThat(woche.rueckgangProzent()).isEqualTo(62.5);
  }

  @Test
  void zweiMessungen_dichteAusSummen_keinMittelwert() {
    Woche woche =
        laufende(
            lage()
                .zaehlend(DIESE_WOCHE, 1, funde(10, 1000), funde(10, 1000))
                .zaehlend(DIESE_WOCHE, 1, funde(0, 9000), funde(0, 9000))
                .ermittle());

    assertThat(woche.vorher().dichte()).isEqualTo(1.0);
    assertThat(woche.endzustand().dichte()).isEqualTo(1.0);
  }

  @Test
  void steigendeFunde_ergebenNegativenRueckgang() {
    Woche woche =
        laufende(lage().zaehlend(DIESE_WOCHE, 1, funde(3, 100), funde(4, 100)).ermittle());

    assertThat(woche.rueckgangProzent()).isCloseTo(-33.3, within(0.05));
  }

  @Test
  void nullZeilen_ergebenFundeUndZeilen_ohneDichte() {
    Woche woche = laufende(lage().zaehlend(DIESE_WOCHE, 1, funde(5, 0), funde(2, 0)).ermittle());

    Summe vorher = woche.vorher();
    assertThat(vorher.funde()).isEqualTo(5);
    assertThat(vorher.zeilen()).isZero();
    assertThat(vorher.dichte()).isNull();
    assertThat(vorher.dichteFehler()).isNull();
    assertThat(vorher.dichteSicherheitsluecken()).isNull();
    assertThat(vorher.dichteWartbarkeitsmaengel()).isNull();
    assertThat(woche.endzustand().funde()).isEqualTo(2);
    assertThat(woche.endzustand().dichte()).isNull();
    assertThat(woche.rueckgangProzent()).isEqualTo(60.0);
  }

  @Test
  void nullFundeVorher_ergebenKeinenRueckgang() {
    Woche woche =
        laufende(lage().zaehlend(DIESE_WOCHE, 1, funde(0, 100), funde(2, 100)).ermittle());

    assertThat(woche.vorher().funde()).isZero();
    assertThat(woche.vorher().dichte()).isZero();
    assertThat(woche.endzustand().funde()).isEqualTo(2);
    assertThat(woche.rueckgangProzent()).isNull();
  }

  @Test
  void nichtZaehlendeMessung_zaehltInKeinerSumme_underscheintMitGrundUndZahl() {
    Woche woche =
        laufende(
            lage()
                .zaehlend(DIESE_WOCHE, 2, funde(10, 1000), funde(5, 1000))
                .nichtZaehlend(DIESE_WOCHE, "missing-final")
                .nichtZaehlend(DIESE_WOCHE, "incomplete")
                .nichtZaehlend(DIESE_WOCHE, "missing-final")
                .ermittle());

    assertThat(woche.zaehlendeMessungen()).isEqualTo(1);
    assertThat(woche.karten()).isEqualTo(2);
    assertThat(woche.vorher().funde()).isEqualTo(10);
    assertThat(woche.vorher().zeilen()).isEqualTo(1000);
    assertThat(woche.nichtZaehlend())
        .containsExactly(new NichtZaehlend("incomplete", 1), new NichtZaehlend("missing-final", 2));
  }

  @Test
  void wocheOhneZaehlendeMessung_hatKeineZustaendeUndKeineKartenzahl() {
    Woche woche = laufende(lage().nichtZaehlend(DIESE_WOCHE, "no-measurement").ermittle());

    assertThat(woche.zaehlendeMessungen()).isZero();
    assertThat(woche.karten()).isNull();
    assertThat(woche.vorher()).isNull();
    assertThat(woche.endzustand()).isNull();
    assertThat(woche.rueckgangProzent()).isNull();
    assertThat(woche.nichtZaehlend()).containsExactly(new NichtZaehlend("no-measurement", 1));
  }

  @Test
  void karteInZweiMessungen_zaehltZweimal() {
    Woche woche =
        laufende(
            lage()
                .zaehlend(DIESE_WOCHE, 1, funde(1, 10), funde(0, 10))
                .zaehlend(DIESE_WOCHE.plusSeconds(60), 1, funde(1, 10), funde(0, 10))
                .ermittle());

    assertThat(woche.zaehlendeMessungen()).isEqualTo(2);
    assertThat(woche.karten()).isEqualTo(2);
  }

  @Test
  void zwoelfWochen_aeltesteZuerst_mitWochenbeginn() {
    Wochenwerte werte = lage().ermittle();

    assertThat(werte.wochen()).hasSize(Kalenderwochen.ANZAHL);
    for (int i = 0; i < Kalenderwochen.ANZAHL; i++) {
      assertThat(werte.wochen().get(i).wochenbeginn()).isEqualTo(Kalenderwochen.beginn(i, JETZT));
    }
  }

  @Test
  void ohneMessungen_keineMessungen_undKeinDataSince() {
    Wochenwerte werte = lage().ermittle();

    assertThat(werte.hatMessungen()).isFalse();
    assertThat(werte.dataSince()).isNull();
    assertThat(werte.wochen())
        .allSatisfy(
            w -> {
              assertThat(w.zaehlendeMessungen()).isZero();
              assertThat(w.vorher()).isNull();
              assertThat(w.nichtZaehlend()).isEmpty();
            });
  }

  @Test
  void messungAusserhalbDesFensters_zaehltInKeinerWoche_aberAlsMessung() {
    Instant zuFrueh = Kalenderwochen.fensterbeginn(JETZT).minusSeconds(1);
    Wochenwerte werte = lage().zaehlend(zuFrueh, 1, funde(1, 10), funde(1, 10)).ermittle();

    assertThat(werte.hatMessungen()).isTrue();
    assertThat(werte.wochen()).allSatisfy(w -> assertThat(w.zaehlendeMessungen()).isZero());
  }

  @Test
  void dataSince_istDieErsteVeroeffentlichung_undDieWochenDavorSindLeer() {
    Instant montag = Instant.parse("2026-11-02T10:00:00Z");
    Wochenwerte werte = lage().zaehlend(montag, 1, funde(1, 10), funde(0, 10)).ermittle();

    assertThat(werte.hatMessungen()).isTrue();
    assertThat(werte.dataSince()).isEqualTo(montag);
    assertThat(werte.wochen().get(8).zaehlendeMessungen()).isEqualTo(1);
    for (int i = 0; i < 8; i++) {
      Woche woche = werte.wochen().get(i);
      assertThat(woche.zaehlendeMessungen()).isZero();
      assertThat(woche.vorher()).isNull();
      assertThat(woche.nichtZaehlend()).isEmpty();
    }
  }

  @Test
  void dataSince_folgtDemKleinstenPublishedAt_auchEinerNichtZaehlenden() {
    Instant montag = Instant.parse("2026-11-02T10:00:00Z");
    Instant frueher = montag.minus(Duration.ofDays(10));
    Wochenwerte werte =
        lage()
            .zaehlend(montag, 1, funde(1, 10), funde(0, 10))
            .nichtZaehlend(frueher, "cards-removed")
            .ermittle();

    assertThat(werte.dataSince()).isEqualTo(frueher);
  }

  @Test
  void dataSince_istLeer_wennDieFruehesteMessungVorDemFensterLiegt() {
    Instant vorDemFenster = Kalenderwochen.fensterbeginn(JETZT).minusSeconds(1);
    Wochenwerte werte =
        lage()
            .zaehlend(Instant.parse("2026-11-02T10:00:00Z"), 1, funde(1, 10), funde(0, 10))
            .nichtZaehlend(vorDemFenster, "incomplete")
            .ermittle();

    assertThat(werte.dataSince()).isNull();
  }

  @Test
  void dataSince_amFensterbeginn_istLeer() {
    Instant fensterbeginn = Kalenderwochen.fensterbeginn(JETZT);
    Wochenwerte werte = lage().nichtZaehlend(fensterbeginn, "incomplete").ermittle();

    assertThat(werte.dataSince()).isNull();
  }

  @Test
  void dataSince_kurzNachFensterbeginn_istGesetzt() {
    Instant kurzDanach = Kalenderwochen.fensterbeginn(JETZT).plusSeconds(1);
    Wochenwerte werte = lage().nichtZaehlend(kurzDanach, "incomplete").ermittle();

    assertThat(werte.dataSince()).isEqualTo(kurzDanach);
  }
}
