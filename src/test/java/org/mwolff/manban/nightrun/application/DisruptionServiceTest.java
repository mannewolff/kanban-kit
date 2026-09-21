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
    return new DisruptionCandidate(laufId, 9L, "Projekt", startedAt, null, true, null);
  }

  /** Ein Lauf, der sich noch nicht als abgeschlossen gemeldet hat. */
  private static DisruptionCandidate unfertig(
      long laufId, Instant startedAt, @Nullable Instant updatedAt) {
    return new DisruptionCandidate(laufId, 9L, "Projekt", startedAt, updatedAt, false, null);
  }

  private static NightRunItem paket(
      long laufId, NightRunState state, @Nullable NightRunErrorClass errorClass) {
    return new NightRunItem(
        laufId * 100,
        laufId,
        9L,
        JETZT,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        721,
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
   * Stellt die Läufe bereit, aus denen die Abfrage der Nacht schöpft — gefiltert nach <b>ihrem</b>
   * Maßstab: Startzeitpunkt ab {@code from} einschließlich bis {@code to} ausschließlich.
   */
  private void nachtLaeufe(DisruptionCandidate... kandidaten) {
    // doAnswer statt when(...).thenAnswer: Ein zweiter Aufruf in demselben Test soll die Antwort
    // ersetzen, nicht die alte mit null-Grenzen auslösen.
    doAnswer(
            aufruf -> {
              Instant from = aufruf.getArgument(0);
              Instant to = aufruf.getArgument(1);
              return Stream.of(kandidaten)
                  .filter(k -> !k.startedAt().isBefore(from) && k.startedAt().isBefore(to))
                  .toList();
            })
        .when(disruptions)
        .candidatesOfNight(any(), any());
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
   * Kriterium 9, Grenzfall: Über die Zugehörigkeit entscheidet der <b>Startzeitpunkt</b>. Ein Lauf
   * von 11:50 bis 12:10 gehört zur Nacht davor und ist nach 12:00 aus beiden Bereichen fort — nicht
   * etwa, weil er noch liefe, sondern weil die neue Nacht ihn nicht kennt.
   */
  @Test
  void einLaufVonVorZwoelfGehoertNachZwoelfInKeineDerNeuenListen() {
    DisruptionService nachZwoelf = mitUhr(Instant.parse("2026-09-20T12:10:00Z"));
    nachtLaeufe(kandidat(5L, Instant.parse("2026-09-20T11:50:00Z")));

    LeitstandView leitstand = nachZwoelf.leitstand(ADMIN, UTC);

    assertThat(leitstand.laufende()).isEmpty();
    assertThat(leitstand.durchgefuehrte()).isEmpty();
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
    verify(disruptions).candidatesOfNight(any(), any());
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

  /** Ein Lauf ohne Arbeit hat kein Paket — der Grund ist der Text, und er reicht. */
  @Test
  void einLaufOhneArbeitIstEineStoerungAuchOhnePaket() {
    when(disruptions.openCandidates())
        .thenReturn(
            List.of(
                new DisruptionCandidate(5L, 9L, "Projekt", JETZT, null, true, "Ready war leer")));

    assertThat(service.leitstand(ADMIN, UTC).stoerungen())
        .singleElement()
        .extracting(v -> v.outcome().noWorkReason())
        .isEqualTo("Ready war leer");
  }

  @Test
  void dieStoerzeileTraegtProjektUndZeitpunkt() {
    when(disruptions.openCandidates())
        .thenReturn(
            List.of(new DisruptionCandidate(5L, 9L, "Mein Projekt", JETZT, null, true, null)));
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
