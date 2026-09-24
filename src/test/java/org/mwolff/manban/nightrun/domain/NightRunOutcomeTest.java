package org.mwolff.manban.nightrun.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Der Maßstab „nicht vollständig gelungen" (Issue #1078, Plan #1072 E2, AK 5), um die Stillefrist
 * erweitert (Issue #1091, AK 6/8 der fachlichen Quelle #1086).
 *
 * <p>Bis hierher lebte er im Browser ({@code frontend/src/lib/leitstand.ts}). Der
 * Plattform-Leitstand geht über alle Projekte und kann die Läufe nicht einzeln im Browser auswerten
 * — deshalb eine Wahrheit, und die liegt im Server.
 *
 * <p>Die Reihenfolge der Regeln trägt eine Aussage und wird hier Fall für Fall festgehalten: die
 * Stillefrist schlägt alles, danach „läuft noch", danach der harte Abbruch, danach rot vor gelb vor
 * grau-mit-Fehlerklasse — und erst danach der Lauf ohne Arbeit.
 *
 * <p>Seit Issue #1185 ist der Lauf ohne Arbeit <b>ein</b> Fall: Jeder Grund ergibt {@link
 * NightRunOutcome.Verdict#NO_WORK} und keine Störung, gleich ob der Runner ihn meldete oder der
 * Server auf {@link NightRunOutcome#GRUND_UNBEKANNT} zurückfiel. Was #1121 am Rückfall festmachte —
 * dahinter könne ein echtes Problem stecken —, sagen jetzt die Pakete: Ein zurückgestelltes Paket
 * steht in der Rangfolge <em>vor</em> dem Grund und macht den Lauf zu {@code WAITING}.
 */
// PMD.TooManyMethods: Testklasse — jede Methode ist ein Fall der Rangfolge, und Faelle werden nicht
// zusammengelegt, um eine Zahl zu druecken. Issue #1143 bringt die vier Faelle des Abbruchgrunds
// dazu und reisst damit die Schwelle von 30. Dieselbe Begruendung wie an DisruptionServiceTest.
@SuppressWarnings("PMD.TooManyMethods")
class NightRunOutcomeTest {

  private static final Instant FIXED = Instant.parse("2026-09-19T22:00:00Z");

  private static final Duration FRIST = Duration.ofMinutes(90);

  /** Ein Grund, wie der Runner ihn meldet (Kit #744) — im Unterschied zum Rückfall des Servers. */
  private static final String GEMELDET = "Ready ist leer — nichts zu tun.";

  /** Ein Abbruchgrund, wie der Runner ihn meldet (Issue #1142). */
  private static final String ABBRUCH =
      "Dirty-Guard: uncommittete Reste in src/main/java/Foo.java, src/test/java/FooTest.java";

  /**
   * Die Fälle ohne Zeitbezug messen an einem frischen Lebenszeichen: {@code updatedAt} ist {@code
   * jetzt}, die Frist greift dort nie. So bleiben die Aussagen der Fälle vor Issue #1091
   * unverändert.
   */
  private static NightRunOutcome befund(
      boolean complete, @Nullable String noWorkReason, List<NightRunItem> items) {
    return befund(complete, noWorkReason, NightRunMode.IMPLEMENTATION, items);
  }

  /** Derselbe Fall mit einer anderen Laufart — sie entscheidet unter gleichrangigen Paketen. */
  private static NightRunOutcome befund(
      boolean complete,
      @Nullable String noWorkReason,
      NightRunMode mode,
      List<NightRunItem> items) {
    return NightRunOutcome.of(
        complete, noWorkReason, null, mode, items, FIXED, FIXED, FIXED, FRIST);
  }

  /** Derselbe Fall mit einem gemeldeten Abbruchgrund (Issue #1143). */
  private static NightRunOutcome abgebrochen(boolean complete, List<NightRunItem> items) {
    return abgebrochen(complete, items, FIXED);
  }

  /** Und mit einem eigenen Bezugszeitpunkt, an dem die Stillefrist gemessen wird. */
  private static NightRunOutcome abgebrochen(
      boolean complete, List<NightRunItem> items, Instant jetzt) {
    return NightRunOutcome.of(
        complete, null, ABBRUCH, NightRunMode.IMPLEMENTATION, items, FIXED, FIXED, jetzt, FRIST);
  }

  private static NightRunItem item(
      int cardNumber, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        1L,
        7L,
        3L,
        FIXED,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        cardNumber,
        "Paket " + cardNumber,
        state,
        errorClass,
        1000L,
        null,
        null,
        null,
        List.of());
  }

  @Test
  void einUnabgeschlossenerLaufLaeuftNoch() {
    var outcome = befund(false, null, List.of(item(1, NightRunState.GREEN, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  /** „Läuft noch" schlägt alles andere — auch ein rotes Paket macht den Lauf nicht zur Störung. */
  @Test
  void einUnabgeschlossenerLaufBleibtLaufendTrotzRotemPaket() {
    var outcome =
        befund(false, null, List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
  }

  /**
   * Issue #1121: Der <b>gemeldete</b> Grund ist der eigene Ausgang „nichts zu tun" — und keine
   * Störung. Wer ein Projekt nachts bewusst ruhen lässt, räumte sonst jeden Morgen eine Meldung
   * weg.
   *
   * <p>Zugleich AK 7 der fachlichen Quelle #1074: Der regulär beendete Lauf ohne Arbeit bleibt
   * {@code NO_WORK}, auch nachdem der Abbruchgrund eine Stufe vor ihm bekommen hat (Issue #1143).
   */
  @Test
  void einLaufOhneArbeitMitGemeldetemGrundHatNichtsZuTun() {
    var outcome = befund(true, GEMELDET, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.NO_WORK);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isEqualTo(GEMELDET);
    assertThat(outcome.abortReason()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  /**
   * Issue #1185, Kriterium 1: Der Rückfall des Servers ist <b>kein</b> Mangel des Laufs. Ein Lauf,
   * der regulär zu Ende kam und nichts Bearbeitbares vorfand, ist auch ohne gemeldeten Grund ruhig
   * — bis #1121 quittierte ein alter Runner oder der Upload-Weg dafür jeden Morgen eine Störung.
   *
   * <p>Der Fall <em>kippt</em> gegenüber #1121: Dort war genau dieser Text {@code FAILED}.
   */
  @Test
  void einLaufOhneArbeitOhneGemeldetenGrundHatNichtsZuTun() {
    var outcome = befund(true, NightRunOutcome.GRUND_UNBEKANNT, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.NO_WORK);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isEqualTo(NightRunOutcome.GRUND_UNBEKANNT);
    assertThat(outcome.isDisruption()).isFalse();
  }

  /**
   * Issue #1185, Kriterium 3: Das maßgebliche Paket steht <b>vor</b> dem Grund ohne Arbeit. Beide
   * Grunde-Arten stehen hier — die Rangfolge gilt für den gemeldeten wie für den Rückfall, und der
   * Ausgang unterscheidet sie nicht mehr.
   *
   * <p>Der Fall <em>kippt</em> gegenüber #1121: Dort verdrängte der Grund das rote Paket, und ein
   * Lauf mit gemeldetem Grund und rotem Paket fiel als {@code NO_WORK} aus der Störungsliste.
   */
  @Test
  void einRotesPaketSchlaegtDenGrundOhneArbeit() {
    var rot = List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    var gemeldet = befund(true, GEMELDET, rot);
    var rueckfall = befund(true, NightRunOutcome.GRUND_UNBEKANNT, rot);

    assertThat(gemeldet.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(gemeldet.decisiveItem().cardNumber()).isEqualTo(1);
    assertThat(gemeldet.noWorkReason())
        .as("das Paket ist die Begründung; ein Grund daneben wäre eine zweite")
        .isNull();
    assertThat(rueckfall.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(rueckfall.decisiveItem().cardNumber()).isEqualTo(1);
  }

  /**
   * Der Anlass des Vorhabens (Issue #1175, Kriterium 3): Ein Lauf, der alle seine Pakete
   * zurückstellte, meldet keinen Grund — der Server fällt auf {@link
   * NightRunOutcome#GRUND_UNBEKANNT} zurück. Maßgeblich ist trotzdem das zurückgestellte Paket, und
   * der Lauf ist „mit Vorbehalt" statt rot.
   */
  @Test
  void saemtlichZurueckgestelltePaketeWartenVorDemGrundOhneArbeit() {
    var outcome =
        befund(
            true,
            NightRunOutcome.GRUND_UNBEKANNT,
            List.of(
                item(6, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET),
                item(7, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.WAITING);
    assertThat(outcome.decisiveItem())
        .isEqualTo(
            new NightRunOutcome.DecisiveItem(
                6, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET));
    assertThat(outcome.noWorkReason()).isNull();
    assertThat(outcome.isDisruption()).isTrue();
  }

  /**
   * Die Gegenprobe zum Fall davor (Kriterium 1): Grau <em>ohne</em> Fehlerklasse ist ein
   * übergangenes Paket und nie maßgeblich. Ein Lauf, der nur solche trägt, hat nichts abgearbeitet
   * — der Grund gilt, und der Lauf ist ruhig.
   */
  @Test
  void nurUebergangenePaketeLassenDenLaufOhneArbeit() {
    var outcome =
        befund(
            true,
            NightRunOutcome.GRUND_UNBEKANNT,
            List.of(item(8, NightRunState.GREY, null), item(9, NightRunState.GREY, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.NO_WORK);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isEqualTo(NightRunOutcome.GRUND_UNBEKANNT);
    assertThat(outcome.isDisruption()).isFalse();
  }

  /** „Läuft noch" schlägt auch den gemeldeten Grund — der Ausgang steht erst am Ende fest. */
  @Test
  void einUnabgeschlossenerLaufLaeuftNochTrotzGemeldetemGrund() {
    var outcome = befund(false, GEMELDET, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(outcome.noWorkReason()).isNull();
  }

  @Test
  void einLeererGrundZaehltNichtAlsLaufOhneArbeit() {
    var outcome = befund(true, "   ", List.of(item(1, NightRunState.GREEN, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.noWorkReason()).isNull();
  }

  @Test
  void einRotesPaketLaesstDenLaufScheitern() {
    var outcome =
        befund(true, null, List.of(item(4, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem())
        .isEqualTo(
            new NightRunOutcome.DecisiveItem(4, NightRunState.RED, NightRunErrorClass.CHECKS_RED));
  }

  @Test
  void einGelbesPaketLaesstDenLaufScheitern() {
    var outcome =
        befund(true, null, List.of(item(5, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(5);
  }

  /** Zurückgestellt ist kein Scheitern, aber auch kein Gelingen — es wartet auf etwas. */
  @Test
  void einGrauesPaketMitFehlerklasseWartet() {
    var outcome =
        befund(
            true, null, List.of(item(6, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.WAITING);
    assertThat(outcome.decisiveItem().errorClass()).isEqualTo(NightRunErrorClass.DEPENDENCY_UNMET);
    assertThat(outcome.isDisruption()).isTrue();
  }

  /** Grau ohne Fehlerklasse ist ein übergangenes Paket — der Lauf hat es nicht angefasst. */
  @Test
  void grauOhneFehlerklasseIstKeineStoerung() {
    var outcome =
        befund(
            true,
            null,
            List.of(item(1, NightRunState.GREEN, null), item(2, NightRunState.GREY, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  @Test
  void einLaufGanzOhnePaketIstGelungen() {
    var outcome = befund(true, null, List.of());

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.decisiveItem()).isNull();
  }

  @Test
  void rotSchlaegtGelb() {
    var outcome =
        befund(
            true,
            null,
            List.of(
                item(1, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED),
                item(2, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(2);
    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  @Test
  void gelbSchlaegtGrauMitFehlerklasse() {
    var outcome =
        befund(
            true,
            null,
            List.of(
                item(1, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION),
                item(2, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(2);
    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  /** Innerhalb einer Farbe entscheidet die Laufreihenfolge, nicht die Kartennummer. */
  @Test
  void innerhalbEinerFarbeGiltDasErsteInLaufreihenfolge() {
    var outcome =
        befund(
            true,
            null,
            List.of(
                item(9, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(2, NightRunState.RED, NightRunErrorClass.CHECKS_RED)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(9);
  }

  // --- Die abgebrochene Kette (Issue #1123) -------------------------------------------------

  /**
   * Der Anlass: Die Kette zu #993 riss in der Runde zu Paket #1112. Beide Einheiten sind rot und
   * gleichrangig, die Ketten-Einheit steht zuerst — sie hat ihren Abbruch aber nur geerbt („harter
   * Stopp in der Runde zu Paket #1112"). Maßgeblich ist das Paket, an dem die Kette tatsächlich
   * riss.
   */
  @Test
  void beiEinerKetteGiltDasLetzteGleichrangigePaket() {
    var outcome =
        befund(
            true,
            null,
            NightRunMode.CHAIN,
            List.of(
                item(993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(1112, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(1112);
    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  /**
   * Dieselben Pakete in einem Implementierungs-Lauf: Dort gibt es keine erbende Ketten-Einheit, und
   * das erste in Laufreihenfolge bleibt maßgeblich (Issue #1078, unverändert).
   */
  @Test
  void ausserhalbEinerKetteBleibtDasErsteGleichrangigePaketMassgeblich() {
    var outcome =
        befund(
            true,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(
                item(993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(1112, NightRunState.RED, NightRunErrorClass.HARD_ABORT)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(993);
  }

  /**
   * Riss die Kette schon in der Planung, ist die Ketten-Einheit das einzige rote Paket — die
   * umgekehrte Reihenfolge wählt dann sie, und der Befund zeigt auf die Kette selbst.
   */
  @Test
  void eineInDerPlanungGerisseneKetteBleibtBeiDerKettenEinheit() {
    var outcome =
        befund(
            true,
            null,
            NightRunMode.CHAIN,
            List.of(
                item(993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(1112, NightRunState.GREEN, null)));

    assertThat(outcome.decisiveItem().cardNumber()).isEqualTo(993);
  }

  /**
   * Die Laufart dreht nur die Reihenfolge, nicht die Rangfolge: Rot schlägt Gelb schlägt
   * Grau-mit-Fehlerklasse auch in einer Kette — sonst wäre das zuletzt gelaufene Paket maßgeblich
   * statt das schwerste.
   */
  @Test
  void inEinerKetteEntscheidetDerRangWeiterVorDerReihenfolge() {
    var rotVorGelb =
        befund(
            true,
            null,
            NightRunMode.CHAIN,
            List.of(
                item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
                item(2, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED)));
    var gelbVorGrau =
        befund(
            true,
            null,
            NightRunMode.CHAIN,
            List.of(
                item(3, NightRunState.YELLOW, NightRunErrorClass.CHECKS_RED),
                item(4, NightRunState.GREY, NightRunErrorClass.AWAITING_DECISION)));

    assertThat(rotVorGelb.decisiveItem().cardNumber()).isEqualTo(1);
    assertThat(gelbVorGrau.decisiveItem().cardNumber()).isEqualTo(3);
  }

  // --- Stillefrist (Issue #1091, AK 6/8 der fachlichen Quelle #1086) -------------------------

  /**
   * Gemessen wird am letzten Lebenszeichen, nicht am Start: Der Lauf läuft seit vier Stunden — weit
   * über der Frist —, hat aber vor einer Minute gemeldet. Ein langer Lauf, der sich regelmäßig
   * meldet, ist genau der Normalfall einer Nacht.
   *
   * <p>Zugleich AK 1 der fachlichen Quelle #1074: Ein Lauf ohne Abschluss, ohne Pakete und mit
   * frischem Lebenszeichen bleibt {@code RUNNING} (Issue #1143).
   */
  @Test
  void einFrischesLebenszeichenHaeltDenLaufLaufend() {
    var outcome =
        NightRunOutcome.of(
            false,
            null,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            FIXED.plus(Duration.ofHours(4)),
            FIXED.plus(Duration.ofHours(4)).plus(Duration.ofMinutes(1)),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(outcome.isDisruption()).isFalse();
  }

  /** Genau auf der Frist ist der Lauf noch nicht tot — erst darüber. */
  @Test
  void genauAufDerFristLaeuftDerLaufNoch() {
    var outcome =
        NightRunOutcome.of(
            false,
            null,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            FIXED,
            FIXED.plus(FRIST),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
  }

  @Test
  void eineSekundeUeberDerFristIstDerLaufGescheitert() {
    var outcome =
        NightRunOutcome.of(
            false,
            null,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            FIXED,
            FIXED.plus(FRIST).plusSeconds(1),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isNull();
    assertThat(outcome.isDisruption()).isTrue();
  }

  /**
   * Ohne Lebenszeichen zählt der Startzeitpunkt — der Upload-Weg lässt {@code updatedAt} bewusst
   * leer. Beide Richtungen stehen hier: Ein Lauf, der {@code startedAt} ignorierte, wäre in der
   * einen Richtung nie und in der anderen immer tot.
   */
  @Test
  void ohneLebenszeichenZaehltDerStartzeitpunkt() {
    var innerhalb =
        NightRunOutcome.of(
            false,
            null,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            null,
            FIXED.plus(FRIST),
            FRIST);
    var darueber =
        NightRunOutcome.of(
            false,
            null,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            null,
            FIXED.plus(FRIST).plusSeconds(1),
            FRIST);

    assertThat(innerhalb.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(darueber.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  /**
   * Der abgeschlossene Lauf hat sein Ergebnis gemeldet; danach schweigt er zu Recht. Die Frist
   * fragt nur, ob ein <em>unfertiger</em> Lauf noch lebt.
   */
  @Test
  void einAbgeschlossenerLaufBleibtVonDerFristUnberuehrt() {
    var outcome =
        NightRunOutcome.of(
            true,
            null,
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(item(1, NightRunState.GREEN, null)),
            FIXED,
            FIXED,
            FIXED.plus(Duration.ofDays(30)),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.SUCCEEDED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.isDisruption()).isFalse();
  }

  /**
   * Die Frist schlägt die Paketauswahl <em>und</em> den Grund ohne Arbeit: Ein verstummter Lauf hat
   * sein Ergebnis nie gemeldet, also trägt sein Befund auch keines.
   */
  @Test
  void beiEinemVerstummtenLaufSchlaegtDieFristPaketUndGrund() {
    var outcome =
        NightRunOutcome.of(
            false,
            "Ready war leer",
            null,
            NightRunMode.IMPLEMENTATION,
            List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT)),
            FIXED,
            FIXED,
            FIXED.plus(Duration.ofHours(4)),
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isNull();
  }

  // --- Der harte Abbruch (Issue #1143, Plan #1139 E5) ----------------------------------------

  /**
   * AK 2 der fachlichen Quelle #1074: Ein abgeschlossen gemeldeter Lauf mit Abbruchgrund ist nicht
   * gelungen — ohne jeden Bezug auf die Stillefrist. Der Bezugszeitpunkt liegt hier dreißig Tage
   * nach dem letzten Lebenszeichen; an einem abgeschlossenen Lauf ändert das nichts.
   *
   * <p>AK 6 steht mit hier: {@code FAILED} ist bereits eine Störung, {@link
   * NightRunOutcome#isDisruption()} bleibt deshalb unverändert.
   */
  @Test
  void einAbbruchgrundLaesstDenLaufScheitern() {
    var outcome = abgebrochen(true, List.of(), FIXED.plus(Duration.ofDays(30)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.abortReason()).isEqualTo(ABBRUCH);
    assertThat(outcome.isDisruption()).isTrue();
  }

  /**
   * AK 3 und der Anlass des Vorhabens: Ein Lauf, der nach drei grünen Paketen hart abbrach, galt
   * bis hierher als gelungen. Der Abbruchgrund schlägt die Pakete.
   */
  @Test
  void einAbbruchgrundSchlaegtLauterGruenePakete() {
    var outcome =
        abgebrochen(
            true,
            List.of(
                item(1, NightRunState.GREEN, null),
                item(2, NightRunState.GREEN, null),
                item(3, NightRunState.GREEN, null)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.abortReason()).isEqualTo(ABBRUCH);
  }

  /**
   * E5: Der Abbruchgrund erzwingt das Urteil, lässt das maßgebliche Paket aber aus den Paketen
   * bestimmen — „Karte #6: zurückgestellt" bleibt die genauere Auskunft. Das graue Paket allein
   * wäre {@link NightRunOutcome.Verdict#WAITING}; der Abbruch macht daraus {@code FAILED}.
   */
  @Test
  void einAbbruchgrundLaesstDasMassgeblichePaketStehen() {
    var outcome =
        abgebrochen(
            true, List.of(item(6, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET)));

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem())
        .isEqualTo(
            new NightRunOutcome.DecisiveItem(
                6, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET));
  }

  /**
   * Der Abbruchgrund steht <b>hinter</b> „verstummt" und „läuft noch": Ein Lauf ohne Abschluss hat
   * noch nichts zu melden, und ein verstummter meldet gar nichts mehr — auch keinen Abbruch. Beide
   * Richtungen stehen hier, sonst bliebe offen, ob die neue Stufe zu weit nach vorn rutschte.
   */
  @Test
  void einAbbruchgrundSchlaegtWederLaeuftNochNochVerstummt() {
    var rot = List.of(item(1, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    var laufend = abgebrochen(false, rot);
    var verstummt = abgebrochen(false, rot, FIXED.plus(FRIST).plusSeconds(1));

    assertThat(laufend.verdict()).isEqualTo(NightRunOutcome.Verdict.RUNNING);
    assertThat(laufend.abortReason()).isNull();
    assertThat(verstummt.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(verstummt.decisiveItem()).isNull();
    assertThat(verstummt.abortReason()).isNull();
  }

  /**
   * Issue #1185, Kriterium 5: Der Abbruchgrund steht weiterhin <b>vor</b> dem Lauf ohne Arbeit —
   * auch ohne ein einziges Paket, und auch wenn der Grund ohne Arbeit daneben gesetzt ist. Nur weil
   * der Lauf ohne Arbeit nach unten gerutscht ist, wird ein abgebrochener Lauf nicht ruhig.
   *
   * <p>Der Dienst setzt beides nie zusammen (Issue #1142); die Rangfolge steht trotzdem hier, denn
   * {@link NightRunOutcome#of} entscheidet über einen ganzen Bestand und nicht über einen Weg.
   */
  @Test
  void einAbbruchgrundSchlaegtDenGrundOhneArbeitAuchOhnePaket() {
    var outcome =
        NightRunOutcome.of(
            true,
            NightRunOutcome.GRUND_UNBEKANNT,
            ABBRUCH,
            NightRunMode.IMPLEMENTATION,
            List.of(),
            FIXED,
            FIXED,
            FIXED,
            FRIST);

    assertThat(outcome.verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
    assertThat(outcome.decisiveItem()).isNull();
    assertThat(outcome.noWorkReason()).isNull();
    assertThat(outcome.abortReason()).isEqualTo(ABBRUCH);
    assertThat(outcome.isDisruption()).isTrue();
  }
}
