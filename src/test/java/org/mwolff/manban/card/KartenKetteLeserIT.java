package org.mwolff.manban.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.card.application.ActorContext.ActorStamp;
import org.mwolff.manban.card.application.CardActivityRepository;
import org.mwolff.manban.card.application.CardArchiveService;
import org.mwolff.manban.card.application.CardLabelEventRepository;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.KartenKetteLeser;
import org.mwolff.manban.card.application.KartenKetteLeser.GliedZustand;
import org.mwolff.manban.card.application.KartenKetteLeser.KartenKette;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenAktivitaet;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenGlied;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenKarte;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenLabelEreignis;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Lese-Fassade der Entscheidungskette gegen die echte Datenbank (Issue #2114, Plan #2108 E1,
 * E6, E12).
 *
 * <p>PIT und JaCoCo schließen die Adapter aus; dass die nativen Abfragen und ihre regulären
 * Ausdrücke unter Postgres das Richtige finden, belegt nur dieser Test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KartenKetteLeserIT extends AbstractIntegrationTest {

  private static final Instant JETZT = Instant.parse("2026-11-10T12:00:00Z");
  private static final ActorStamp TOKEN =
      new ActorStamp(CardActivityOrigin.TOKEN, "Nachtlauf", "claude-opus-5-5", null);
  private static final ActorStamp SITZUNG =
      new ActorStamp(CardActivityOrigin.SESSION, null, null, null);

  @Autowired private KartenKetteLeser leser;
  @Autowired private CardRepository cards;
  @Autowired private CardArchiveService archiv;
  @Autowired private CardActivityRepository activity;
  @Autowired private CardLabelEventRepository labelEvents;
  @Autowired private JdbcTemplate jdbc;

  private long userId;
  private long projektId;
  private long boardA;
  private long spalteA;
  private long boardB;
  private long spalteB;
  private Ort fremdesProjekt;
  private int naechsteNummer = 1;

  @BeforeEach
  void seed() {
    userId =
        id(
            "INSERT INTO app_user (email, display_name)"
                + " VALUES ('kette@example.com', 'K') RETURNING id");
    projektId = projekt("P");
    jdbc.update(
        "INSERT INTO project_membership (project_id, user_id, role) VALUES (?, ?, 'OWNER')",
        projektId,
        userId);
    boardA = board(projektId, "A");
    spalteA = spalte(boardA, "Ready");
    boardB = board(projektId, "B");
    spalteB = spalte(boardB, "In review");
    long fremd = projekt("Fremd");
    long fremdesBoard = board(fremd, "F");
    fremdesProjekt = new Ort(fremd, fremdesBoard, spalte(fremdesBoard, "Ready"));
  }

  @Test
  void vorfahrenUndNachfahrenUeberMehrereBoardsDesselbenProjekts() {
    Card fachplan = karte(boardA, spalteA, "[Requirement] Anlass", null, null);
    Card plan = karte(boardB, spalteB, "[Plan] Weg", null, fachplan.requireId());
    Card paket = karte(boardA, spalteA, "Paket", null, plan.requireId());
    Card kind = karte(boardB, spalteB, "Nacharbeit", null, paket.requireId());
    Card enkel = karte(boardA, spalteA, "Noch eine", null, kind.requireId());

    KartenKette kette = leser.lade(paket.requireId());

    assertThat(kette.karte().spalte()).isEqualTo("Ready");
    assertThat(kette.karte().spaltenSchluessel()).isEqualTo("READY");
    assertThat(kette.vorfahren())
        .extracting(g -> karteVon(g).titel(), g -> karteVon(g).art(), g -> karteVon(g).spalte())
        .containsExactly(
            tuple("[Plan] Weg", "PLAN", "In review"),
            tuple("[Requirement] Anlass", "FACHLICH", "Ready"));
    assertThat(kette.nachfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(kind.number(), enkel.number());
  }

  @Test
  void vorfahrNurUeberDieZeilenPlanUndFachlicheQuelleGefunden() {
    Card fachplan = karte(boardA, spalteA, "[Requirement] Anlass", null, null);
    Card plan = karte(boardB, spalteB, "[Plan] Weg", null, null);
    Card paket =
        karte(
            boardA,
            spalteA,
            "Paket",
            "## Kontext\nPlan: Issue #"
                + plan.number()
                + "\nFachliche Quelle: Issue #"
                + fachplan.number()
                + "\n",
            null);

    assertThat(leser.lade(paket.requireId()).vorfahren())
        .extracting(g -> karteVon(g).id())
        .containsExactly(plan.requireId(), fachplan.requireId());
  }

  @Test
  void dieZeileRequirementGiltWieFachlicheQuelle() {
    Card fachplan = karte(boardA, spalteA, "[Requirement] Cause", null, null);
    Card paket = karte(boardA, spalteA, "Paket", "Requirement: Issue #" + fachplan.number(), null);

    assertThat(leser.lade(paket.requireId()).vorfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(fachplan.number());
  }

  @Test
  void vorfahrInAnderemProjektOhneTitel() {
    Card fremd = karteIn(fremdesProjekt, "Geheim", "Geheim", null);
    Card paket = karte(boardA, spalteA, "Paket", null, fremd.requireId());

    assertThat(leser.lade(paket.requireId()).vorfahren())
        .containsExactly(new KettenGlied(GliedZustand.ANDERES_PROJEKT, null, null));
  }

  @Test
  void eineGleicheNummerInAnderemProjektWirdUeberDieZeilenNichtGefunden() {
    Card fremd = karteIn(fremdesProjekt, "Geheim", null, null);
    Card paket = karte(boardA, spalteA, "Paket", "Plan: Issue #" + fremd.number(), null);

    List<KettenGlied> vorfahren = leser.lade(paket.requireId()).vorfahren();

    assertThat(vorfahren)
        .containsExactly(new KettenGlied(GliedZustand.GELOESCHT, fremd.number(), null));
  }

  @Test
  void vorfahrImPapierkorbNenntNurDieNummerUndDieKetteGehtWeiter() {
    Card fachplan = karte(boardA, spalteA, "[Requirement] Anlass", null, null);
    Card plan = karte(boardA, spalteA, "[Plan] Weg", null, fachplan.requireId());
    Card paket = karte(boardA, spalteA, "Paket", null, plan.requireId());
    cards.softDelete(plan.requireId(), JETZT);

    List<KettenGlied> vorfahren = leser.lade(paket.requireId()).vorfahren();

    assertThat(vorfahren.getFirst())
        .isEqualTo(new KettenGlied(GliedZustand.PAPIERKORB, plan.number(), null));
    assertThat(vorfahren.get(1).nummer()).isEqualTo(fachplan.number());
  }

  @Test
  void endgueltigGeloeschterVorfahrMitNummer() {
    Card plan = karte(boardB, spalteB, "[Plan] Weg", null, null);
    Card paket = karte(boardA, spalteA, "Paket", null, plan.requireId());

    archiv.purge(userId, plan.requireId());

    assertThat(leser.lade(paket.requireId()).vorfahren())
        .containsExactly(new KettenGlied(GliedZustand.GELOESCHT, plan.number(), null));
  }

  @Test
  void ringInDenVorfahrenEndet() {
    Card a = karte(boardA, spalteA, "A", null, null);
    Card b = karte(boardA, spalteA, "B", null, a.requireId());
    cards.save(cards.findById(a.requireId()).orElseThrow().withDerivedFrom(b.requireId()));

    assertThat(leser.lade(a.requireId()).vorfahren())
        .extracting(KettenGlied::nummer)
        .containsExactly(b.number());
  }

  @Test
  void einArchivierterVorfahrErscheintMitSeinenAngaben() {
    Card plan = karte(boardA, spalteA, "[Plan] Weg", null, null);
    Card paket = karte(boardA, spalteA, "Paket", null, plan.requireId());
    cards.save(cards.findById(plan.requireId()).orElseThrow().asArchived());

    assertThat(leser.lade(paket.requireId()).vorfahren())
        .extracting(g -> karteVon(g).titel(), g -> karteVon(g).archiviert())
        .containsExactly(tuple("[Plan] Weg", true));
  }

  @Test
  void einZugInEineSpalteAusserhalbDesProzessesOhneStatusTraegtKeinenSchluessel() {
    Card paket = karte(boardA, spalteA, "Paket", null, null);
    activity.add(
        paket.requireId(),
        userId,
        CardActivityType.MOVED,
        "Verschoben nach Anstehend",
        JETZT,
        SITZUNG);

    assertThat(leser.lade(paket.requireId()).aktivitaeten())
        .extracting(KettenAktivitaet::detail, KettenAktivitaet::spaltenSchluessel)
        .containsExactly(tuple("Verschoben nach Anstehend", null));
  }

  @Test
  void ideenOhnePassendeNummerWerdenNichtGeladen() {
    Card plan = karte(boardA, spalteA, "[Plan] Weg", null, null);
    Card paket = karte(boardA, spalteA, "Paket", null, plan.requireId());
    Card passend =
        karte(boardB, spalteB, "[Idea] Passt", "Bahn: 3\nErgebnis: Issue #" + plan.number(), null);
    Card englisch =
        karte(boardA, spalteA, "[Idea] English", "Result: Issue #" + paket.number() + "\n", null);
    karte(boardA, spalteA, "[Idea] Andere Nummer", "Ergebnis: Issue #9" + plan.number(), null);
    karte(
        boardA, spalteA, "[Idea] Laengere Nummer", "Ergebnis: Issue #" + plan.number() + "0", null);
    karte(boardA, spalteA, "[Idea] Fliesstext", "Das Ergebnis: Issue #" + plan.number(), null);
    karte(boardA, spalteA, "[Idea] Andere Zeile", "Quelle: Issue #" + plan.number(), null);
    karte(boardA, spalteA, "[Plan] Keine Idee", "Ergebnis: Issue #" + plan.number(), null);
    Card papierkorb =
        karte(boardA, spalteA, "[Idea] Weg", "Ergebnis: Issue #" + plan.number(), null);
    cards.softDelete(papierkorb.requireId(), JETZT);
    karteIn(fremdesProjekt, "[Idea] Fremd", "Ergebnis: Issue #" + plan.number(), null);

    assertThat(leser.lade(paket.requireId()).ideen())
        .extracting(KettenKarte::id)
        .containsExactlyInAnyOrder(passend.requireId(), englisch.requireId());
  }

  @Test
  void aktivitaetenUndLabelEreignisseNurDerKartenDerKette() {
    Card plan = karte(boardA, spalteA, "[Plan] Weg", null, null);
    Card paket = karte(boardA, spalteA, "Paket", null, plan.requireId());
    Card unbeteiligt = karte(boardA, spalteA, "Unbeteiligt", null, null);
    activity.add(plan.requireId(), userId, CardActivityType.UPDATED, "spaet", JETZT, SITZUNG);
    activity.add(
        paket.requireId(),
        userId,
        CardActivityType.UPDATED,
        "frueh",
        JETZT.minusSeconds(1),
        SITZUNG);
    activity.add(unbeteiligt.requireId(), userId, CardActivityType.UPDATED, "x", JETZT, SITZUNG);
    labelEvents.add(paket.requireId(), "kit:nightrun", JETZT, SITZUNG, userId);
    labelEvents.add(unbeteiligt.requireId(), "fremd", JETZT, SITZUNG, userId);

    KartenKette kette = leser.lade(paket.requireId());

    assertThat(kette.aktivitaeten())
        .extracting(KettenAktivitaet::kartenId, KettenAktivitaet::detail)
        .containsExactly(tuple(paket.requireId(), "frueh"), tuple(plan.requireId(), "spaet"));
    assertThat(kette.labelEreignisse())
        .extracting(KettenLabelEreignis::kartenId, KettenLabelEreignis::labelName)
        .containsExactly(tuple(paket.requireId(), "kit:nightrun"));
  }

  @Test
  void einZugMitStatusTraegtDenStatusAlsSchluessel() {
    Card paket = karte(boardA, spalteA, "Paket", null, null);
    activity.add(
        paket.requireId(),
        userId,
        CardActivityType.MOVED,
        "Verschoben nach Anstehend",
        JETZT,
        TOKEN,
        org.mwolff.manban.card.domain.CardStatus.READY);

    assertThat(leser.lade(paket.requireId()).aktivitaeten())
        .extracting(KettenAktivitaet::origin, KettenAktivitaet::spaltenSchluessel)
        .containsExactly(tuple("TOKEN", "READY"));
  }

  @Test
  void eineUeberTokenAngelegteKarteMitAutorModellMachtDasProjektZumKitProjekt() {
    assertThat(leser.traegtKitKarte(projektId)).isFalse();

    Card karte = karte(boardA, spalteA, "Paket", "## Kontext\nAutor-Modell: claude\n", null);
    activity.add(karte.requireId(), userId, CardActivityType.CREATED, "angelegt", JETZT, TOKEN);

    assertThat(leser.traegtKitKarte(projektId)).isTrue();
    assertThat(leser.traegtKitKarte(fremdesProjekt.projekt())).isFalse();
  }

  @Test
  void auchDieEnglischeZeileUndDasPlanModellGelten() {
    Card englisch = karte(boardA, spalteA, "Paket", "Author-Model: claude", null);
    activity.add(englisch.requireId(), userId, CardActivityType.CREATED, "a", JETZT, TOKEN);
    assertThat(leser.traegtKitKarte(projektId)).isTrue();

    jdbc.update(
        "UPDATE card SET description = 'Plan-Modell: x' WHERE id = ?", englisch.requireId());
    assertThat(leser.traegtKitKarte(projektId)).isTrue();

    jdbc.update("UPDATE card SET description = 'Plan-Model: x' WHERE id = ?", englisch.requireId());
    assertThat(leser.traegtKitKarte(projektId)).isTrue();
  }

  @Test
  void eineVonHandAngelegteKarteMitDerZeileMachtKeinKitProjekt() {
    Card karte = karte(boardA, spalteA, "Paket", "Autor-Modell: claude", null);
    activity.add(karte.requireId(), userId, CardActivityType.CREATED, "a", JETZT, SITZUNG);

    assertThat(leser.traegtKitKarte(projektId)).isFalse();
  }

  @Test
  void eineUeberTokenAngelegteKarteOhneZeileMachtKeinKitProjekt() {
    Card karte = karte(boardA, spalteA, "Paket", "Das Autor-Modell: claude", null);
    activity.add(karte.requireId(), userId, CardActivityType.CREATED, "a", JETZT, TOKEN);

    assertThat(leser.traegtKitKarte(projektId)).isFalse();
  }

  @Test
  void einSpaetererTokenZugMachtEineVonHandAngelegteKarteNichtZurKitKarte() {
    Card karte = karte(boardA, spalteA, "Paket", "Autor-Modell: claude", null);
    activity.add(karte.requireId(), userId, CardActivityType.UPDATED, "a", JETZT, TOKEN);

    assertThat(leser.traegtKitKarte(projektId)).isFalse();
  }

  @Test
  void eineKarteImPapierkorbZaehltNichtAlsKitKarte() {
    Card karte = karte(boardA, spalteA, "Paket", "Autor-Modell: claude", null);
    activity.add(karte.requireId(), userId, CardActivityType.CREATED, "a", JETZT, TOKEN);
    cards.softDelete(karte.requireId(), JETZT);

    assertThat(leser.traegtKitKarte(projektId)).isFalse();
  }

  /** Projekt, Board und Spalte, in denen eine Karte liegt. */
  private record Ort(long projekt, long board, long spalte) {}

  private static KettenKarte karteVon(KettenGlied glied) {
    KettenKarte karte = glied.karte();
    assertThat(karte).isNotNull();
    return karte;
  }

  private Card karte(
      long board,
      long spalte,
      String titel,
      @Nullable String beschreibung,
      @Nullable Long herkunft) {
    return speichere(projektId, board, spalte, titel, beschreibung, herkunft);
  }

  private Card karteIn(
      Ort ort, String titel, @Nullable String beschreibung, @Nullable Long herkunft) {
    return speichere(ort.projekt(), ort.board(), ort.spalte(), titel, beschreibung, herkunft);
  }

  private Card speichere(
      long projekt,
      long board,
      long spalte,
      String titel,
      @Nullable String beschreibung,
      @Nullable Long herkunft) {
    int nummer = naechsteNummer;
    naechsteNummer++;
    return cards.save(
        new Card(
            null,
            board,
            spalte,
            nummer,
            titel,
            beschreibung,
            nummer,
            false,
            null,
            null,
            JETZT,
            JETZT,
            CardType.CARD,
            null,
            null,
            null,
            projekt,
            null,
            herkunft,
            null,
            null));
  }

  private long projekt(String name) {
    return id("INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id", name, userId);
  }

  private long board(long projekt, String name) {
    return id("INSERT INTO board (project_id, name) VALUES (?, ?) RETURNING id", projekt, name);
  }

  private long spalte(long board, String name) {
    return id(
        "INSERT INTO board_column (board_id, name, position) VALUES (?, ?, 0) RETURNING id",
        board,
        name);
  }

  private long id(String sql, Object... args) {
    Long wert = jdbc.queryForObject(sql, Long.class, args);
    if (wert == null) {
      throw new IllegalStateException("kein Schluessel: " + sql);
    }
    return wert;
  }
}
