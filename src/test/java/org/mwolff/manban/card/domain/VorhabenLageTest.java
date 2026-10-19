package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.Durchsatzermittlung.Durchsatz;
import org.mwolff.manban.card.domain.VorhabenLage.Dokument;
import org.mwolff.manban.card.domain.VorhabenLage.Ergebnis;
import org.mwolff.manban.card.domain.VorhabenLage.Lage;

/** Lage, Zählung und ausstehende Dokumente je Vorhaben (Issue #1825, Plan #1819, A3, A6–A8). */
// PMD.TooManyMethods: Das Paket verlangt je Fachregel einen eigenen Fall; eine kleine Testmethode
// je Fall bleibt lesbarer als eine Aufteilung auf mehrere Klassen.
@SuppressWarnings("PMD.TooManyMethods")
class VorhabenLageTest {

  private static final Instant FIXED = Instant.parse("2026-10-17T10:00:00Z");
  private static final long BACKLOG = 100L;
  private static final long IN_REVIEW = 101L;
  private static final long DONE = 102L;
  private static final Map<Long, String> SPALTEN =
      Map.of(BACKLOG, "Backlog", IN_REVIEW, "In Review", DONE, "Done");

  /** Jeden Tag genau ein Paket: n offene Pakete sind an Tag n sicher fertig. */
  private static final Durchsatz JEDEN_TAG_EINS =
      new Durchsatz(Collections.nCopies(Durchsatzermittlung.TAGE, 1), Durchsatzermittlung.TAGE);

  /** Ein Tag mit Paketen zu wenig für eine Prognose (AK 5). */
  private static final Durchsatz ZU_WENIG =
      new Durchsatz(Collections.nCopies(Durchsatzermittlung.TAGE, 1), 4);

  /** Gerade genug Tage mit Paketen. */
  private static final Durchsatz GERADE_GENUG =
      new Durchsatz(Collections.nCopies(Durchsatzermittlung.TAGE, 1), 5);

  /** Lauter Nulltage: Die Rechnung erreicht bis zur Schutzgrenze keinen Tag. */
  private static final Durchsatz NULLTAGE =
      new Durchsatz(Collections.nCopies(Durchsatzermittlung.TAGE, 0), 5);

  /** Das Board des Tests: Karten in Anlage-Reihenfolge, Mitglieder je Vorhaben samt Archiv. */
  private final List<Card> karten = new ArrayList<>();

  private final Map<Long, Set<Card>> mitArchiv = new LinkedHashMap<>();
  private long naechsteId = 1L;

  private static Card karte(
      long id,
      String titel,
      CardType type,
      long spalte,
      @Nullable CardStatus status,
      @Nullable Long derivedFrom,
      boolean archiviert) {
    return new Card(
        id,
        1L,
        spalte,
        (int) id,
        titel,
        null,
        0,
        archiviert,
        null,
        null,
        FIXED,
        FIXED,
        type,
        null,
        null,
        null,
        1L,
        null,
        derivedFrom,
        null,
        status);
  }

  private long neueId() {
    long id = naechsteId;
    naechsteId++;
    return id;
  }

  private Card vorhaben() {
    return vorhaben(false);
  }

  private Card vorhaben(boolean archiviert) {
    long id = neueId();
    Card epic = karte(id, "Vorhaben " + id, CardType.EPIC, BACKLOG, null, null, archiviert);
    karten.add(epic);
    mitArchiv.put(id, new LinkedHashSet<>());
    return epic;
  }

  private Card zu(Card epic, Card karte) {
    karten.add(karte);
    mitArchiv.get(epic.requireId()).add(karte);
    return karte;
  }

  private Card paket(Card epic, CardStatus status) {
    return paket(epic, status, null, false);
  }

  private Card paket(Card epic, CardStatus status, @Nullable Card aus, boolean archiviert) {
    long id = neueId();
    long spalte = spalteVon(status);
    return zu(
        epic, karte(id, "Paket " + id, CardType.CARD, spalte, status, idVon(aus), archiviert));
  }

  private static long spalteVon(CardStatus status) {
    if (status == CardStatus.IN_REVIEW) {
      return IN_REVIEW;
    }
    return status == CardStatus.DONE ? DONE : BACKLOG;
  }

  private void offenePakete(Card epic, int anzahl) {
    for (int i = 0; i < anzahl; i++) {
      paket(epic, CardStatus.BACKLOG);
    }
  }

