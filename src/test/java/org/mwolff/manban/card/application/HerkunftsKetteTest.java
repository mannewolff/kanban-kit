package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.CardBestandRepository.KarteImBestand;
import org.mwolff.manban.card.application.CardSearchService.Kette;
import org.mwolff.manban.card.application.CardSearchService.KettenGlied;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;

/**
 * Unit-Tests der Herkunftskette (Issue #1580, Plan #1574 E11–E13): reine Rechnung über eine
 * Kartenmenge, ohne Datenbank. Der Nachlader steht für {@code findByIds} und kennt die Karten
 * außerhalb der Board-Sicht — im Papierkorb und auf fremden Boards.
 */
class HerkunftsKetteTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");
  private static final long BOARD = 10L;
  private static final long FREMDES_BOARD = 11L;
  private static final long SPALTE = 20L;
  private static final long PROJEKT = 1L;
  private static final long FREMDES_PROJEKT = 2L;

  private final Map<Long, Card> ausserhalb = new HashMap<>();
  private final Map<Long, Integer> verloren = new HashMap<>();

  /** Karten außerhalb der Board-Liste, wie {@code findImBestand} sie liefert (Issue #1930). */
  private final Map<Long, KarteImBestand> bestand = new HashMap<>();

  private final List<Set<Long>> ladeaufrufe = new ArrayList<>();

  private static Card karte(
      long id, long board, int number, String title, @Nullable Long derivedFrom, boolean archived) {
    return new Card(
        id,
        board,
        SPALTE,
        number,
        title,
        null,
        0,
        archived,
        null,
        1L,
        FIXED,
        FIXED,
        CardType.CARD,
        null,
        null,
        null,
        1L,
        null,
        derivedFrom,
        null,
        null);
  }

  private static Card aufBoard(long id, int number, String title, @Nullable Long derivedFrom) {
    return karte(id, BOARD, number, title, derivedFrom, false);
  }

  private Kette kette(Card start, List<Card> boardKarten) {
    Function<Long, Optional<Card>> nachlader = id -> Optional.ofNullable(ausserhalb.get(id));
    return HerkunftsKette.berechne(
        start, boardKarten, nachlader, verloren, Map.of(SPALTE, "In Review"));
  }

  private static KettenGlied luecke(int number, int depth) {
    return new KettenGlied(number, null, null, null, false, depth, true);
  }

  @Test
  void dieKarteSelbst_traegtArtSpalteUndTiefeNull() {
    Card paket =
        new Card(
            3L,
            BOARD,
            SPALTE,
            30,
            "Paket",
            null,
            0,
            false,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.CARD,
            null,
            null,
            null,
            1L,
            null,
            null,
            null,
            CardStatus.READY);

    Kette k = kette(paket, List.of(paket));

    assertThat(k.karte())
        .isEqualTo(new KettenGlied(30, "Paket", "ARBEITSPAKET", "READY", false, 0, false));
    assertThat(k.vorfahren()).isEmpty();
    assertThat(k.nachfahren()).isEmpty();
  }

  @Test
  void vorfahren_reichenUeberArchivierteGliederBisZurWurzel() {
    Card fachlich = aufBoard(1L, 10, "[Fachlich] Anlass", null);
    Card plan = karte(2L, BOARD, 20, "[Plan] Weg", 1L, true);
    Card paket = aufBoard(3L, 30, "Paket", 2L);

    Kette k = kette(paket, List.of(fachlich, plan, paket));

    assertThat(k.vorfahren())
        .containsExactly(
            new KettenGlied(20, "[Plan] Weg", "PLAN", "IN_REVIEW", true, 1, false),
            new KettenGlied(10, "[Fachlich] Anlass", "FACHLICH", "IN_REVIEW", false, 2, false));
  }

  @Test
  void spalteUnbekannt_zaehltAlsBacklog() {
    Card karte =
        new Card(
            1L,
            BOARD,
            99L,
            10,
            "[Fachlich] X",
            null,
            0,
            false,
            null,
            1L,
            FIXED,
            FIXED,
            CardType.EPIC,
            null,
            null,
            null,
            1L,
            null,
            null,
            null,
            null);

    Kette k = kette(karte, List.of(karte));

    assertThat(k.karte().column()).isEqualTo("BACKLOG");
    assertThat(k.karte().kind()).isEqualTo("VORHABEN");
  }

  @Test
  void papierkorbVorfahr_istLuecke_undDerAufstiegReichtBisZumFachlichDarueber() {
    Card fachlich = aufBoard(1L, 10, "[Fachlich] Anlass", null);
    ausserhalb.put(2L, karte(2L, BOARD, 20, "[Plan] im Papierkorb", 1L, false));
    Card paket = aufBoard(3L, 30, "Paket", 2L);

    Kette k = kette(paket, List.of(fachlich, paket));

    assertThat(k.vorfahren())
        .containsExactly(
            luecke(20, 1),
            new KettenGlied(10, "[Fachlich] Anlass", "FACHLICH", "IN_REVIEW", false, 2, false));
  }

  @Test
  void zweiPapierkorbVorfahrenNacheinander_sindZweiLuecken() {
    Card fachlich = aufBoard(1L, 10, "[Fachlich] Anlass", null);
    ausserhalb.put(2L, karte(2L, BOARD, 20, "[Plan] weg", 1L, false));
    ausserhalb.put(4L, karte(4L, BOARD, 40, "Zwischen weg", 2L, false));
    Card paket = aufBoard(3L, 30, "Paket", 4L);

    Kette k = kette(paket, List.of(fachlich, paket));

    assertThat(k.vorfahren()).extracting(KettenGlied::number).containsExactly(40, 20, 10);
    assertThat(k.vorfahren()).extracting(KettenGlied::gap).containsExactly(true, true, false);
  }

  @Test
  void vorfahrAufFremdemBoard_istLuecke_undBeendetDieKette() {
    ausserhalb.put(1L, karte(1L, FREMDES_BOARD, 10, "[Fachlich] fremd", null, false));
    ausserhalb.put(2L, karte(2L, FREMDES_BOARD, 20, "[Plan] fremd", 1L, false));
    Card paket = aufBoard(3L, 30, "Paket", 2L);

    Kette k = kette(paket, List.of(paket));

    assertThat(k.vorfahren()).containsExactly(luecke(20, 1));
  }

  @Test
  void fremdesBoardMitGleicherIdJenseitsDesLongCaches_istDasselbeBoard() {
    // Board-IDs als Long jenseits des Caches: ein Referenzvergleich hielte das eigene Board für
    // fremd und brächte die Kette vorzeitig zum Ende.
    long board = 1000L;
    Card fachlich = karte(1L, Long.valueOf(1000L), 10, "[Fachlich] Anlass", null, false);
    ausserhalb.put(2L, karte(2L, Long.valueOf(1000L), 20, "[Plan] weg", 1L, false));
    Card paket = karte(3L, Long.valueOf(board), 30, "Paket", 2L, false);

    Kette k = kette(paket, List.of(fachlich, paket));

    assertThat(k.vorfahren()).extracting(KettenGlied::number).containsExactly(20, 10);
  }

  @Test
  void endgueltigGeloeschterVorfahrMitVerlorenerNummer_istLuecke_undBeendetDieKette() {
    Card plan = aufBoard(2L, 20, "[Plan] Weg", null);
    Card paket = aufBoard(3L, 30, "Paket", 2L);
    verloren.put(2L, 10);

    Kette k = kette(paket, List.of(plan, paket));

    assertThat(k.vorfahren())
        .containsExactly(
            new KettenGlied(20, "[Plan] Weg", "PLAN", "IN_REVIEW", false, 1, false), luecke(10, 2));
  }

  @Test
  void verloreneNummerAnDerKarteSelbst_istDerErsteVorfahr() {
    Card paket = aufBoard(3L, 30, "Paket", null);
    verloren.put(3L, 20);

    assertThat(kette(paket, List.of(paket)).vorfahren()).containsExactly(luecke(20, 1));
  }

  @Test
  void endgueltigGeloeschterVorfahrOhneVerloreneNummer_erscheintNicht() {
    Card paket = aufBoard(3L, 30, "Paket", null);

    assertThat(kette(paket, List.of(paket)).vorfahren()).isEmpty();
  }

  @Test
  void vorfahrNichtNachladbar_beendetDieKetteOhneLuecke() {
    Card paket = aufBoard(3L, 30, "Paket", 2L);

    assertThat(kette(paket, List.of(paket)).vorfahren()).isEmpty();
  }

  @Test
  void zyklus_endetVorDemErstenWiedersehen_inBeideRichtungen() {
    // Ring 1 -> 2 -> 3 -> 1 (derivedFrom), möglich seit #607.
    Card a = aufBoard(1L, 10, "A", 3L);
    Card b = aufBoard(2L, 20, "B", 1L);
    Card c = aufBoard(3L, 30, "C", 2L);

    Kette k = kette(a, List.of(a, b, c));

    assertThat(k.vorfahren()).extracting(KettenGlied::number).containsExactly(30, 20);
    assertThat(k.vorfahren()).extracting(KettenGlied::depth).containsExactly(1, 2);
    assertThat(k.nachfahren()).extracting(KettenGlied::number).containsExactly(20, 30);
    assertThat(k.nachfahren()).extracting(KettenGlied::depth).containsExactly(1, 2);
  }

  @Test
  void zyklusUeberDenPapierkorb_endetAuch() {
    Card a = aufBoard(1L, 10, "A", 2L);
    ausserhalb.put(2L, karte(2L, BOARD, 20, "B weg", 1L, false));

    assertThat(kette(a, List.of(a)).vorfahren()).containsExactly(luecke(20, 1));
  }

  @Test
  void nachfahren_inPraeorderMitTiefe_geschwisterNachNummer() {
    Card fachlich = aufBoard(1L, 10, "[Fachlich] Anlass", null);
    Card planB = aufBoard(5L, 25, "[Plan] B", 1L);
    Card planA = karte(2L, BOARD, 20, "[Plan] A", 1L, true);
    Card paketA2 = aufBoard(4L, 22, "Paket A2", 2L);
    Card paketA1 = aufBoard(3L, 21, "Paket A1", 2L);
    Card enkel = aufBoard(6L, 23, "Enkel", 3L);
    Card fremdeWurzel = aufBoard(7L, 99, "Unverbunden", null);

    Kette k =
        kette(fachlich, List.of(fremdeWurzel, planB, paketA2, enkel, fachlich, planA, paketA1));

    assertThat(k.nachfahren())
        .extracting(KettenGlied::number, KettenGlied::depth, KettenGlied::archived)
        .containsExactly(
            tuple(20, 1, true),
            tuple(21, 2, false),
            tuple(23, 3, false),
            tuple(22, 2, false),
            tuple(25, 1, false));
    assertThat(k.nachfahren()).noneMatch(KettenGlied::gap);
  }

  @Test
  void nachfahrImPapierkorb_fehltSamtTeilbaum_undFremdeNachfahrenFehlenStill() {
    // Karte 2 liegt im Papierkorb und steht darum nicht in der Board-Menge; ihr Kind 3 liegt auf
    // dem Board, hängt aber nur an 2. Nachfahren fremder Boards stehen nie in der Board-Menge.
    Card fachlich = aufBoard(1L, 10, "[Fachlich] Anlass", null);
    Card kindDesGeloeschten = aufBoard(3L, 30, "Enkel", 2L);
    Card sichtbar = aufBoard(4L, 40, "Sichtbar", 1L);

    Kette k = kette(fachlich, List.of(fachlich, kindDesGeloeschten, sichtbar));

    assertThat(k.nachfahren()).extracting(KettenGlied::number).containsExactly(40);
  }

  // --- vorfahrenJeKarte: die Herkunft der Board-Liste (Issue #1930) ---

  private void imBestand(Card c, boolean imPapierkorb, @Nullable Integer verloreneNummer) {
    bestand.put(c.requireId(), new KarteImBestand(c, imPapierkorb, verloreneNummer));
  }

  private static Card imProjekt(long id, long projekt, int number, @Nullable Long derivedFrom) {
    Card c = karte(id, FREMDES_BOARD, number, "Karte " + number, derivedFrom, false);
    return new Card(
        c.id(),
        c.boardId(),
        c.columnId(),
        c.number(),
        c.title(),
        c.description(),
        c.positionInColumn(),
        c.archived(),
        c.movedToDoneAt(),
        c.createdBy(),
        c.createdAt(),
        c.updatedAt(),
        c.type(),
        c.parentId(),
        c.shortcode(),
        c.dueDate(),
        projekt,
        c.externalKey(),
        c.derivedFromCardId(),
        c.requirementCardId(),
        c.status());
  }

  private Map<Long, List<HerkunftsGlied>> vorfahren(List<Card> boardKarten) {
    return HerkunftsKette.vorfahrenJeKarte(
        boardKarten,
        ids -> {
          ladeaufrufe.add(Set.copyOf(ids));
          return ids.stream().map(bestand::get).filter(Objects::nonNull).toList();
        },
        verloren,
        PROJEKT);
  }

  private static HerkunftsGlied sichtbar(int number, String title) {
    return new HerkunftsGlied(number, title, false, null);
  }

  @Test
  void vorfahrenJeKarte_ketteUeberDreiStufen_naechsterVorfahrZuerst() {
    Card idee = aufBoard(1L, 1, "[Idee] I", null);
    Card fachlich = aufBoard(2L, 2, "[Fachlich] F", 1L);
    Card plan = karte(3L, BOARD, 3, "[Plan] P", 2L, true);
    Card paket = aufBoard(4L, 4, "Paket", 3L);

    Map<Long, List<HerkunftsGlied>> v = vorfahren(List.of(idee, fachlich, plan, paket));

    assertThat(v.get(4L))
        .containsExactly(
            new HerkunftsGlied(3, "[Plan] P", true, null),
            sichtbar(2, "[Fachlich] F"),
            sichtbar(1, "[Idee] I"));
    assertThat(v.get(1L)).isEmpty();
    // Alles liegt auf dem Board: kein Nachladen.
    assertThat(ladeaufrufe).isEmpty();
  }

  @Test
  void vorfahrenJeKarte_vorfahrAufAnderemBoardDesselbenProjekts_mitTitel() {
    Card idee = karte(1L, FREMDES_BOARD, 1, "[Idee] I", null, false);
    imBestand(idee, false, null);
    Card fachlich = aufBoard(2L, 2, "[Fachlich] F", 1L);

    assertThat(vorfahren(List.of(fachlich)).get(2L)).containsExactly(sichtbar(1, "[Idee] I"));
  }

  @Test
  void vorfahrenJeKarte_papierkorbIstLuecke_undDieKetteGehtWeiter() {
    Card idee = karte(1L, FREMDES_BOARD, 1, "[Idee] I", null, false);
    imBestand(idee, false, null);
    Card fachlich = aufBoard(2L, 2, "[Fachlich] F", 1L);
    imBestand(fachlich, true, null);
    Card paket = aufBoard(3L, 3, "Paket", 2L);

    assertThat(vorfahren(List.of(paket)).get(3L))
        .containsExactly(
            new HerkunftsGlied(2, null, false, HerkunftsGlied.Luecke.PAPIERKORB),
            sichtbar(1, "[Idee] I"));
  }

  @Test
  void vorfahrenJeKarte_geloeschterVorfahrIstLueckeAmEnde() {
    // Auf dem Board aus der Map, außerhalb aus dem Bestand.
    Card plan = karte(2L, FREMDES_BOARD, 2, "[Plan] P", null, false);
    imBestand(plan, false, 77);
    Card paket = aufBoard(3L, 3, "Paket", 2L);
    Card waise = aufBoard(4L, 4, "Waise", null);
    verloren.put(4L, 66);

    Map<Long, List<HerkunftsGlied>> v = vorfahren(List.of(paket, waise));

    assertThat(v.get(3L))
        .containsExactly(
            sichtbar(2, "[Plan] P"),
            new HerkunftsGlied(77, null, false, HerkunftsGlied.Luecke.GELOESCHT));
    assertThat(v.get(4L))
        .containsExactly(new HerkunftsGlied(66, null, false, HerkunftsGlied.Luecke.GELOESCHT));
  }

  @Test
  void vorfahrenJeKarte_fremdesProjektEndetOhneGlied() {
    Card fremd = imProjekt(1L, FREMDES_PROJEKT, 1, null);
    imBestand(fremd, false, null);
    Card plan = imProjekt(2L, PROJEKT, 2, 1L);
    imBestand(plan, false, null);
    Card paket = aufBoard(3L, 3, "Paket", 2L);

    assertThat(vorfahren(List.of(paket)).get(3L)).containsExactly(sichtbar(2, "Karte 2"));
  }

  @Test
  void vorfahrenJeKarte_ringEndet_undUnbekannteIdEndetAuch() {
    Card a = aufBoard(1L, 1, "A", 2L);
    Card b = aufBoard(2L, 2, "B", 1L);
    Card c = aufBoard(3L, 3, "C", 99L);

    Map<Long, List<HerkunftsGlied>> v = vorfahren(List.of(a, b, c));

    assertThat(v.get(1L)).containsExactly(sichtbar(2, "B"));
    assertThat(v.get(2L)).containsExactly(sichtbar(1, "A"));
    assertThat(v.get(3L)).isEmpty();
  }

  @Test
  void vorfahrenJeKarte_eineLadeabfrageJeStufe_ueberAlleKarten() {
    // Zwei Pakete mit je eigener Kette über drei Stufen außerhalb des Boards; Stufe 3 teilen sie.
    Card wurzel = karte(1L, FREMDES_BOARD, 1, "W", null, false);
    Card mitteA = karte(2L, FREMDES_BOARD, 2, "MA", 1L, false);
    Card mitteB = karte(3L, FREMDES_BOARD, 3, "MB", 1L, false);
    Card obenA = karte(4L, FREMDES_BOARD, 4, "OA", 2L, false);
    Card obenB = karte(5L, FREMDES_BOARD, 5, "OB", 3L, false);
    for (Card c : List.of(wurzel, mitteA, mitteB, obenA, obenB)) {
      imBestand(c, false, null);
    }
    Card paketA = aufBoard(6L, 6, "PA", 4L);
    Card paketB = aufBoard(7L, 7, "PB", 5L);

    Map<Long, List<HerkunftsGlied>> v = vorfahren(List.of(paketA, paketB));

    assertThat(ladeaufrufe).containsExactly(Set.of(4L, 5L), Set.of(2L, 3L), Set.of(1L));
    assertThat(v.get(6L)).extracting(HerkunftsGlied::number).containsExactly(4, 2, 1);
    assertThat(v.get(7L)).extracting(HerkunftsGlied::number).containsExactly(5, 3, 1);
  }
}
