package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.nightrun.application.DisruptionRepository.AckTarget;
import org.mwolff.manban.nightrun.application.DisruptionRepository.DisruptionCandidate;
import org.mwolff.manban.nightrun.application.DisruptionService.DisruptionView;
import org.mwolff.manban.nightrun.application.DisruptionService.LeitstandView;
import org.mwolff.manban.nightrun.domain.NightRunErrorClass;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOutcome;
import org.mwolff.manban.nightrun.domain.NightRunState;

/**
 * Die Use-Cases des Plattform-Leitstands (Issue #1080, um den Leitstand erweitert in #1095).
 *
 * <p>Was die Datenbank entscheidet — Gattung, Abschluss, Teilnahme, vorhandene Quittung, Spanne der
 * Nacht — steht nicht hier, sondern in {@code DisruptionRepositoryIT} und {@code
 * LaeufeDerNachtRepositoryIT}: Das sind Zusagen über zwei Abfragen, und ein Mock, der sie nachbaut,
 * bewiese nur, dass der Test die Abfrage kennt. Hier steht, was der Dienst mit den Kandidaten
 * macht: Rechte, Maßstab, Aufteilung, Reihenfolge, Idempotenz.
 *
 * <p><b>Die Spanne der Nacht ist die Ausnahme</b> und wird hier geprüft, weil der Dienst sie selbst
 * rechnet: Aus Zone und Uhr entstehen die beiden Grenzen, mit denen er die Abfrage aufruft. {@link
 * #nachtLaeufe} bildet deshalb nicht eine feste Antwort ab, sondern die Zugehörigkeitsregel — nur
 * so fällt auf, wenn der Dienst die falsche Spanne bildet.
 */
// PMD.TooManyMethods: Testklasse — jede Methode ist ein Fall, und Faelle werden nicht
// zusammengelegt, um eine Zahl zu druecken. Issue #1123 bringt die beiden Faelle der Laufart dazu
// und reisst damit die Schwelle von 30.
@SuppressWarnings("PMD.TooManyMethods")
class DisruptionServiceTest {

  /** 08:00 UTC — vor der Tagesgrenze, die laufende Nacht ist damit die vom 19. auf den 20. */
  private static final Instant JETZT = Instant.parse("2026-09-20T08:00:00Z");

  private static final ZoneId UTC = ZoneId.of("UTC");
  private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
  private static final long ADMIN = 1L;
  private static final long NIEMAND = 2L;

  private DisruptionRepository disruptions;
  private NightRunRepository runs;
  private PlatformAdminChecker platformAdminChecker;
  private DisruptionService service;

  private static DisruptionCandidate kandidat(long laufId, Instant startedAt) {
    return kandidat(laufId, startedAt, NightRunMode.IMPLEMENTATION);
  }

  private static DisruptionCandidate kandidat(long laufId, Instant startedAt, NightRunMode mode) {
    return new DisruptionCandidate(laufId, 9L, "Projekt", mode, startedAt, null, true, null, null);
  }

  /** Ein Lauf, der sich noch nicht als abgeschlossen gemeldet hat. */
  private static DisruptionCandidate unfertig(
      long laufId, Instant startedAt, @Nullable Instant updatedAt) {
    return new DisruptionCandidate(
        laufId,
        9L,
        "Projekt",
        NightRunMode.IMPLEMENTATION,
        startedAt,
        updatedAt,
        false,
        null,
        null);
  }

  /** Ein Lauf, der seinen harten Abbruch selbst gemeldet hat (Issue #1143). */
  private static DisruptionCandidate abgebrochen(long laufId, Instant startedAt) {
    return new DisruptionCandidate(
        laufId,
        9L,
        "Projekt",
        NightRunMode.CHAIN,
        startedAt,
        null,
        true,
        null,
        "Dirty-Guard: uncommittete Reste in src/main/java/Foo.java");
  }

  private static NightRunItem paket(
      long laufId, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return paket(laufId, 721, state, errorClass);
  }