  /** Eine Karte ohne Status, deren Art allein ihr Titel bestimmt. */
  private Card dokument(Card epic, String titel, @Nullable Card aus, long spalte, boolean archiv) {
    long id = neueId();
    return zu(epic, karte(id, titel, CardType.CARD, spalte, null, idVon(aus), archiv));
  }

  private Card plan(Card epic, @Nullable Card aus) {
    return dokument(epic, "[Plan] Plan " + naechsteId, aus, BACKLOG, false);
  }

  private Card fachplan(Card epic) {
    return dokument(epic, "[Fachlich] Anforderung " + naechsteId, null, BACKLOG, false);
  }

  private static @Nullable Long idVon(@Nullable Card karte) {
    return karte == null ? null : karte.requireId();
  }

  /** Die Zugehörigkeit wie {@code EpicMembership.compute}: ohne archivierte Karten. */
  private Map<Long, Set<Card>> ohneArchiv() {
    Map<Long, Set<Card>> ergebnis = new LinkedHashMap<>();
    mitArchiv.forEach(
        (id, mitglieder) -> {
          Set<Card> aktiv = new LinkedHashSet<>();
          mitglieder.stream().filter(c -> !c.archived()).forEach(aktiv::add);
          ergebnis.put(id, aktiv);
        });
    return ergebnis;
  }

  private List<Ergebnis> ermittle(Durchsatz durchsatz) {
    return VorhabenLage.ermittle(karten, ohneArchiv(), mitArchiv, SPALTEN, durchsatz);
  }

  private Ergebnis ermittle(Card epic, Durchsatz durchsatz) {
    return ermittle(durchsatz).stream()
        .filter(e -> e.vorhaben().equals(epic))
        .findFirst()
        .orElseThrow();
  }

  // --- Rangfolge AK 11 ---

  @Test
  @DisplayName("Rangfolge AK 11: ohne Pakete gilt NO_PACKAGES, auch bei zu wenig Durchsatz")
  void ohnePaketeVorAllemAnderen() {
    Card epic = vorhaben();
    plan(epic, null);

    assertThat(ermittle(epic, ZU_WENIG).lage()).isEqualTo(Lage.NO_PACKAGES);
    assertThat(ermittle(epic, JEDEN_TAG_EINS).lage()).isEqualTo(Lage.NO_PACKAGES);
  }

  @Test
  @DisplayName("Rangfolge AK 11: ohne offene Pakete gilt ALL_DONE, auch bei zu wenig Durchsatz")
  void ohneOffeneVorDurchsatz() {
    Card epic = vorhaben();
    paket(epic, CardStatus.IN_REVIEW);

    Ergebnis ergebnis = ermittle(epic, ZU_WENIG);

    assertThat(ergebnis.lage()).isEqualTo(Lage.ALL_DONE);
    assertThat(ergebnis.tage()).isNull();
    assertThat(ermittle(epic, JEDEN_TAG_EINS).lage()).isEqualTo(Lage.ALL_DONE);
  }

  @Test
  @DisplayName("Rangfolge AK 11: mit offenen Paketen und zu wenig Durchsatz gilt INSUFFICIENT")
  void zuWenigDurchsatz() {
    Card epic = vorhaben();
    paket(epic, CardStatus.READY);
    plan(epic, null);

    Ergebnis ergebnis = ermittle(epic, ZU_WENIG);

    assertThat(ergebnis.lage()).isEqualTo(Lage.INSUFFICIENT_THROUGHPUT);
    assertThat(ergebnis.tage()).isNull();
    assertThat(ergebnis.earliest()).isFalse();
  }

  @Test
  @DisplayName("Rangfolge AK 11: mit offenen Paketen und genug Durchsatz gilt FORECAST mit Tagen")
  void prognose() {
    Card epic = vorhaben();
    offenePakete(epic, 3);

    Ergebnis ergebnis = ermittle(epic, GERADE_GENUG);

    assertThat(ergebnis.lage()).isEqualTo(Lage.FORECAST);
    assertThat(ergebnis.tage()).isEqualTo(new Fertigprognose.Tage(3, 3));
    assertThat(ergebnis.earliest()).isFalse();
  }

  // --- AK 12 ---

