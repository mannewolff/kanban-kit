package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.WarteKarteView;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunOutcome.Verdict;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.ReadyBewertung;
import org.mwolff.manban.nightrun.domain.RunnerLage;
import org.mwolff.manban.nightrun.domain.RunnerLage.Gesamt;
import org.mwolff.manban.nightrun.domain.RunnerLage.LaufZustand;
import org.mwolff.manban.nightrun.domain.Wartegrund;
import org.mwolff.manban.nightrun.domain.Wartegrund.LaufgrundArt;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/**
 * Der Meldeweg der Bewertung der Ready-Karten (Issue #1726), die Lage des Runners (Issue #1727) und
 * der Wartegrund einer Ready-Karte (Issue #1728).
 */
class NightRunNowServiceTest {

  private static final long USER = 7L;
  private static final long PROJECT = 42L;
  private static final Instant T1 = Instant.parse("2026-10-13T22:40:00Z");

  private final NightReadyAssessmentRepository bewertungen =
      mock(NightReadyAssessmentRepository.class);
  private final NightRunRepository runs = mock(NightRunRepository.class);
  private final PermissionChecker permissions = mock(PermissionChecker.class);
  private static final Instant JETZT = Instant.parse("2026-10-14T02:00:00Z");
  private static final Duration FRIST = Duration.ofMinutes(30);

  private final NightRunProperties properties =
      new NightRunProperties(null, null, null, null, FRIST);
  private final CardRunQueryService cards = mock(CardRunQueryService.class);
  private final DisruptionRepository disruptions = mock(DisruptionRepository.class);
  private final NightRunNowService service =
      new NightRunNowService(
          bewertungen,
          runs,
          permissions,
          properties,
          Clock.fixed(JETZT, ZoneOffset.UTC),
          cards,
          disruptions);

  private static NightRun lauf(long id, Instant startedAt, boolean complete, Instant updatedAt) {
    return new NightRun(
        id,
        PROJECT,
        startedAt,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        60_000,
        0,
        0,
        0,
        null,
        startedAt,
        NightRunOrigin.TOKEN,
        "runner",
        complete,
        updatedAt,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private static NightRunItem paket(long runId, NightRunState state) {
    return new NightRunItem(
        null,
        runId,
        PROJECT,
        JETZT,
        NightRunMode.IMPLEMENTATION,
        NightRunKind.NIGHT,
        1700,
        "P",
        state,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        List.of(),
        List.of());
  }

  private static ReadyBewertung bewertung(Instant am) {
    return new ReadyBewertung(
        am, null, "kit:nightrun", List.of(new ReadyBewertung.Eintrag(1695, 1, List.of())));
  }

  /** Die Signatur nimmt Primitive (ArchitectureTest: nightrun kennt accesstoken nicht). */
  @Test
  void meldeBewertung_nimmtPrimitiveUndKeinenPrincipal() throws NoSuchMethodException {
    assertThat(
            NightRunNowService.class
                .getMethod("meldeBewertung", long.class, long.class, ReadyBewertung.class)
                .getParameterTypes())
        .containsExactly(long.class, long.class, ReadyBewertung.class);
  }

  @Test
  void meldeBewertung_verlangtDenBesitzer_undSchreibtSonstNichts() {
    doThrow(new ProjectAccessDeniedException()).when(permissions).requireOwner(USER, PROJECT);

    assertThatThrownBy(() -> service.meldeBewertung(USER, PROJECT, bewertung(T1)))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(bewertungen, runs);
  }

  @Test
  void meldeBewertung_sperrtDasProjektVorDemLesenUndErsetzt() {
    when(bewertungen.findByProjectId(PROJECT)).thenReturn(Optional.empty());

    service.meldeBewertung(USER, PROJECT, bewertung(T1));

    InOrder reihenfolge = inOrder(permissions, runs, bewertungen);
    reihenfolge.verify(permissions).requireOwner(USER, PROJECT);
    reihenfolge.verify(runs).lockProject(PROJECT);
    reihenfolge.verify(bewertungen).findByProjectId(PROJECT);
    reihenfolge.verify(bewertungen).ersetze(PROJECT, bewertung(T1));
  }

  @Test
  void meldeBewertung_ersetztEineGleichAlte_alsWiederholung() {
    when(bewertungen.findByProjectId(PROJECT)).thenReturn(Optional.of(bewertung(T1)));

    service.meldeBewertung(USER, PROJECT, bewertung(T1));

    verify(bewertungen).ersetze(PROJECT, bewertung(T1));
  }

  @Test
  void meldeBewertung_weistEineAeltereAb_undLaesstDieGespeicherteStehen() {
    when(bewertungen.findByProjectId(PROJECT))
        .thenReturn(Optional.of(bewertung(T1.plusSeconds(60))));

    assertThatThrownBy(() -> service.meldeBewertung(USER, PROJECT, bewertung(T1)))
        .isInstanceOf(NightReadyAssessmentOutdatedException.class);

    verify(bewertungen, never()).ersetze(anyLong(), ArgumentMatchers.any());
  }

  @Test
  void jetzt_verlangtDenNachtlaufZugriff_undLiestSonstNichts() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.jetzt(USER, PROJECT))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(runs, bewertungen, disruptions);
  }

