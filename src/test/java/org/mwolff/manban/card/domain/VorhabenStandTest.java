package org.mwolff.manban.card.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.domain.VorhabenStand.Dokument;
import org.mwolff.manban.card.domain.VorhabenStand.Ergebnis;
import org.mwolff.manban.card.domain.VorhabenStand.Paket;

/**
 * Unit-Tests des Stands je Vorhaben (Issue #2001, Plan #1990 E1, E10; fachlich #1954 AK 3): Pakete,
 * Dokumente, Abschluss und Kostenkarten.
 *
 * <p>Die Zugehörigkeit samt Archiv steht hier von Hand, so wie {@code
 * EpicMembership.computeMitArchiv} sie liefert — wie in {@code KostenzuordnungTest}: {@code
 * EpicMembership} ist modulintern in {@code card.application}. Eine Karte ohne Herkunft hängt über
 * {@code parentId} am Vorhaben, eine mit Herkunft erbt es.
 */
// PMD.TooManyMethods: Das Paket verlangt je Fachregel einen eigenen Fall; eine kleine Testmethode
// je Fall bleibt lesbarer als eine Aufteilung auf mehrere Klassen (wie VorhabenLageTest).
@SuppressWarnings("PMD.TooManyMethods")
class VorhabenStandTest {

  private static final Instant FIXED = Instant.parse("2026-10-17T10:00:00Z");
  private static final long BACKLOG = 100L;
  private static final long DONE = 102L;
  private static final Map<Long, String> SPALTEN = Map.of(BACKLOG, "Backlog", DONE, "Done");

  private final List<Card> karten = new ArrayList<>();
  private final Map<Long, Set<Card>> mitArchiv = new LinkedHashMap<>();
  private long naechsteId = 1L;

