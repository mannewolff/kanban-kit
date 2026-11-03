package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Unit-Tests der Kostenzuordnung (Issue #2000, Plan #1990 E1): Jede Karte zählt bei genau einem
 * Vorhaben — unmittelbar über {@code parentId} vor geerbt über die Herkunftskette.
 *
 * <p>Die Zugehörigkeit steht hier von Hand, so wie {@code EpicMembership.computeMitArchiv} sie
 * liefert: {@code EpicMembership} ist modulintern in {@code card.application}, und die Regel der
 * Kostenzuordnung soll ohne sie prüfbar sein.
 */
class KostenzuordnungTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private static Card vorhaben(long id, int number, boolean archived) {
    return karte(id, number, CardType.EPIC, null, null, archived);
  }

  private static Card karte(
      long id, int number, @Nullable Long parentId, @Nullable Long derivedFrom) {
    return karte(id, number, CardType.CARD, parentId, derivedFrom, false);
  }

  private static Card karte(
      long id,
      int number,
      CardType type,
      @Nullable Long parentId,
      @Nullable Long derivedFrom,
      boolean archived) {
    return new Card(
        id,
        10L,
        100L,
        number,
        "Karte " + number,
        null,
        0,
        archived,
        null,
        null,
        FIXED,
        FIXED,
        type,
        parentId,
        null,
        null,
        1L,
        null,
        derivedFrom,
        null,
        null);
  }

  @Test
  void eineUnmittelbarZugeordneteKarteZaehltBeiIhremVorhaben() {
    Card export = vorhaben(1L, 1, false);
    Card paket = karte(2L, 2, 1L, null);

    Map<Integer, Card> ergebnis =
        Kostenzuordnung.ermittle(List.of(export, paket), Map.of(1L, Set.of(paket)));

    assertThat(ergebnis).containsExactly(Map.entry(2, export));
  }

  /** Antwort 7: unmittelbar vor geerbt — die Karte zählt genau einmal, beim eigenen Vorhaben. */
  @Test
  void unmittelbarGehtVorGeerbt() {
    Card export = vorhaben(1L, 1, false);
    Card suche = vorhaben(2L, 2, false);
    Card plan = karte(3L, 3, 2L, null);
    Card paket = karte(4L, 4, 1L, 3L);

    Map<Integer, Card> ergebnis =
        Kostenzuordnung.ermittle(
            List.of(export, suche, plan, paket),
            Map.of(1L, Set.of(paket), 2L, Set.of(plan, paket)));

    assertThat(ergebnis).containsOnly(Map.entry(4, export), Map.entry(3, suche));
  }

  @Test
  void eineNurGeerbteKarteZaehltBeimErstenVorhabenDerKette() {
    Card export = vorhaben(1L, 1, false);
    Card suche = vorhaben(2L, 2, false);
    Card anforderung = karte(3L, 3, 2L, null);
    Card plan = karte(4L, 4, 1L, 3L);
    Card paket = karte(5L, 5, null, 4L);

    Map<Integer, Card> ergebnis =
        Kostenzuordnung.ermittle(
            List.of(export, suche, anforderung, plan, paket),
            Map.of(1L, Set.of(plan, paket), 2L, Set.of(anforderung, plan, paket)));

    assertThat(ergebnis.get(5)).isEqualTo(export);
    assertThat(ergebnis.get(4)).isEqualTo(export);
    assertThat(ergebnis.get(3)).isEqualTo(suche);
  }

  @Test
  void dieKetteReichtUeberMehrereAbleitungen() {
    Card export = vorhaben(1L, 1, false);
    Card anforderung = karte(2L, 2, 1L, null);
    Card plan = karte(3L, 3, null, 2L);
    Card paket = karte(4L, 4, null, 3L);
    Card nacharbeit = karte(5L, 5, null, 4L);

    Map<Integer, Card> ergebnis =
        Kostenzuordnung.ermittle(
            List.of(export, anforderung, plan, paket, nacharbeit),
            Map.of(1L, Set.of(anforderung, plan, paket, nacharbeit)));

    assertThat(ergebnis.get(5)).isEqualTo(export);
    assertThat(ergebnis).containsOnlyKeys(2, 3, 4, 5);
  }

  @Test
  void derWegGehtDurchArchivierteKartenHindurch() {
    Card export = vorhaben(1L, 1, false);
    Card plan = karte(2L, 2, CardType.CARD, 1L, null, true);
    Card paket = karte(3L, 3, CardType.CARD, null, 2L, true);

    Map<Integer, Card> ergebnis =
        Kostenzuordnung.ermittle(List.of(export, plan, paket), Map.of(1L, Set.of(plan, paket)));

    assertThat(ergebnis).containsOnly(Map.entry(2, export), Map.entry(3, export));
  }

  @Test
  void einArchiviertesVorhabenBleibtKostenvorhaben() {
    Card export = vorhaben(1L, 1, true);
    Card paket = karte(2L, 2, 1L, null);

    assertThat(Kostenzuordnung.ermittle(List.of(export, paket), Map.of(1L, Set.of(paket))))
        .containsExactly(Map.entry(2, export));
  }

  /**
   * Ein Vorhaben auf dem Weg nach oben ist ein Glied wie jedes andere: Seine eigene Herkunft führt
   * weiter, es selbst ist für die Karte darunter kein Kostenvorhaben.
   */
  @Test
  void derWegGehtDurchEinVorhabenHindurch() {
    Card export = vorhaben(1L, 1, false);
    Card anforderung = karte(2L, 2, 1L, null);
    Card unterVorhaben = karte(3L, 3, CardType.EPIC, null, 2L, true);
    Card paket = karte(4L, 4, null, 3L);

    Map<Integer, Card> ergebnis =
        Kostenzuordnung.ermittle(
            List.of(export, anforderung, unterVorhaben, paket),
            Map.of(1L, Set.of(anforderung, paket), 3L, Set.of()));

    assertThat(ergebnis).containsOnly(Map.entry(2, export), Map.entry(4, export));
  }

  @Test
  void eineKarteOhneVorhabenUndEinVorhabenSelbstFehlen() {
    Card export = vorhaben(1L, 1, false);
    Card frei = karte(2L, 2, null, null);
    Card abgeleitetVonFrei = karte(3L, 3, null, 2L);

    assertThat(
            Kostenzuordnung.ermittle(
                List.of(export, frei, abgeleitetVonFrei), Map.of(1L, Set.of())))
        .isEmpty();
  }

  /** Ein {@code parentId}, das auf kein Vorhaben dieses Boards zeigt, ordnet nichts zu. */
  @Test
  void einVerweisAufEinFremdesVorhabenOrdnetNichtsZu() {
    Card paket = karte(2L, 2, 99L, null);

    assertThat(Kostenzuordnung.ermittle(List.of(paket), Map.of())).isEmpty();
  }

  /** Zählt die Zugehörigkeit eine Karte nicht zum Vorhaben, ist es nicht ihr Kostenvorhaben. */
  @Test
  void ohneZugehoerigkeitKeinKostenvorhaben() {
    Card keinVorhaben = karte(1L, 1, null, null);
    Card paket = karte(2L, 2, 1L, null);

    assertThat(Kostenzuordnung.ermittle(List.of(keinVorhaben, paket), Map.of())).isEmpty();
  }

  @Test
  @Timeout(5)
  void einRingOhneVorhabenTerminiert() {
    Card a = karte(2L, 2, null, 3L);
    Card b = karte(3L, 3, null, 2L);

    assertThat(Kostenzuordnung.ermittle(List.of(a, b), Map.of())).isEmpty();
  }
}