  private static NightRunItem paket(
      long laufId, int cardNumber, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        laufId * 100 + cardNumber,
        laufId,
        9L,
        JETZT,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        cardNumber,
        "Paket",
        state,
        errorClass,
        1L,
        null,
        null,
        null,
        List.of());
  }

  private static List<Long> ids(List<DisruptionView> zeilen) {
    return zeilen.stream().map(DisruptionView::nightRunId).toList();
  }

  /**
   * Stellt die Läufe bereit, aus denen die Abfrage der Nacht schöpft — gefiltert nach <b>ihren
   * beiden</b> Zweigen (Issue #1109): Start in der Nacht ({@code from} einschließlich, {@code to}
   * ausschließlich) <em>oder</em> unfertig mit einem Lebenszeichen innerhalb der Stillefrist.
   */
  private void nachtLaeufe(DisruptionCandidate... kandidaten) {
    // doAnswer statt when(...).thenAnswer: Ein zweiter Aufruf in demselben Test soll die Antwort
    // ersetzen, nicht die alte mit null-Grenzen auslösen.
    doAnswer(
            aufruf -> {
              Instant from = aufruf.getArgument(0);
              Instant to = aufruf.getArgument(1);
              Instant lebenszeichenAb =
                  aufruf.<Instant>getArgument(2).minus(aufruf.<Duration>getArgument(3));
              return Stream.of(kandidaten)
                  .filter(k -> inDerNacht(k, from, to) || nochAmLeben(k, lebenszeichenAb))
                  .toList();
            })
        .when(disruptions)
        .candidatesOfNight(any(), any(), any(), any());
  }

  private static boolean inDerNacht(DisruptionCandidate k, Instant from, Instant to) {
    return !k.startedAt().isBefore(from) && k.startedAt().isBefore(to);
  }

  /** Der zweite Zweig der Abfrage; ohne letzte Meldung zählt der Start als Lebenszeichen. */
  private static boolean nochAmLeben(DisruptionCandidate k, Instant lebenszeichenAb) {
    Instant lebenszeichen = k.updatedAt() == null ? k.startedAt() : k.updatedAt();
    return !k.complete() && !lebenszeichen.isBefore(lebenszeichenAb);
  }

  private void pakete(NightRunItem... items) {
    when(runs.findItemsByRunIds(any())).thenReturn(List.of(items));
  }

  @BeforeEach
  void setUp() {
    disruptions = mock(DisruptionRepository.class);
    runs = mock(NightRunRepository.class);
    platformAdminChecker = mock(PlatformAdminChecker.class);
    when(platformAdminChecker.isPlatformAdmin(ADMIN)).thenReturn(true);
    service =
        new DisruptionService(
            disruptions,
            runs,
            platformAdminChecker,
            new NightRunProperties(null, null, null, null, null),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  // --- Rechte (AK 3, AK 7, Kriterium 15) -----------------------------------------------------

  @Test
  void derLeitstandVerlangtDenPlattformAdmin_undLiestOhneIhnNichts() {
    assertThatThrownBy(() -> service.leitstand(NIEMAND, UTC))
        .isInstanceOf(AdminAccessDeniedException.class);
    verifyNoInteractions(disruptions);
  }

  @Test
  void dasQuittierenVerlangtDenPlattformAdmin_undSchreibtOhneIhnNichts() {
    assertThatThrownBy(() -> service.acknowledge(NIEMAND, 5L))
        .isInstanceOf(AdminAccessDeniedException.class);
    verifyNoInteractions(disruptions);
  }

  /** Plan E5: <b>eine</b> Rechteprüfung für drei Listen, nicht drei nebeneinander. */
  @Test
  void dreiListenKostenEineRechtepruefung() {
    nachtLaeufe(kandidat(5L, JETZT));
    when(disruptions.openCandidates()).thenReturn(List.of(kandidat(5L, JETZT)));
    pakete(paket(5L, NightRunState.RED, NightRunErrorClass.CHECKS_RED));

    service.leitstand(ADMIN, UTC);

    verify(platformAdminChecker, times(1)).isPlatformAdmin(ADMIN);
  }

  /** AK 7: Der Plattform-Admin liest die Störungen ohne jede Projekt-Mitgliedschaft. */
  @Test
  void derPlattformAdminLiestOhneProjektMitgliedschaft() {
    when(disruptions.openCandidates()).thenReturn(List.of(kandidat(5L, JETZT)));
    pakete(paket(5L, NightRunState.RED, NightRunErrorClass.CHECKS_RED));

    assertThat(service.leitstand(ADMIN, UTC).stoerungen()).hasSize(1);
  }

  // --- Die Aufteilung der Nacht (Kriterien 1, 2, 6) ------------------------------------------

  /**
   * Der verstummte Lauf ist der Kern: Er trägt {@code complete = false} und steht trotzdem unter
   * den durchgeführten — das leistet die Stillefrist aus Issue #1091, nicht ein zweites Feld.
   *
   * <p>Zugleich AK 1 der fachlichen Quelle #1074: Der Kandidat mit {@code complete = false} und
   * frischem Lebenszeichen (Lauf 7) steht unter den <b>laufenden</b> — daran ändert die neue Stufe
   * des Abbruchgrunds nichts (Issue #1143).
   */
  @Test
  void laufendeUndDurchgefuehrteTeilenSichDieNacht() {
    nachtLaeufe(
        unfertig(7L, JETZT.minus(Duration.ofMinutes(10)), JETZT.minus(Duration.ofMinutes(5))),
        kandidat(6L, JETZT.minus(Duration.ofHours(1))),
        unfertig(5L, JETZT.minus(Duration.ofHours(5)), JETZT.minus(Duration.ofHours(4))));
    pakete(paket(6L, NightRunState.GREEN, null));

    LeitstandView leitstand = service.leitstand(ADMIN, UTC);

    assertThat(ids(leitstand.laufende())).containsExactly(7L);
    assertThat(ids(leitstand.durchgefuehrte())).containsExactly(6L, 5L);
    assertThat(leitstand.durchgefuehrte())
        .filteredOn(v -> v.nightRunId() == 5L)
        .singleElement()
        .extracting(v -> v.outcome().verdict())
        .isEqualTo(NightRunOutcome.Verdict.FAILED);
  }

  /** Issue #1128: Jede Zeile trägt die Art ihres Laufs, wie der Kandidat sie liefert. */
  @Test
  void jedeZeileTraegtDieArtIhresLaufs() {
    nachtLaeufe(
        kandidat(6L, JETZT.minus(Duration.ofHours(1)), NightRunMode.CHAIN),
        kandidat(5L, JETZT.minus(Duration.ofHours(2)), NightRunMode.REVIEW));
    when(disruptions.openCandidates())
        .thenReturn(List.of(kandidat(5L, JETZT.minus(Duration.ofHours(2)), NightRunMode.REVIEW)));
    pakete(paket(5L, NightRunState.RED, NightRunErrorClass.CHECKS_RED));

    LeitstandView leitstand = service.leitstand(ADMIN, UTC);

    assertThat(leitstand.durchgefuehrte())
        .extracting(DisruptionView::mode)
        .containsExactly(NightRunMode.CHAIN, NightRunMode.REVIEW);
    assertThat(leitstand.stoerungen())
        .extracting(DisruptionView::mode)
        .containsExactly(NightRunMode.REVIEW);
  }

  /**
   * Kriterium 8: Ein neues Lebenszeichen holt den Lauf unter die laufenden zurück, ohne dass
   * irgendetwas zurückgesetzt würde — die Stillefrist ist eine Leseregel, keine Zustandsänderung.
   */
  @Test
  void einNeuesLebenszeichenStelltDenLaufWiederUnterDieLaufenden() {
    nachtLaeufe(unfertig(5L, JETZT.minus(Duration.ofHours(5)), JETZT.minus(Duration.ofHours(4))));

    assertThat(ids(service.leitstand(ADMIN, UTC).durchgefuehrte())).containsExactly(5L);

    nachtLaeufe(unfertig(5L, JETZT.minus(Duration.ofHours(5)), JETZT.minus(Duration.ofMinutes(1))));

    LeitstandView danach = service.leitstand(ADMIN, UTC);
    assertThat(ids(danach.laufende())).containsExactly(5L);
    assertThat(danach.durchgefuehrte()).isEmpty();
    verify(disruptions, never()).acknowledge(anyLong(), anyLong(), any());
  }

  /** Kriterium 9: jüngster Startzeitpunkt zuoberst — in beiden neuen Listen. */
  @Test
  void beideNeuenListenStehenJuengsterZuoberst() {
    nachtLaeufe(
        unfertig(9L, JETZT.minus(Duration.ofMinutes(10)), JETZT),
        kandidat(8L, JETZT.minus(Duration.ofMinutes(20))),
        unfertig(7L, JETZT.minus(Duration.ofMinutes(30)), JETZT),
        kandidat(6L, JETZT.minus(Duration.ofMinutes(40))));

    LeitstandView leitstand = service.leitstand(ADMIN, UTC);

    assertThat(ids(leitstand.laufende())).containsExactly(9L, 7L);
    assertThat(ids(leitstand.durchgefuehrte())).containsExactly(8L, 6L);
  }

  /**
   * Kriterium 9, Grenzfall: Über die Zugehörigkeit zu den <b>beendeten</b> Läufen entscheidet der
   * Startzeitpunkt. Ein <em>abgeschlossener</em> Lauf von 11:50 gehört zur Nacht davor und ist nach
   * 12:00 aus beiden Bereichen fort, weil die neue Nacht ihn nicht kennt.
   */
  @Test
  void einBeendeterLaufVonVorZwoelfGehoertNachZwoelfInKeineDerNeuenListen() {
    DisruptionService nachZwoelf = mitUhr(Instant.parse("2026-09-20T12:10:00Z"));
    nachtLaeufe(kandidat(5L, Instant.parse("2026-09-20T11:50:00Z")));

    LeitstandView leitstand = nachZwoelf.leitstand(ADMIN, UTC);

    assertThat(leitstand.laufende()).isEmpty();
    assertThat(leitstand.durchgefuehrte()).isEmpty();
  }

  // --- Die Nachtgrenze gilt nur den beendeten Läufen (Issue #1109) ----------------------------

  /**
   * Kriterium 1 und AK 1 aus #1086: Ein Lauf, der um 10:30 begann und um 13:00 noch arbeitet, steht
   * unter den <b>laufenden</b> — obwohl sein Start vor der Nachtgrenze liegt. Für den Bereich der
   * laufenden Läufe kennt #1086 keine Nachtgrenze; sie zieht AK 9 nur für die beendeten.
   */
  @Test
  void einLaufVonVorZwoelfDerNochArbeitetStehtUnterDenLaufenden() {
    DisruptionService nachZwoelf = mitUhr(Instant.parse("2026-09-20T13:00:00Z"));
    nachtLaeufe(
        unfertig(5L, Instant.parse("2026-09-20T10:30:00Z"), Instant.parse("2026-09-20T12:50:00Z")));

    LeitstandView leitstand = nachZwoelf.leitstand(ADMIN, UTC);

    assertThat(ids(leitstand.laufende())).containsExactly(5L);
    assertThat(leitstand.durchgefuehrte()).isEmpty();
  }

  /**
   * Kriterium 2 und AK 9 aus #1086: Derselbe Lauf, um 13:30 abgeschlossen und um 14:00 abgefragt,
   * verlässt beide Lauf-Bereiche — er gehört zur alten Nacht. Als Störung bleibt er sichtbar, denn
   * die Störungsliste geht über alle Nächte.
   */
  @Test
  void derAbgeschlosseneLaufDerAltenNachtVerlaesstBeideBereiche_bleibtAberStoerung() {
    DisruptionService nachZwoelf = mitUhr(Instant.parse("2026-09-20T14:00:00Z"));
    DisruptionCandidate beendet = kandidat(5L, Instant.parse("2026-09-20T10:30:00Z"));
    nachtLaeufe(beendet);
    when(disruptions.openCandidates()).thenReturn(List.of(beendet));
    pakete(paket(5L, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    LeitstandView leitstand = nachZwoelf.leitstand(ADMIN, UTC);

    assertThat(leitstand.laufende()).isEmpty();
    assertThat(leitstand.durchgefuehrte()).isEmpty();
    assertThat(ids(leitstand.stoerungen())).containsExactly(5L);
  }

  /**
   * Kriterium 3: Der Dienst zieht die Nachtgrenze für die beendeten Läufe <b>ausdrücklich</b> und
   * leitet sie nicht aus der Abfrage ab.
   *
   * <p>Der zweite Zweig der Abfrage liefert auch Läufe früherer Nächte; zwischen Abfrage und
   * Auswertung kann die Stillefrist einen davon verstummen lassen — die Abfrage bekommt ihren
   * Bezugszeitpunkt, der Befund liest die Uhr erneut. Ohne die Grenze stünde so ein Lauf der alten
   * Nacht unter den beendeten Läufen der neuen. Der Kandidat kommt deshalb direkt aus dem Mock,
   * nicht über {@link #nachtLaeufe}: Genau diesen Rand bildet dessen Filter nicht ab.
   */
  @Test
  void einVerstummterLaufEinerFruehrenNachtStehtInKeinemBereich() {
    DisruptionService nachZwoelf = mitUhr(Instant.parse("2026-09-20T14:00:00Z"));
    when(disruptions.candidatesOfNight(any(), any(), any(), any()))
        .thenReturn(
            List.of(
                unfertig(
                    5L,
                    Instant.parse("2026-09-20T10:30:00Z"),
                    Instant.parse("2026-09-20T11:00:00Z"))));

    LeitstandView leitstand = nachZwoelf.leitstand(ADMIN, UTC);

    assertThat(leitstand.laufende()).isEmpty();
    assertThat(leitstand.durchgefuehrte()).isEmpty();
  }

  /**
   * Die Abfrage bekommt denselben Maßstab, mit dem der Befund später rechnet — Uhr und Stillefrist
   * des Dienstes. Eine eigene Regel in SQL zeigte einen Lauf, den die Auswertung des Projekts
   * anders sieht (#1086 AK 8).
   */
  @Test
  void dieAbfrageBekommtDieNachtgrenzenUndDenselbenMassstabWieDerBefund() {
    service.leitstand(ADMIN, UTC);

    verify(disruptions)
        .candidatesOfNight(
            // Seit Issue #1135 ab dem Beginn des vorigen Zyklus: Er steht unter „Beendete Läufe"
            // mit.
            Instant.parse("2026-09-18T12:00:00Z"),
            Instant.parse("2026-09-20T12:00:00Z"),
            JETZT,
            Duration.ofMinutes(90));
  }

  // --- Dieser und voriger Zyklus (Issue #1135) -----------------------------------------------

  /** Ein Dienst, dessen Uhr auf 22.09. 13:00 in Berlin steht — eine Stunde nach der Grenze. */
  private DisruptionService umEinsNachMittag() {
    return new DisruptionService(
        disruptions,
        runs,
        platformAdminChecker,
        new NightRunProperties(null, null, null, null, null),
        Clock.fixed(Instant.parse("2026-09-22T11:00:00Z"), ZoneOffset.UTC));
  }

  /**
   * AK 1: Die beendeten Läufe verteilen sich auf diesen und den vorigen Zyklus; älter fällt weg.
   */
  @Test
  void beendeteLaeufeStehenNachZyklusGetrennt() {
    nachtLaeufe(
        kandidat(3L, Instant.parse("2026-09-22T10:30:00Z")), // 22.09. 12:30 — dieser Zyklus
        kandidat(2L, Instant.parse("2026-09-21T21:00:00Z")), // 21.09. 23:00 — voriger Zyklus
        kandidat(1L, Instant.parse("2026-09-20T20:00:00Z"))); // 20.09. 22:00 — älter

    LeitstandView leitstand = umEinsNachMittag().leitstand(ADMIN, BERLIN);

    assertThat(ids(leitstand.durchgefuehrte())).containsExactly(3L);
    assertThat(ids(leitstand.durchgefuehrteVoriger())).containsExactly(2L);
  }

  /** AK 2: Start 11:59 gehört zum vorigen Zyklus, Start 12:00 zum laufenden. */
  @Test
  void dieGrenzeLiegtUmZwoelf() {
    nachtLaeufe(
        kandidat(2L, Instant.parse("2026-09-22T10:00:00Z")), // 12:00
        kandidat(1L, Instant.parse("2026-09-22T09:59:00Z"))); // 11:59

    LeitstandView leitstand = umEinsNachMittag().leitstand(ADMIN, BERLIN);

    assertThat(ids(leitstand.durchgefuehrte())).containsExactly(2L);
    assertThat(ids(leitstand.durchgefuehrteVoriger())).containsExactly(1L);
  }

  /** Ein laufender Lauf aus dem vorigen Zyklus bleibt aktiv und steht unter keinem der beiden. */
  @Test
  void einLaufenderLaufDesVorigenZyklusBleibtAktiv() {
    nachtLaeufe(
        new DisruptionCandidate(
            4L,
            9L,
            "Projekt",
            NightRunMode.CHAIN,
            Instant.parse("2026-09-22T08:27:00Z"),
            Instant.parse("2026-09-22T10:55:00Z"),
            false,
            null,
            null));

    LeitstandView leitstand = umEinsNachMittag().leitstand(ADMIN, BERLIN);

    assertThat(ids(leitstand.laufende())).containsExactly(4L);
    assertThat(leitstand.durchgefuehrte()).isEmpty();
    assertThat(leitstand.durchgefuehrteVoriger()).isEmpty();
  }

  /**
   * Plan E6: Die Zone kommt vom Browser und verschiebt die Nachtgrenze. Derselbe Lauf um 10:30 UTC
   * liegt in der laufenden Berliner Nacht (Grenze 10:00 UTC), nicht aber in der laufenden UTC-Nacht
   * (Grenze 12:00 UTC).
   */
  @Test
  void dieZoneEntscheidetUeberDieNacht() {
    nachtLaeufe(kandidat(5L, Instant.parse("2026-09-19T10:30:00Z")));

    assertThat(ids(service.leitstand(ADMIN, BERLIN).durchgefuehrte())).containsExactly(5L);
    assertThat(service.leitstand(ADMIN, UTC).durchgefuehrte()).isEmpty();
  }

  // --- Die Störungen bleiben, was sie waren (Kriterium 17) -----------------------------------

  /**
   * Kriterium 17 und AK 4 aus #1064: Die Störungen gehen über <b>alle</b> Nächte, nicht nur die
   * laufende — und ein verstummter Lauf ist keine Störung, weil die Störungsabfrage ihn nicht
   * kennt.
   */
  @Test
  void dieStoerungenGehenUeberAlleNaechte_undKennenDenVerstummtenNicht() {
    nachtLaeufe(unfertig(5L, JETZT.minus(Duration.ofHours(5)), JETZT.minus(Duration.ofHours(4))));
    when(disruptions.openCandidates())
        .thenReturn(List.of(kandidat(4L, JETZT.minus(Duration.ofDays(3)))));
    pakete(paket(4L, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    LeitstandView leitstand = service.leitstand(ADMIN, UTC);

    assertThat(ids(leitstand.stoerungen())).containsExactly(4L);
    assertThat(ids(leitstand.durchgefuehrte())).containsExactly(5L);
  }

  /**
   * Kriterien 15 und 16: Ein Lauf eines nicht teilnehmenden Projekts und eine interaktive Sitzung
   * fehlen in allen drei Listen. Beide Filter sitzen in den zwei Abfragen ({@code
   * LaeufeDerNachtRepositoryIT}, {@code DisruptionRepositoryIT}); hier steht die Zusage, die der
   * Dienst dazu beiträgt — er schöpft aus <b>keiner</b> weiteren Quelle, die an ihnen vorbeiführte.
   */
  @Test
  void derDienstSchoepftNurAusDenBeidenGefiltertenAbfragen() {
    nachtLaeufe(kandidat(5L, JETZT));
    pakete(paket(5L, NightRunState.GREEN, null));

    LeitstandView leitstand = service.leitstand(ADMIN, UTC);

    assertThat(ids(leitstand.durchgefuehrte())).containsExactly(5L);
    verify(disruptions).candidatesOfNight(any(), any(), any(), any());
    verify(disruptions).openCandidates();
    verifyNoMoreInteractions(disruptions);
  }

  // --- Der Maßstab entscheidet ---------------------------------------------------------------

  @Test
  void einGelungenerLaufIstKandidat_aberKeineStoerung() {
    when(disruptions.openCandidates())
        .thenReturn(List.of(kandidat(5L, JETZT), kandidat(6L, JETZT.minusSeconds(60))));
    pakete(
        paket(5L, NightRunState.GREEN, null),
        paket(6L, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    assertThat(ids(service.leitstand(ADMIN, UTC).stoerungen())).containsExactly(6L);
  }

  @Test
  void einZurueckgestelltesPaketIstEineStoerung() {
    when(disruptions.openCandidates()).thenReturn(List.of(kandidat(5L, JETZT)));
    pakete(paket(5L, NightRunState.GREY, NightRunErrorClass.DEPENDENCY_UNMET));

    assertThat(service.leitstand(ADMIN, UTC).stoerungen())
        .singleElement()
        .extracting(v -> v.outcome().verdict())
        .isEqualTo(NightRunOutcome.Verdict.WAITING);
  }

  /**
   * Ein Lauf ohne Arbeit hat kein Paket — der Grund ist der Text, und er reicht. Seit Issue #1121
   * ist das nur noch beim <b>Rückfall</b> des Servers eine Störung: Er kann einen alten Runner, den
   * Upload-Weg oder einen Lauf meinen, der alle Pakete zurückstellte.
   */
  @Test
  void einLaufOhneArbeitMitUnbekanntemGrundIstEineStoerungAuchOhnePaket() {
    when(disruptions.openCandidates())
        .thenReturn(
            List.of(
                new DisruptionCandidate(
                    5L,
                    9L,
                    "Projekt",
                    NightRunMode.IMPLEMENTATION,
                    JETZT,
                    null,
                    true,
                    NightRunOutcome.GRUND_UNBEKANNT,
                    null)));

    assertThat(service.leitstand(ADMIN, UTC).stoerungen())
        .singleElement()
        .extracting(v -> v.outcome().noWorkReason())
        .isEqualTo(NightRunOutcome.GRUND_UNBEKANNT);
  }

  /**
   * Issue #1121: Ein Lauf, der nichts zu tun fand, steht unter den <b>durchgeführten</b> — und
   * nicht unter den Störungen. Die Störungsabfrage liefert ihn weiterhin (sie kennt den Ausgang
   * nicht); den Unterschied macht allein {@code isDisruption()} des Befunds.
   */
  @Test
  void einLaufOhneArbeitMitGemeldetemGrundIstKeineStoerung() {
    DisruptionCandidate ruhig =
        new DisruptionCandidate(
            5L,
            9L,
            "Projekt",
            NightRunMode.IMPLEMENTATION,
            JETZT,
            null,
            true,
            "Ready ist leer — nichts zu tun.",
            null);
    nachtLaeufe(ruhig);
    when(disruptions.openCandidates()).thenReturn(List.of(ruhig));

    LeitstandView leitstand = service.leitstand(ADMIN, UTC);

    assertThat(leitstand.stoerungen()).isEmpty();
    assertThat(leitstand.durchgefuehrte())
        .singleElement()
        .extracting(v -> v.outcome().verdict())
        .isEqualTo(NightRunOutcome.Verdict.NO_WORK);
  }

  /**
   * AK 6 der fachlichen Quelle #1074: Ein Lauf, der seinen harten Abbruch gemeldet hat, steht unter
   * den <b>Störungen</b> — auch ohne ein einziges nicht-grünes Paket. Bis Issue #1143 fiel er als
   * gelungen aus der Liste; gespeichert war der Grund seit #1142, gewirkt hat er nicht.
   *
   * <p>Der Kandidat steht zugleich unter den durchgeführten Läufen der Nacht: Ein abgebrochener
   * Lauf meldet sich abgeschlossen, und seine Störung ist dieselbe Zeile.
   */
  @Test
  void einAbgebrochenerLaufIstEineStoerung() {
    DisruptionCandidate abgebrochen = abgebrochen(5L, JETZT);
    nachtLaeufe(abgebrochen);
    when(disruptions.openCandidates()).thenReturn(List.of(abgebrochen));
    pakete(paket(5L, NightRunState.GREEN, null));

    LeitstandView leitstand = service.leitstand(ADMIN, UTC);

    assertThat(leitstand.stoerungen())
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.nightRunId()).isEqualTo(5L);
              assertThat(v.outcome().verdict()).isEqualTo(NightRunOutcome.Verdict.FAILED);
              assertThat(v.outcome().abortReason()).isEqualTo(abgebrochen.abortReason());
            });
    assertThat(ids(leitstand.durchgefuehrte())).containsExactly(5L);
  }

  /**
   * Issue #1123: Bei einer abgebrochenen Kette zeigt die Störzeile das Paket, an dem sie riss — die
   * Ketten-Einheit steht zuerst und hat ihren Abbruch nur geerbt. Der Dienst muss die Laufart dafür
   * vom Kandidaten bis in den Befund durchreichen; ließe er sie weg, stünde hier wieder „Karte
   * #993".
   */
  @Test
  void dieStoerzeileEinerKetteZeigtDasPaketAnDemSieRiss() {
    when(disruptions.openCandidates()).thenReturn(List.of(kandidat(5L, JETZT, NightRunMode.CHAIN)));
    pakete(
        paket(5L, 993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
        paket(5L, 1112, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    assertThat(service.leitstand(ADMIN, UTC).stoerungen())
        .singleElement()
        .extracting(v -> v.outcome().decisiveItem().cardNumber())
        .isEqualTo(1112);
  }

  /** Derselbe Fall außerhalb einer Kette bleibt beim ersten Paket in Laufreihenfolge. */
  @Test
  void dieStoerzeileEinesImplementierungslaufsBleibtBeimErstenPaket() {
    when(disruptions.openCandidates())
        .thenReturn(List.of(kandidat(5L, JETZT, NightRunMode.IMPLEMENTATION)));
    pakete(
        paket(5L, 993, NightRunState.RED, NightRunErrorClass.HARD_ABORT),
        paket(5L, 1112, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    assertThat(service.leitstand(ADMIN, UTC).stoerungen())
        .singleElement()
        .extracting(v -> v.outcome().decisiveItem().cardNumber())
        .isEqualTo(993);
  }

  @Test
  void dieStoerzeileTraegtProjektUndZeitpunkt() {
    when(disruptions.openCandidates())
        .thenReturn(
            List.of(
                new DisruptionCandidate(
                    5L,
                    9L,
                    "Mein Projekt",
                    NightRunMode.IMPLEMENTATION,
                    JETZT,
                    null,
                    true,
                    null,
                    null)));
    pakete(paket(5L, NightRunState.RED, NightRunErrorClass.CHECKS_RED));

    assertThat(service.leitstand(ADMIN, UTC).stoerungen())
        .singleElement()
        .satisfies(
            v -> {
              assertThat(v.projectId()).isEqualTo(9L);
              assertThat(v.projectName()).isEqualTo("Mein Projekt");
              assertThat(v.startedAt()).isEqualTo(JETZT);
            });
  }

  /**
   * Ein Lauf, der in beiden Abfragen steht, wird <b>einmal</b> nach Paketen gefragt — sonst liefe
   * die Auswertung zweimal über dieselben Zeilen.
   */
  @Test
  void diePaketeWerdenFuerBeideAbfragenInEinemZugGeholt() {
    nachtLaeufe(kandidat(6L, JETZT), kandidat(5L, JETZT.minusSeconds(60)));
    when(disruptions.openCandidates()).thenReturn(List.of(kandidat(5L, JETZT.minusSeconds(60))));
    pakete(paket(5L, NightRunState.RED, NightRunErrorClass.HARD_ABORT));

    service.leitstand(ADMIN, UTC);

    verify(runs).findItemsByRunIds(List.of(6L, 5L));
  }

  @Test
  void ohneKandidatenWirdNichtNachPaketenGefragt() {
    assertThat(service.leitstand(ADMIN, UTC))
        .satisfies(
            l -> {
              assertThat(l.laufende()).isEmpty();
              assertThat(l.durchgefuehrte()).isEmpty();
              assertThat(l.stoerungen()).isEmpty();
            });
    verifyNoInteractions(runs);
  }

  // --- Quittieren (AK 8, 10) -----------------------------------------------------------------

  @Test
  void dasQuittierenSchreibtDieQuittungMitNutzerUndUhr() {
    when(disruptions.ackTarget(5L)).thenReturn(Optional.of(new AckTarget(5L, 9L)));

    service.acknowledge(ADMIN, 5L);

    verify(disruptions).acknowledge(5L, ADMIN, JETZT);
  }

  /** AK 10: Der Lauf selbst wird nicht angefasst — die Quittung liegt daneben. */
  @Test
  void dasQuittierenLaesstDenLaufUnberuehrt() {
    when(disruptions.ackTarget(5L)).thenReturn(Optional.of(new AckTarget(5L, 9L)));

    service.acknowledge(ADMIN, 5L);

    verifyNoInteractions(runs);
  }

  /** AK 8: Zwei Admins räumen dieselbe Zeile weg — der zweite darf nichts Rotes sehen. */
  @Test
  void dasZweiteQuittierenIstKeinFehler() {
    when(disruptions.ackTarget(5L)).thenReturn(Optional.of(new AckTarget(5L, 9L)));

    service.acknowledge(ADMIN, 5L);

    assertThatCode(() -> service.acknowledge(ADMIN, 5L)).doesNotThrowAnyException();
  }

  @Test
  void einUnbekannterOderVerdraengterLaufIst404_undSchreibtNichts() {
    when(disruptions.ackTarget(5L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.acknowledge(ADMIN, 5L))
        .isInstanceOf(DisruptionNotFoundException.class);
    verify(disruptions, never()).acknowledge(5L, ADMIN, JETZT);
  }

  private DisruptionService mitUhr(Instant jetzt) {
    return new DisruptionService(
        disruptions,
        runs,
        platformAdminChecker,
        new NightRunProperties(null, null, null, null, null),
        Clock.fixed(jetzt, ZoneOffset.UTC));
  }
}