  private static Card karte(
      long id,
      String titel,
      CardType type,
      long spalte,
      @Nullable CardStatus status,
      @Nullable Long parentId,
      @Nullable Long derivedFrom,
      boolean archiviert,
      Instant angelegt) {
    return new Card(
        id,
        1L,
        spalte,
        (int) id,
        titel,
        "Beschreibung " + id,
        0,
        archiviert,
        status == CardStatus.DONE ? angelegt.plusSeconds(60) : null,
        null,
        angelegt,
        angelegt,
        type,
        parentId,
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

  private Card vorhaben(boolean archiviert) {
    long id = neueId();
    Card epic =
        karte(id, "Vorhaben " + id, CardType.EPIC, BACKLOG, null, null, null, archiviert, FIXED);
    karten.add(epic);
    mitArchiv.put(id, new LinkedHashSet<>());
    return epic;
  }

  private Card vorhaben() {
    return vorhaben(false);
  }

  /** Legt die Karte an; ohne Herkunft hängt sie unmittelbar am Vorhaben. */
  private Card zu(
      Card epic,
      String titel,
      long spalte,
      @Nullable CardStatus status,
      @Nullable Card aus,
      boolean archiviert,
      Instant angelegt) {
    long id = neueId();
    Card karte =
        karte(
            id,
            titel,
            CardType.CARD,
            spalte,
            status,
            aus == null ? epic.requireId() : null,
            aus == null ? null : aus.requireId(),
            archiviert,
            angelegt);
    karten.add(karte);
    mitArchiv.get(epic.requireId()).add(karte);
    return karte;
  }

  private Card paket(Card epic, CardStatus status, @Nullable Card aus) {
    return zu(
        epic,
        "Paket " + naechsteId,
        status == CardStatus.DONE ? DONE : BACKLOG,
        status,
        aus,
        false,
        FIXED);
  }

  private Card plan(Card epic, @Nullable Card aus) {
    return zu(epic, "[Plan] Plan " + naechsteId, BACKLOG, null, aus, false, FIXED);
  }

  private Card fachplan(Card epic) {
    return zu(epic, "[Fachlich] Anforderung " + naechsteId, BACKLOG, null, null, false, FIXED);
  }

  private List<Ergebnis> ermittle() {
    return VorhabenStand.ermittle(karten, mitArchiv, SPALTEN);
  }

  private Ergebnis ermittle(Card epic) {
    return ermittle().stream().filter(e -> e.vorhaben().equals(epic)).findFirst().orElseThrow();
  }

  // --- Abschluss (E10, AK 3) ---

  @Test
  void einVorhabenOhnePaketIstNieAbgeschlossen() {
    Card epic = vorhaben();
    zu(epic, "[Fachlich] Anforderung", DONE, null, null, false, FIXED);

    Ergebnis stand = ermittle(epic);

    assertThat(stand.pakete()).isEmpty();
    assertThat(stand.abgeschlossen()).isFalse();
  }

  @Test
  void einVorhabenOhneJedeKarteIstNieAbgeschlossen() {
    Card epic = vorhaben();

    assertThat(ermittle(epic).abgeschlossen()).isFalse();
  }

  @Test
  void alleDokumenteUndPaketeInDoneSchliessenAb() {
    Card epic = vorhaben();
    Card anforderung = zu(epic, "[Fachlich] A", DONE, null, null, false, FIXED);
    Card plan = zu(epic, "[Plan] P", DONE, null, anforderung, false, FIXED);
    paket(epic, CardStatus.DONE, plan);
    paket(epic, CardStatus.DONE, plan);

    assertThat(ermittle(epic).abgeschlossen()).isTrue();
  }

  @Test
  void einPaketAusserhalbVonDoneHaeltDenAbschlussAuf() {
    Card epic = vorhaben();
    Card plan = zu(epic, "[Plan] P", DONE, null, null, false, FIXED);
    paket(epic, CardStatus.DONE, plan);
    paket(epic, CardStatus.IN_REVIEW, plan);

    assertThat(ermittle(epic).abgeschlossen()).isFalse();
  }

  @Test
  void einOffenesDokumentHaeltDenAbschlussAuf() {
    Card epic = vorhaben();
    Card plan = plan(epic, null);
    paket(epic, CardStatus.DONE, plan);

    assertThat(ermittle(epic).abgeschlossen()).isFalse();
  }

  @Test
  void einArchiviertesPaketZaehlt() {
    Card epic = vorhaben();
    Card plan = zu(epic, "[Plan] P", DONE, null, null, false, FIXED);
    Card archiviert = zu(epic, "Paket", DONE, CardStatus.DONE, plan, true, FIXED);

    Ergebnis stand = ermittle(epic);

    assertThat(stand.pakete()).extracting(Paket::nummer).containsExactly(archiviert.number());
    assertThat(stand.abgeschlossen()).isTrue();
  }

  /** E10: Ein neues Paket öffnet ein abgeschlossenes Vorhaben wieder. */
  @Test
  void einNeuesPaketOeffnetEinAbgeschlossenesVorhabenWieder() {
    Card epic = vorhaben();
    Card plan = zu(epic, "[Plan] P", DONE, null, null, false, FIXED);
    paket(epic, CardStatus.DONE, plan);
    assertThat(ermittle(epic).abgeschlossen()).isTrue();

    paket(epic, CardStatus.BACKLOG, plan);

    assertThat(ermittle(epic).abgeschlossen()).isFalse();
  }

  // --- Dokumente (AK 3) ---

  @Test
  void einDokumentInDoneOhneNachfahrIstNichtOffenAberUngeschnitten() {
    Card epic = vorhaben();
    Card plan = zu(epic, "[Plan] P", DONE, null, null, false, FIXED);

    assertThat(ermittle(epic).dokumente())
        .containsExactly(new Dokument(plan.number(), Kartenart.PLAN, false, false));
  }

  @Test
  void einArchiviertesDokumentIstNichtOffen() {
    Card epic = vorhaben();
    Card plan = zu(epic, "[Plan] P", BACKLOG, null, null, true, FIXED);
    paket(epic, CardStatus.DONE, plan);

    Ergebnis stand = ermittle(epic);

    assertThat(stand.dokumente())
        .containsExactly(new Dokument(plan.number(), Kartenart.PLAN, false, true));
    assertThat(stand.abgeschlossen()).isTrue();
  }

  @Test
  void geschnittenIstEinPlanMitPaketUndEinFachplanMitPlan() {
    Card epic = vorhaben();
    Card anforderung = fachplan(epic);
    Card plan = plan(epic, anforderung);
    Card ohnePaket = plan(epic, null);
    paket(epic, CardStatus.BACKLOG, plan);

    assertThat(ermittle(epic).dokumente())
        .extracting(Dokument::nummer, Dokument::art, Dokument::offen, Dokument::geschnitten)
        .containsExactly(
            tuple(anforderung.number(), Kartenart.FACHLICH, true, true),
            tuple(plan.number(), Kartenart.PLAN, true, true),
            tuple(ohnePaket.number(), Kartenart.PLAN, true, false));
  }

  @Test
  void eineIdeeIstWederDokumentNochPaket() {
    Card epic = vorhaben();
    zu(epic, "[Idee] Einfall", BACKLOG, null, null, false, FIXED);

    Ergebnis stand = ermittle(epic);

    assertThat(stand.dokumente()).isEmpty();
    assertThat(stand.pakete()).isEmpty();
  }

  @Test
  void hatAnforderungNurMitFachplan() {
    Card mitAnforderung = vorhaben();
    fachplan(mitAnforderung);
    Card nurPlan = vorhaben();
    plan(nurPlan, null);

    assertThat(ermittle(mitAnforderung).hatAnforderung()).isTrue();
    assertThat(ermittle(nurPlan).hatAnforderung()).isFalse();
  }

  @Test
  void eineArchivierteAnforderungZaehlt() {
    Card epic = vorhaben();
    zu(epic, "[Fachlich] A", BACKLOG, null, null, true, FIXED);

    assertThat(ermittle(epic).hatAnforderung()).isTrue();
  }

  /** Jedes Dokument war vor seinem Schnitt ungeschnitten; nur Pakete ohne Dokument nicht. */
  @Test
  void hatteUngeschnittenesDokumentHeisstEinDokumentGehoertDazu() {
    Card nurPakete = vorhaben();
    paket(nurPakete, CardStatus.DONE, null);
    Card geschnitten = vorhaben();
    Card plan = zu(geschnitten, "[Plan] P", DONE, null, null, true, FIXED);
    paket(geschnitten, CardStatus.DONE, plan);

    assertThat(ermittle(nurPakete).hatteUngeschnittenesDokument()).isFalse();
    assertThat(ermittle(geschnitten).hatteUngeschnittenesDokument()).isTrue();
  }

  // --- Pakete ---

  @Test
  void einPaketTraegtNummerDoneBeschreibungUndZeiten() {
    Card epic = vorhaben();
    Instant angelegt = FIXED.minusSeconds(3600);
    Card fertig = zu(epic, "Fertig", DONE, CardStatus.DONE, null, false, angelegt);
    Card offen = paket(epic, CardStatus.IN_PROGRESS, null);

    assertThat(ermittle(epic).pakete())
        .containsExactly(
            new Paket(
                fertig.number(),
                true,
                "Beschreibung " + fertig.number(),
                angelegt,
                angelegt.plusSeconds(60)),
            new Paket(offen.number(), false, "Beschreibung " + offen.number(), FIXED, null));
  }

  /** Ohne Status entscheidet die Spalte ({@code Arbeitspaket.effektivDone}). */
  @Test
  void einPaketOhneStatusIstDoneNachSeinerSpalte() {
    Card epic = vorhaben();
    Card inDone = zu(epic, "Ohne Status", DONE, null, null, false, FIXED);

    assertThat(ermittle(epic).pakete())
        .extracting(Paket::nummer, Paket::done)
        .containsExactly(tuple(inDone.number(), true));
  }

  @Test
  void paketeUndDokumenteStehenNachNummer() {
    Card epic = vorhaben();
    Card spaeter =
        karte(
            50L,
            "Paket 50",
            CardType.CARD,
            BACKLOG,
            CardStatus.BACKLOG,
            epic.requireId(),
            null,
            false,
            FIXED);
    Card frueher =
        karte(
            40L,
            "Paket 40",
            CardType.CARD,
            BACKLOG,
            CardStatus.BACKLOG,
            epic.requireId(),
            null,
            false,
            FIXED);
    karten.add(spaeter);
    karten.add(frueher);
    mitArchiv.get(epic.requireId()).add(spaeter);
    mitArchiv.get(epic.requireId()).add(frueher);

    assertThat(ermittle(epic).pakete()).extracting(Paket::nummer).containsExactly(40, 50);
  }

  // --- Vorhaben ---

  @Test
  void auchArchivierteVorhabenStehenDaUndDieReihenfolgeFolgtDerNummer() {
    Card zweites = karte(9L, "Vorhaben 9", CardType.EPIC, BACKLOG, null, null, null, true, FIXED);
    karten.add(zweites);
    mitArchiv.put(9L, new LinkedHashSet<>());
    Card erstes = vorhaben();

    assertThat(ermittle()).extracting(Ergebnis::vorhaben).containsExactly(erstes, zweites);
  }

  @Test
  void einVorhabenOhneEintragInDerZugehoerigkeitIstLeer() {
    karten.add(karte(7L, "Vorhaben 7", CardType.EPIC, BACKLOG, null, null, null, false, FIXED));

    Ergebnis stand = ermittle().getFirst();

    assertThat(stand.pakete()).isEmpty();
    assertThat(stand.dokumente()).isEmpty();
    assertThat(stand.kostenkarten()).isEmpty();
    assertThat(stand.fruehesteKostenkarteAngelegt()).isNull();
  }

  // --- Kostenkarten (E1) ---

  @Test
  void dieKostenkartenSindDieKartenMitDiesemKostenvorhaben() {
    Card epic = vorhaben();
    Card anforderung = fachplan(epic);
    Card plan = plan(epic, anforderung);
    Card paket = paket(epic, CardStatus.BACKLOG, plan);

    assertThat(ermittle(epic).kostenkarten())
        .containsExactly(anforderung.number(), plan.number(), paket.number());
  }

  /** E1: Eine Karte in zwei Vorhaben zählt nur beim unmittelbaren. */
  @Test
  void eineKarteInZweiVorhabenIstNurBeimUnmittelbarenKostenkarte() {
    Card geerbt = vorhaben();
    Card unmittelbar = vorhaben();
    Card plan = plan(geerbt, null);
    Card paket =
        karte(
            neueId(),
            "Paket",
            CardType.CARD,
            BACKLOG,
            CardStatus.BACKLOG,
            unmittelbar.requireId(),
            plan.requireId(),
            false,
            FIXED);
    karten.add(paket);
    mitArchiv.get(geerbt.requireId()).add(paket);
    mitArchiv.get(unmittelbar.requireId()).add(paket);

    assertThat(ermittle(geerbt).kostenkarten()).containsExactly(plan.number());
    assertThat(ermittle(unmittelbar).kostenkarten()).containsExactly(paket.number());
    assertThat(ermittle(geerbt).pakete()).extracting(Paket::nummer).containsExactly(paket.number());
  }

  @Test
  void dieFruehesteAnlagezeitEinerKostenkarte() {
    Card epic = vorhaben();
    Instant frueh = FIXED.minusSeconds(7200);
    Card plan = zu(epic, "[Plan] P", BACKLOG, null, null, false, FIXED);
    zu(epic, "Paket", BACKLOG, CardStatus.BACKLOG, plan, true, frueh);

    assertThat(ermittle(epic).fruehesteKostenkarteAngelegt()).isEqualTo(frueh);
  }
}