  @Test
  @DisplayName("AK 12: alle Pakete umgesetzt ohne ausstehendes Dokument")
  void alleUmgesetztOhneDokument() {
    Card epic = vorhaben();
    Card plan = plan(epic, null);
    paket(epic, CardStatus.IN_REVIEW, plan, false);
    paket(epic, CardStatus.DONE, plan, false);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.lage()).isEqualTo(Lage.ALL_DONE);
    assertThat(ergebnis.ausstehend()).isEmpty();
  }

  @Test
  @DisplayName("AK 12: alle Pakete umgesetzt, weitere folgen aus einem Plan ohne Paket")
  void alleUmgesetztMitDokument() {
    Card epic = vorhaben();
    Card geschnitten = plan(epic, null);
    paket(epic, CardStatus.DONE, geschnitten, false);
    Card offen = plan(epic, null);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.lage()).isEqualTo(Lage.ALL_DONE);
    assertThat(ergebnis.ausstehend()).containsExactly(new Dokument(offen.number(), offen.title()));
    assertThat(ergebnis.earliest()).isFalse();
  }

  // --- AK 6 / E8 ---

  @Test
  @DisplayName("AK 6: ein Plan ohne Paket macht die Prognose zu „frühestens“")
  void planOhnePaketMachtFruehestens() {
    Card epic = vorhaben();
    offenePakete(epic, 2);
    Card plan = plan(epic, null);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.lage()).isEqualTo(Lage.FORECAST);
    assertThat(ergebnis.earliest()).isTrue();
    assertThat(ergebnis.ausstehend()).containsExactly(new Dokument(plan.number(), plan.title()));
  }

  @Test
  @DisplayName("AK 6: ein Fachplan ohne Plan macht die Prognose zu „frühestens“")
  void fachplanOhnePlanMachtFruehestens() {
    Card epic = vorhaben();
    offenePakete(epic, 2);
    Card fachplan = fachplan(epic);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.earliest()).isTrue();
    assertThat(ergebnis.ausstehend())
        .containsExactly(new Dokument(fachplan.number(), fachplan.title()));
  }

  @Test
  @DisplayName("E8: ein Fachplan mit Plan ist geplant, der Plan mit Paket geschnitten")
  void ketteFachplanPlanPaket() {
    Card epic = vorhaben();
    Card fachplan = fachplan(epic);
    Card plan = plan(epic, fachplan);
    paket(epic, CardStatus.BACKLOG, plan, false);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.earliest()).isFalse();
    assertThat(ergebnis.ausstehend()).isEmpty();
  }

  @Test
  @DisplayName("E8: ein Fachplan, aus dem nur ein Paket statt eines Plans entstand, steht aus")
  void fachplanMitPaketOhnePlan() {
    Card epic = vorhaben();
    Card fachplan = fachplan(epic);
    paket(epic, CardStatus.BACKLOG, fachplan, false);

    assertThat(ermittle(epic, JEDEN_TAG_EINS).ausstehend())
        .containsExactly(new Dokument(fachplan.number(), fachplan.title()));
  }

  @Test
  @DisplayName("E8: ein [Task] mit Herkunft auf den Plan macht ihn geschnitten")
  void taskMachtPlanGeschnitten() {
    Card epic = vorhaben();
    offenePakete(epic, 1);
    Card plan = plan(epic, null);
    long id = neueId();
    zu(
        epic,
        karte(id, "[Task] Aufgabe", CardType.CARD, BACKLOG, CardStatus.READY, idVon(plan), false));

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.earliest()).isFalse();
    assertThat(ergebnis.ausstehend()).isEmpty();
    assertThat(ergebnis.open()).isEqualTo(2);
  }

  @Test
  @DisplayName("E8: eine Idee mit Herkunft auf den Plan macht ihn nicht geschnitten")
  void ideeMachtPlanNichtGeschnitten() {
    Card epic = vorhaben();
    offenePakete(epic, 1);
    Card plan = plan(epic, null);
    dokument(epic, "[Idee] Einfall", plan, BACKLOG, false);

    assertThat(ermittle(epic, JEDEN_TAG_EINS).ausstehend())
        .containsExactly(new Dokument(plan.number(), plan.title()));
  }

  @Test
  @DisplayName("E8: ein Plan mit ausschließlich archivierten Paketen löst kein frühestens aus")
  void planMitArchiviertenPaketenLoestNichtsAus() {
    Card epic = vorhaben();
    offenePakete(epic, 1);
    Card plan = plan(epic, null);
    paket(epic, CardStatus.DONE, plan, true);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.earliest()).isFalse();
    assertThat(ergebnis.ausstehend()).isEmpty();
  }

  @Test
  @DisplayName("E8: ein Plan in einer Done-Spalte oder archiviert löst nichts aus")
  void planInDoneOderArchiviertLoestNichtsAus() {
    Card epic = vorhaben();
    offenePakete(epic, 1);
    dokument(epic, "[Plan] fertig", null, DONE, false);
    dokument(epic, "[Plan] archiviert", null, BACKLOG, true);
    dokument(epic, "[Fachlich] archiviert", null, BACKLOG, true);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.earliest()).isFalse();
    assertThat(ergebnis.ausstehend()).isEmpty();
  }

  @Test
  @DisplayName("E8: Ideen und Tasks ohne Nachfahren lösen nichts aus")
  void ideenUndTasksLoesenNichtsAus() {
    Card epic = vorhaben();
    offenePakete(epic, 1);
    dokument(epic, "[Idee] Einfall", null, BACKLOG, false);
    long id = neueId();
    zu(epic, karte(id, "[Task] Aufgabe", CardType.CARD, BACKLOG, CardStatus.READY, null, false));

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.earliest()).isFalse();
    assertThat(ergebnis.ausstehend()).isEmpty();
  }

  @Test
  @DisplayName("E7: mehrere ausstehende Dokumente aufsteigend nach Nummer")
  void mehrereDokumenteNachNummer() {
    Card epic = vorhaben();
    offenePakete(epic, 1);
    long spaet = 50L;
    long frueh = 40L;
    zu(epic, karte(spaet, "[Plan] spät", CardType.CARD, BACKLOG, null, null, false));
    zu(epic, karte(frueh, "[Fachlich] früh", CardType.CARD, BACKLOG, null, null, false));

    assertThat(ermittle(epic, JEDEN_TAG_EINS).ausstehend())
        .containsExactly(new Dokument(40, "[Fachlich] früh"), new Dokument(50, "[Plan] spät"));
  }

  // --- E6 ---

  @Test
  @DisplayName("E6: alle Pakete archiviert ergibt ALL_DONE mit 0/0 statt NO_PACKAGES")
  void alleArchiviertErgibtAllDone() {
    Card epic = vorhaben();
    paket(epic, CardStatus.DONE, null, true);
    paket(epic, CardStatus.IN_REVIEW, null, true);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.lage()).isEqualTo(Lage.ALL_DONE);
    assertThat(ergebnis.total()).isZero();
    assertThat(ergebnis.finished()).isZero();
    assertThat(ergebnis.open()).isZero();
  }

  // --- A3 ---

  @Test
  @DisplayName("A3: mehr als 1 000 offene Pakete ergeben INSUFFICIENT_THROUGHPUT ohne Rechnung")
  void mehrAlsTausendOffenePakete() {
    Card gross = vorhaben();
    offenePakete(gross, VorhabenLage.HOECHSTE_PAKETZAHL + 1);
    Card grenze = vorhaben();
    offenePakete(grenze, VorhabenLage.HOECHSTE_PAKETZAHL);

    assertThat(ermittle(gross, JEDEN_TAG_EINS).lage()).isEqualTo(Lage.INSUFFICIENT_THROUGHPUT);
    assertThat(ermittle(gross, JEDEN_TAG_EINS).open()).isEqualTo(1001);
    Ergebnis ergebnis = ermittle(grenze, JEDEN_TAG_EINS);
    assertThat(ergebnis.lage()).isEqualTo(Lage.FORECAST);
    assertThat(ergebnis.tage()).isEqualTo(new Fertigprognose.Tage(1000, 1000));
  }

  @Test
  @DisplayName("A3: ein n ohne Tag ergibt INSUFFICIENT_THROUGHPUT, die übrigen behalten ihre Lage")
  void anzahlOhneTag() {
    Card offen = vorhaben();
    offenePakete(offen, 1);
    Card fertig = vorhaben();
    paket(fertig, CardStatus.DONE);
    Card leer = vorhaben();

    List<Ergebnis> ergebnisse = ermittle(NULLTAGE);

    assertThat(ergebnisse)
        .extracting(Ergebnis::vorhaben, Ergebnis::lage)
        .containsExactly(
            tuple(offen, Lage.INSUFFICIENT_THROUGHPUT),
            tuple(fertig, Lage.ALL_DONE),
            tuple(leer, Lage.NO_PACKAGES));
    assertThat(ergebnisse.getFirst().tage()).isNull();
  }

  @Test
  @DisplayName("A3: zwei Vorhaben mit verschiedenem n erhalten je ihre Tage aus einem Durchlauf")
  void verschiedeneN() {
    Card klein = vorhaben();
    offenePakete(klein, 2);
    Card gross = vorhaben();
    offenePakete(gross, 7);

    assertThat(ermittle(klein, JEDEN_TAG_EINS).tage()).isEqualTo(new Fertigprognose.Tage(2, 2));
    assertThat(ermittle(gross, JEDEN_TAG_EINS).tage()).isEqualTo(new Fertigprognose.Tage(7, 7));
  }

  // --- AK 18 ---

  @Test
  @DisplayName("AK 18: ein archiviertes Vorhaben fehlt, ohne Pakete und alle fertig erscheinen")
  void sichtbarkeit() {
    Card archiviert = vorhaben(true);
    offenePakete(archiviert, 2);
    Card leer = vorhaben();
    Card fertig = vorhaben();
    paket(fertig, CardStatus.IN_REVIEW);

    assertThat(ermittle(JEDEN_TAG_EINS))
        .extracting(Ergebnis::vorhaben)
        .containsExactly(leer, fertig);
  }

  @Test
  @DisplayName("AK 18: nur Vorhaben erscheinen, keine gewöhnlichen Karten")
  void nurVorhaben() {
    karten.add(
        karte(neueId(), "lose Karte", CardType.CARD, BACKLOG, CardStatus.READY, null, false));
    Card epic = vorhaben();

    assertThat(ermittle(JEDEN_TAG_EINS)).extracting(Ergebnis::vorhaben).containsExactly(epic);
  }

  // --- Zählung AK 13 / AK 16 ---

  @Test
  @DisplayName("AK 13/16: offen außerhalb von In Review und Done, fertig darin, nur Arbeitspakete")
  void zaehlung() {
    Card epic = vorhaben();
    paket(epic, CardStatus.BACKLOG);
    paket(epic, CardStatus.READY);
    paket(epic, CardStatus.IN_PROGRESS);
    paket(epic, CardStatus.IN_REVIEW);
    paket(epic, CardStatus.DONE);
    paket(epic, CardStatus.DONE, null, true);
    plan(epic, null);
    fachplan(epic);
    dokument(epic, "[Idee] Einfall", null, BACKLOG, false);
    long mensch = neueId();
    zu(
        epic,
        karte(mensch, "[Mensch] Schritt", CardType.CARD, BACKLOG, CardStatus.READY, null, false));

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.total()).isEqualTo(6);
    assertThat(ergebnis.finished()).isEqualTo(2);
    assertThat(ergebnis.open()).isEqualTo(4);
  }

  @Test
  @DisplayName("AK 13/16: ein Paket ohne Status zählt nicht")
  void paketOhneStatusZaehltNicht() {
    Card epic = vorhaben();
    dokument(epic, "Paket ohne Status", null, BACKLOG, false);

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.lage()).isEqualTo(Lage.NO_PACKAGES);
    assertThat(ergebnis.total()).isZero();
  }

  @Test
  @DisplayName("AK 13/16: ein Dokument mit Status zählt nicht als Paket")
  void dokumentMitStatusZaehltNicht() {
    Card epic = vorhaben();
    long id = neueId();
    zu(epic, karte(id, "[Plan] mit Status", CardType.CARD, BACKLOG, CardStatus.READY, null, false));

    Ergebnis ergebnis = ermittle(epic, JEDEN_TAG_EINS);

    assertThat(ergebnis.lage()).isEqualTo(Lage.NO_PACKAGES);
    assertThat(ergebnis.total()).isZero();
  }

  @Test
  @DisplayName(
      "AK 11: Tage nur bei FORECAST, auch wenn die Zählung ohne Archiv offene Pakete findet")
  void tageNurBeiPrognose() {
    Card epic = vorhaben();
    offenePakete(epic, 1);
    Map<Long, Set<Card>> ohneGeschnittene = Map.of(epic.requireId(), Set.of());

    Ergebnis ergebnis =
        VorhabenLage.ermittle(karten, ohneArchiv(), ohneGeschnittene, SPALTEN, JEDEN_TAG_EINS)
            .getFirst();

    assertThat(ergebnis.lage()).isEqualTo(Lage.NO_PACKAGES);
    assertThat(ergebnis.open()).isEqualTo(1);
    assertThat(ergebnis.tage()).isNull();
  }

  // --- E12 ---

  @Test
  @DisplayName("E12: otherEpicsSharing zählt die anderen sichtbaren Vorhaben mit offenen Paketen")
  void andereVorhaben() {
    Card a = vorhaben();
    offenePakete(a, 1);
    Card b = vorhaben();
    offenePakete(b, 2);
    Card fertig = vorhaben();
    paket(fertig, CardStatus.DONE);
    vorhaben();
    Card archiviert = vorhaben(true);
    offenePakete(archiviert, 1);

    List<Ergebnis> ergebnisse = ermittle(JEDEN_TAG_EINS);

    assertThat(ergebnisse).extracting(Ergebnis::otherEpicsSharing).containsExactly(1, 1, 2, 2);
  }
}
