package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.Fertigprognose.Tage;

/** Exakte Fertigprognose aus den Tageswerten (Issue #1823, Plan #1819, A3, A4, E5). */
class FertigprognoseTest {

  /** Verteilungen für die Eigenschaften AK 2, AK 3 und A3 — mit Nulltagen und Ausreißern. */
  private static final List<List<Integer>> VERTEILUNGEN =
      List.of(
          List.of(1),
          List.of(0, 1),
          List.of(0, 0, 3),
          List.of(2, 0, 0, 0, 5, 1, 0),
          List.of(0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
          List.of(4, 4, 3, 5, 0, 6),
          List.of(
              0, 1, 1, 2, 0, 0, 7, 1, 0, 0, 0, 3, 0, 1, 0, 2, 0, 0, 1, 0, 0, 0, 4, 0, 1, 0, 0, 0, 2,
              0));

  /** Bei so vielen Tagen mit einem einzigen Paket fällt der 85-%-Tag genau auf die Grenze. */
  private static final int SCHUTZGRENZE_DRIN = 19_240;

  /** Ein Tag mehr, und der 85-%-Tag läge hinter der Grenze (Tag 36 502). */
  private static final int SCHUTZGRENZE_DRAUSSEN = 19_241;

  private static final Set<Integer> ANZAHLEN =
      IntStream.rangeClosed(1, 25).boxed().collect(Collectors.toSet());

  private static Optional<Tage> einzeln(List<Integer> tageswerte, int anzahl) {
    return Fertigprognose.rechne(tageswerte, Set.of(anzahl)).get(anzahl);
  }

  private static Tage tage(List<Integer> tageswerte, int anzahl) {
    return einzeln(tageswerte, anzahl).orElseThrow();
  }

  @Test
  @DisplayName("Jeden Tag genau 1 Paket: n Pakete sind an Tag n fertig, für beide Marken")
  void jedenTagEinPaket() {
    Map<Integer, Optional<Tage>> prognose =
        Fertigprognose.rechne(Collections.nCopies(30, 1), Set.of(1, 5, 12));

    assertThat(prognose)
        .containsOnly(
            Map.entry(1, Optional.of(new Tage(1, 1))),
            Map.entry(5, Optional.of(new Tage(5, 5))),
            Map.entry(12, Optional.of(new Tage(12, 12))));
  }

  @Test
  @DisplayName("Halbe Nulltage: P(fertig) = 1 − 2⁻ᵏ für 1 Paket, Binomial für 2 Pakete")
  void halbeNulltage() {
    List<Integer> werte = List.of(0, 1);

    // n = 1: 1/2 an Tag 1, 3/4 an Tag 2, 7/8 an Tag 3
    assertThat(tage(werte, 1)).isEqualTo(new Tage(1, 3));
    // n = 2: 1/4, 4/8, 11/16, 26/32, 57/64 an den Tagen 2 bis 6
    assertThat(tage(werte, 2)).isEqualTo(new Tage(3, 6));
  }

  @Test
  @DisplayName("Zwei Nulltage und ein Dreiertag: Sprünge über mehrere Pakete")
  void nulltageUndDreiertag() {
    List<Integer> werte = List.of(0, 0, 3);

    // n = 2 und 3: 1 − (2/3)ᵏ, also 5/9 an Tag 2 und 211/243 an Tag 5
    assertThat(tage(werte, 2)).isEqualTo(new Tage(2, 5));
    assertThat(tage(werte, 3)).isEqualTo(new Tage(2, 5));
    // n = 4: mindestens zwei Dreier, 131/243 an Tag 5 und 16867/19683 an Tag 9
    assertThat(tage(werte, 4)).isEqualTo(new Tage(5, 9));
  }

  @Test
  @DisplayName("Eine Wahrscheinlichkeit genau auf der Marke erreicht sie")
  void genauAufDerMarke() {
    // 17 von 20 Tagen mit einem Paket: P(fertig an Tag 1) = 0,85 genau
    List<Integer> werte = new ArrayList<>(Collections.nCopies(17, 1));
    werte.addAll(Collections.nCopies(3, 0));

    assertThat(tage(werte, 1)).isEqualTo(new Tage(1, 1));
  }

  @Test
  @DisplayName(
      "Eine exakt erreichte Marke zählt, auch wenn die Gleitkommasumme knapp darunter liegt")
  void markeTrotzRundung() {
    // 4 Nulltage, 4 Einser, 5 Zweier, 7 Dreier: P(Summe zweier Tage ≥ 4) = 200/400 = 1/2 genau,
    // gerechnet ergibt sich 0,49999999999999994
    List<Integer> werte = new ArrayList<>(Collections.nCopies(4, 0));
    werte.addAll(Collections.nCopies(4, 1));
    werte.addAll(Collections.nCopies(5, 2));
    werte.addAll(Collections.nCopies(7, 3));

    assertThat(tage(werte, 4)).isEqualTo(new Tage(2, 4));
  }

  @Test
  @DisplayName("AK 2: Das 85-%-Datum liegt nie vor dem 50-%-Datum")
  void fuenfundachtzigNieVorFuenfzig() {
    for (List<Integer> werte : VERTEILUNGEN) {
      Fertigprognose.rechne(werte, ANZAHLEN)
          .forEach(
              (anzahl, tage) ->
                  assertThat(tage.orElseThrow().tag85())
                      .as("Verteilung %s, n = %d", werte, anzahl)
                      .isGreaterThanOrEqualTo(tage.orElseThrow().tag50()));
    }
  }

  @Test
  @DisplayName("AK 3: Mehr offene Pakete liegen bei gleichem Durchsatz nie früher")
  void mehrPaketeNieFrueher() {
    for (List<Integer> werte : VERTEILUNGEN) {
      Map<Integer, Optional<Tage>> prognose = Fertigprognose.rechne(werte, ANZAHLEN);
      for (int anzahl = 1; anzahl < 25; anzahl++) {
        Tage weniger = prognose.get(anzahl).orElseThrow();
        Tage mehr = prognose.get(anzahl + 1).orElseThrow();
        assertThat(mehr.tag50())
            .as("Verteilung %s, n = %d", werte, anzahl)
            .isGreaterThanOrEqualTo(weniger.tag50());
        assertThat(mehr.tag85())
            .as("Verteilung %s, n = %d", werte, anzahl)
            .isGreaterThanOrEqualTo(weniger.tag85());
      }
    }
  }

  @Test
  @DisplayName("AK 10: Gleiche Eingabe, gleiche Ausgabe — kein Zufall")
  void gleicheEingabeGleicheAusgabe() {
    for (List<Integer> werte : VERTEILUNGEN) {
      assertThat(Fertigprognose.rechne(werte, ANZAHLEN))
          .isEqualTo(Fertigprognose.rechne(List.copyOf(werte), Set.copyOf(ANZAHLEN)));
    }
  }

  @Test
  @DisplayName("A3: Mehrere n aus einem Durchlauf liefern dieselben Tage wie Einzeldurchläufe")
  void einDurchlaufWieEinzeldurchlaeufe() {
    for (List<Integer> werte : VERTEILUNGEN) {
      Map<Integer, Optional<Tage>> gemeinsam = Fertigprognose.rechne(werte, ANZAHLEN);
      for (int anzahl : ANZAHLEN) {
        assertThat(gemeinsam.get(anzahl))
            .as("Verteilung %s, n = %d", werte, anzahl)
            .isEqualTo(einzeln(werte, anzahl));
      }
    }
  }

  @Test
  @DisplayName("Schutzgrenze: lauter Nulltage liefern für jedes n keinen Tag, keine Ausnahme")
  void schutzgrenzeMitLauterNulltagen() {
    Map<Integer, Optional<Tage>> prognose =
        Fertigprognose.rechne(Collections.nCopies(30, 0), Set.of(1, 4));

    assertThat(prognose)
        .containsOnly(Map.entry(1, Optional.empty()), Map.entry(4, Optional.empty()));
  }

  @Test
  @DisplayName("Schutzgrenze: ein 85-%-Tag genau an der Grenze zählt, einer danach nicht")
  void schutzgrenzeGenau() {
    // Ein Paket an einem von m Tagen: P(fertig an Tag k) = 1 − (1 − 1/m)ᵏ
    assertThat(einzeln(einPaketAn(SCHUTZGRENZE_DRIN), 1).orElseThrow().tag85())
        .isEqualTo(Fertigprognose.SCHUTZGRENZE);
    assertThat(einzeln(einPaketAn(SCHUTZGRENZE_DRAUSSEN), 1)).isEmpty();
  }

  @Test
  @DisplayName("Die Schutzgrenze liegt bei 36 500 Tagen")
  void schutzgrenzeIstHundertJahre() {
    assertThat(Fertigprognose.SCHUTZGRENZE).isEqualTo(36_500);
  }

  @Test
  @DisplayName("Ohne Tageswerte, mit n unter 1 oder negativen Werten gibt es keine Rechnung")
  void ungueltigeEingaben() {
    assertThatThrownBy(() -> Fertigprognose.rechne(List.of(), Set.of(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Fertigprognose.rechne(List.of(1), Set.of(0)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Fertigprognose.rechne(List.of(1, -1), Set.of(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Ohne n gibt es eine leere Prognose")
  void ohneAnzahlen() {
    assertThat(Fertigprognose.rechne(List.of(1), Set.of())).isEmpty();
  }

  /** m Tageswerte, davon genau einer mit einem Paket. */
  private static List<Integer> einPaketAn(int tage) {
    List<Integer> werte = new ArrayList<>(Collections.nCopies(tage - 1, 0));
    werte.add(1);
    return werte;
  }
}