  /**
   * Nur Nachtläufe, nie interaktive Sitzungen; die Frist kommt aus den Properties, jetzt aus der
   * Uhr.
   */
  @Test
  void jetzt_liestDieNachtlaeufe_undMisstGegenDieStillefristDerProperties() {
    // Eine Minute über der Frist der Properties: verstummt — unter der Vorgabe von 90 min liefe er.
    NightRun verstummt =
        lauf(1L, JETZT.minus(Duration.ofHours(1)), false, JETZT.minus(FRIST).minusSeconds(60));
    when(runs.findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.NIGHT))
        .thenReturn(List.of(verstummt));

    RunnerLage lage = service.jetzt(USER, PROJECT);

    verify(permissions).requireNightRunAccess(USER, PROJECT);
    verify(runs, never())
        .findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.INTERACTIVE);
    verify(runs, never()).findItemsByRunIds(ArgumentMatchers.any());
    assertThat(lage.gesamt()).isEqualTo(Gesamt.AKTIV);
    assertThat(lage.laeufe().getFirst().zustand()).isEqualTo(LaufZustand.VERSTUMMT);
  }

  /** Geladen werden nur die Pakete des letzten beendeten Laufs, und das Ergebnis wertet sie aus. */
  @Test
  void jetzt_laedtNurDiePaketeDesLetztenBeendetenLaufs() {
    NightRun letzter = lauf(3L, JETZT.minus(Duration.ofHours(2)), true, JETZT);
    NightRun frueher = lauf(2L, JETZT.minus(Duration.ofHours(5)), true, JETZT);
    when(runs.findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.NIGHT))
        .thenReturn(List.of(letzter, frueher));
    when(runs.findItemsByRunIds(List.of(3L))).thenReturn(List.of(paket(3L, NightRunState.RED)));

    RunnerLage lage = service.jetzt(USER, PROJECT);

    assertThat(lage.gesamt()).isEqualTo(Gesamt.KEIN_RUNNER);
    assertThat(lage.letzterLauf())
        .extracting(RunnerLage.LetzterLauf::runId, RunnerLage.LetzterLauf::ergebnis)
        .containsExactly(3L, Verdict.FAILED);
  }

  @Test
  void jetzt_ohneLaeufe_istNie() {
    when(runs.findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.NIGHT))
        .thenReturn(List.of());

    assertThat(service.jetzt(USER, PROJECT).gesamt()).isEqualTo(Gesamt.NIE);
    verify(runs, never()).findItemsByRunIds(ArgumentMatchers.any());
  }

  private static final long CARD = 99L;

  @Test
  void wartegrund_prueftDasRechtVorDerSpalte_undVerraetSonstNichts() {
    when(cards.warteKarte(CARD)).thenReturn(new WarteKarteView(PROJECT, 1695, false, JETZT));
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.wartegrund(USER, CARD))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(runs, bewertungen, disruptions);
  }

  @Test
  void wartegrund_einerKarteAusserhalbVonReady_ist404() {
    when(cards.warteKarte(CARD)).thenReturn(new WarteKarteView(PROJECT, 1695, false, JETZT));

    assertThatThrownBy(() -> service.wartegrund(USER, CARD))
        .isInstanceOf(KarteNichtInReadyException.class);

    verify(permissions).requireNightRunAccess(USER, PROJECT);
    verifyNoInteractions(runs, bewertungen, disruptions);
  }

  /** Bewertung, Läufe und Änderungszeitpunkt fließen in die Ableitung. */
  @Test
  void wartegrund_leitetAusLageBewertungUndAenderungAb() {
    when(cards.warteKarte(CARD))
        .thenReturn(new WarteKarteView(PROJECT, 1695, true, T1.plusSeconds(1)));
    when(runs.findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.NIGHT))
        .thenReturn(List.of());
    when(bewertungen.findByProjectId(PROJECT)).thenReturn(Optional.of(bewertung(T1)));

    Wartegrund w = service.wartegrund(USER, CARD);

    assertThat(w.laufgruende())
        .extracting(Wartegrund.Laufgrund::art)
        .containsExactly(LaufgrundArt.KEINE_UMSETZUNGSNACHT);
    assertThat(w.vermerke())
        .extracting(Wartegrund.Vermerk::art)
        .containsExactly(Wartegrund.VermerkArt.SEIT_BEWERTUNG_GEAENDERT);
    assertThat(w.paketeDavor()).isZero();
    assertThat(w.startbereit()).isFalse();
  }

  /** Die Quittungen des Projekts fließen in die Lage: ein quittierter, verstummter Lauf fehlt. */
  @Test
  void jetzt_liestDieQuittungenDesProjekts_undLaesstDenQuittiertenVerstummtenWeg() {
    NightRun verstummt =
        lauf(1L, JETZT.minus(Duration.ofDays(20)), false, JETZT.minus(Duration.ofDays(20)));
    when(runs.findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.NIGHT))
        .thenReturn(List.of(verstummt));
    when(disruptions.acknowledgedRunIds(PROJECT)).thenReturn(Set.of(1L));

    RunnerLage lage = service.jetzt(USER, PROJECT);

    assertThat(lage).isEqualTo(new RunnerLage(Gesamt.KEIN_RUNNER, List.of(), null));
  }

  /**
   * Issue #1742: Ein verstummter, quittierter Lauf ist am wartenden Paket kein Grund „verstummt“.
   */
  @Test
  void wartegrund_nenntEinenQuittiertenVerstummtenLaufNicht() {
    NightRun nacht = lauf(1L, JETZT.minusSeconds(600), false, JETZT.minusSeconds(60));
    NightRun verstummt =
        lauf(2L, JETZT.minus(Duration.ofDays(20)), false, JETZT.minus(Duration.ofDays(20)));
    when(cards.warteKarte(CARD)).thenReturn(new WarteKarteView(PROJECT, 1695, true, T1));
    when(runs.findByProjectAndKindOrderByStartedAtDesc(PROJECT, NightRunKind.NIGHT))
        .thenReturn(List.of(nacht, verstummt));
    when(disruptions.acknowledgedRunIds(PROJECT)).thenReturn(Set.of(2L));
    when(bewertungen.findByProjectId(PROJECT)).thenReturn(Optional.of(bewertung(T1)));

    Wartegrund w = service.wartegrund(USER, CARD);

    assertThat(w.laufgruende())
        .extracting(Wartegrund.Laufgrund::art)
        .doesNotContain(LaufgrundArt.VERSTUMMT);
    assertThat(w.startbereit()).isTrue();
  }
}
