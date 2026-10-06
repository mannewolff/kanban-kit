package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.LaufKarteView;
import org.mwolff.manban.card.application.CardRunQueryService.TokenActivityView;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.comment.application.CommentService.LaufstandView;
import org.mwolff.manban.nightrun.domain.CardRef;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.NightRunOrigin;
import org.mwolff.manban.nightrun.domain.NightRunProgress;
import org.mwolff.manban.nightrun.domain.NightRunState;
import org.mwolff.manban.nightrun.domain.PackageProgress;
import org.mwolff.manban.nightrun.domain.PackageState;
import org.mwolff.manban.nightrun.domain.ProgressAssignment;
import org.mwolff.manban.nightrun.domain.ProgressStage;
import org.mwolff.manban.nightrun.domain.StationsZustand;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.domain.Permission;

/**
 * Der Fortschritt eines Laufs als Use-Case (Issue #1375, Plan #1372): Rechteprüfung, Laden des
 * Laufs, Fensterbestimmung und das Umsetzen der Abfragen in die Eingaben der Ermittlung. Die
 * Ermittlung selbst belegt {@code FortschrittErmittlungTest}; hier zählt, was der Dienst ihr gibt.
 */
// PMD.ExcessiveImports: Die Importe folgen den Typen der drei Module, aus denen der Dienst liest
// (card, comment, nightrun) — dieselbe Ursache wie am Dienst selbst.
// PMD.TooManyMethods: zwei Use-Cases des Dienstes (Fortschritt eines Laufs, Kettenstand einer
// Karte), je Erfolgs- und Fehlerpfad ein kleiner Test — kein Refactoring-Signal.
@SuppressWarnings({"PMD.ExcessiveImports", "PMD.TooManyMethods"})
class NightRunProgressServiceTest {

  private static final long USER = 1L;
  private static final long PROJECT = 42L;
  private static final long RUN = 9L;
  private static final long BOARD = 5L;
  private static final String TOKEN = "Nachtlauf";
  private static final Instant JETZT = Instant.parse("2026-10-04T16:00:00Z");
  private static final Instant START = JETZT.minus(Duration.ofMinutes(40));
  private static final Duration STILLE = Duration.ofMinutes(90);
  private static final long KARTEN_ID = 70L;
  private static final int NUMMER = 1420;
  private static final Instant EIGENER_LAUF = Instant.parse("2026-10-04T01:00:00.123Z");
  private static final Instant FREMDER_LAUF = Instant.parse("2026-10-04T02:00:00Z");

  private NightRunRepository runs;
  private PermissionChecker permissions;
  private CardRunQueryService cards;
  private CommentService comments;
  private NightRunProgressService service;

  @BeforeEach
  void setUp() {
    runs = mock(NightRunRepository.class);
    permissions = mock(PermissionChecker.class);
    cards = mock(CardRunQueryService.class);
    comments = mock(CommentService.class);
    service =
        new NightRunProgressService(
            runs,
            permissions,
            cards,
            comments,
            new NightRunProperties(null, null, null, null, STILLE),
            Clock.fixed(JETZT, ZoneOffset.UTC));
  }

  private static NightRun lauf(
      long id,
      Instant startedAt,
      NightRunMode mode,
      @Nullable String tokenName,
      boolean complete,
      long durationMs,
      @Nullable Instant updatedAt) {
    return new NightRun(
        id,
        PROJECT,
        startedAt,
        mode,
        NightRunKind.NIGHT,
        durationMs,
        0,
        0,
        0,
        null,
        startedAt,
        NightRunOrigin.TOKEN,
        tokenName,
        complete,
        updatedAt,
        null,
        null,
        null,
        null,
        null);
  }

  private static NightRun laufend(NightRunMode mode) {
    return lauf(RUN, START, mode, TOKEN, false, 0L, JETZT.minusSeconds(60));
  }

  private void gefunden(NightRun lauf) {
    when(runs.findByIdAndProjectId(RUN, PROJECT)).thenReturn(Optional.of(lauf));
  }

  private static LaufKarteView karte(
      long id, int nummer, String titel, @Nullable String status, @Nullable Long herkunft) {
    return new LaufKarteView(
        id,
        nummer,
        titel,
        BOARD,
        status,
        List.of(),
        herkunft,
        "CARD",
        !titel.startsWith("["),
        null);
  }

  // --- Rechte und Laden ----------------------------------------------------------------------

  @Test
  void verlangtDenNachtlaufZugriff() {
    gefunden(laufend(NightRunMode.CHAIN));

    service.progress(USER, PROJECT, RUN);

    verify(permissions).requireNightRunAccess(USER, PROJECT);
  }

  @Test
  void nichtmitgliedBekommtNotFoundOhneJedeAbfrage() {
    doThrow(new ProjectNotFoundException()).when(permissions).requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.progress(USER, PROJECT, RUN))
        .isInstanceOf(ProjectNotFoundException.class);
    verifyNoInteractions(runs, cards, comments);
  }

  @Test
  void mitgliedOhneOwnerBekommtAccessDeniedOhneJedeAbfrage() {
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.progress(USER, PROJECT, RUN))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verifyNoInteractions(runs, cards, comments);
  }

  /** E10: Ein Lauf eines anderen Projekts ist so unbekannt wie eine erfundene ID. */
  @Test
  void laufEinesAnderenProjektsIstNotFound() {
    when(runs.findByIdAndProjectId(RUN, PROJECT)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.progress(USER, PROJECT, RUN))
        .isInstanceOf(NightRunNotFoundException.class);
    verifyNoInteractions(cards, comments);
  }

  // --- Modi (E8) ------------------------------------------------------------------------------

  @Test
  void reviewLaufLiefertLeerOhneAbfragen() {
    gefunden(laufend(NightRunMode.REVIEW));

    assertThat(service.progress(USER, PROJECT, RUN)).isEqualTo(NightRunProgress.leer());
    verifyNoInteractions(cards, comments);
    verify(runs, never()).findOverlapping(anyLong(), any(), any(), any());
  }

  @Test
  void interaktiveSitzungLiefertLeerOhneAbfragen() {
    gefunden(laufend(NightRunMode.INTERACTIVE));

    assertThat(service.progress(USER, PROJECT, RUN)).isEqualTo(NightRunProgress.leer());
    verifyNoInteractions(cards, comments);
    verify(runs, never()).findOverlapping(anyLong(), any(), any(), any());
  }

  /** E3: Ohne Token-Namen (Browser-Upload) ist der ganze Fortschritt unbekannt — ohne Abfragen. */
  @Test
  void laufOhneTokenNamenIstUnbekanntOhneAbfragen() {
    gefunden(lauf(RUN, START, NightRunMode.CHAIN, null, true, 1000L, null));

    assertThat(service.progress(USER, PROJECT, RUN).zuordnung())
        .isEqualTo(ProgressAssignment.UNBEKANNT);
    verifyNoInteractions(cards, comments);
    verify(runs, never()).findOverlapping(anyLong(), any(), any(), any());
  }

  // --- Fenster (E2) ---------------------------------------------------------------------------

  @Test
  void gemeldeterLaufEndetBeiStartPlusDauer() {
    gefunden(lauf(RUN, START, NightRunMode.CHAIN, TOKEN, true, 600_000L, JETZT.minusSeconds(10)));

    service.progress(USER, PROJECT, RUN);

    Instant ende = START.plusMillis(600_000L);
    verify(cards).tokenActivitiesInWindow(PROJECT, TOKEN, START, ende);
    verify(runs).findOverlapping(PROJECT, TOKEN, START, ende);
  }

  @Test
  void laufenderLaufEndetJetzt() {
    gefunden(laufend(NightRunMode.IMPLEMENTATION));

    service.progress(USER, PROJECT, RUN);

    verify(cards).tokenActivitiesInWindow(PROJECT, TOKEN, START, JETZT);
  }

  /** Genau auf der Frist ist der Lauf noch nicht verstummt — dieselbe Regel wie im Befund. */
  @Test
  void laufGenauAufDerStillefristLaeuftNoch() {
    Instant start = JETZT.minus(Duration.ofHours(3));
    gefunden(lauf(RUN, start, NightRunMode.CHAIN, TOKEN, false, 0L, JETZT.minus(STILLE)));

    service.progress(USER, PROJECT, RUN);

    verify(cards).tokenActivitiesInWindow(PROJECT, TOKEN, start, JETZT);
  }

  @Test
  void verstummterLaufEndetBeiSeinerLetztenMeldung() {
    Instant start = JETZT.minus(Duration.ofHours(5));
    Instant letzte = JETZT.minus(STILLE).minusSeconds(1);
    gefunden(lauf(RUN, start, NightRunMode.CHAIN, TOKEN, false, 0L, letzte));

    service.progress(USER, PROJECT, RUN);

    verify(cards).tokenActivitiesInWindow(PROJECT, TOKEN, start, letzte);
  }

  /** Ohne je eine Meldung ist der Start das letzte Lebenszeichen. */
  @Test
  void verstummterLaufOhneMeldungEndetBeimStart() {
    Instant start = JETZT.minus(Duration.ofHours(5));
    gefunden(lauf(RUN, start, NightRunMode.CHAIN, TOKEN, false, 0L, null));

    service.progress(USER, PROJECT, RUN);

    verify(cards).tokenActivitiesInWindow(PROJECT, TOKEN, start, start);
  }

  // --- Eingaben der Ermittlung -----------------------------------------------------------------

  /**
   * Der Weg im Ganzen: Aktivitäten, Karten samt Herkunft und Laufstände gehen in die Ermittlung,
   * und ein Paket, das der Lauf angelegt und bis In review bewegt hat, steht als fertig da.
   */
  @Test
  void setztDieAbfragenInDieEingabenDerErmittlungUm() {
    gefunden(laufend(NightRunMode.CHAIN));
    when(cards.tokenActivitiesInWindow(PROJECT, TOKEN, START, JETZT))
        .thenReturn(
            List.of(
                new TokenActivityView(20L, "CREATED", START.plusSeconds(60), null, null),
                new TokenActivityView(30L, "CREATED", START.plusSeconds(120), null, null),
                new TokenActivityView(30L, "STATUS_CHANGED", START.plusSeconds(180), null, null)));
    when(comments.laufstaendeImProjekt(PROJECT))
        .thenReturn(
            List.of(
                new LaufstandView(
                    10L,
                    "## Laufstand\n\nzuletzt fertig: review fertig für #2 um "
                        + START.plusSeconds(90),
                    null)));
    LaufKarteView anforderung = karte(10L, 1, "[Fachlich] Fortschritt", null, null);
    LaufKarteView plan = karte(20L, 2, "[Plan] Fortschritt", null, 10L);
    LaufKarteView paket = karte(30L, 3, "Paket 1/1", "IN_REVIEW", 20L);
    when(cards.cardsByIds(any()))
        .thenAnswer(
            inv -> {
              Collection<Long> ids = inv.getArgument(0);
              return List.of(anforderung, plan, paket).stream()
                  .filter(k -> ids.contains(k.id()))
                  .toList();
            });

    NightRunProgress fortschritt = service.progress(USER, PROJECT, RUN);

    assertThat(fortschritt.zuordnung()).isEqualTo(ProgressAssignment.OK);
    assertThat(fortschritt.pakete())
        .containsExactly(
            new PackageProgress(new CardRef(3, "Paket 1/1", BOARD), PackageState.FERTIG));
    assertThat(fortschritt.ketten())
        .singleElement()
        .satisfies(
            k -> {
              assertThat(k.anforderung())
                  .isEqualTo(new CardRef(1, "[Fachlich] Fortschritt", BOARD));
              assertThat(k.plan()).isEqualTo(new CardRef(2, "[Plan] Fortschritt", BOARD));
            });
  }

  /**
   * Laufkennung und Status danach gehen aus Aktivitäten und Laufständen in die Ermittlung (Issue
   * #1429): Die Spur des fremden Laufs fällt weg, das eigene Paket steht mit dem Status seiner
   * Bewegung da statt mit dem heutigen, und der eigene Laufstand trägt die Kette.
   */
  @Test
  void reichtLaufkennungUndStatusDanachDurch() {
    gefunden(laufend(NightRunMode.CHAIN));
    when(cards.tokenActivitiesInWindow(PROJECT, TOKEN, START, JETZT))
        .thenReturn(
            List.of(
                new TokenActivityView(30L, "MOVED", START.plusSeconds(60), START, "READY"),
                new TokenActivityView(
                    31L, "MOVED", START.plusSeconds(70), START.plusSeconds(5), "IN_REVIEW")));
    when(comments.laufstaendeImProjekt(PROJECT))
        .thenReturn(
            List.of(
                new LaufstandView(
                    10L,
                    "## Laufstand\n\nzuletzt begonnen: plan begonnen für #1 um "
                        + START.plusSeconds(30),
                    START)));
    LaufKarteView anforderung = karte(10L, 1, "[Fachlich] Fortschritt", null, null);
    LaufKarteView eigenes = karte(30L, 3, "Paket 1/2", "IN_REVIEW", null);
    LaufKarteView fremdes = karte(31L, 4, "Paket 2/2", "IN_REVIEW", null);
    when(cards.cardsByIds(any()))
        .thenAnswer(
            inv -> {
              Collection<Long> ids = inv.getArgument(0);
              return List.of(anforderung, eigenes, fremdes).stream()
                  .filter(k -> ids.contains(k.id()))
                  .toList();
            });

    NightRunProgress fortschritt = service.progress(USER, PROJECT, RUN);

    assertThat(fortschritt.pakete())
        .containsExactly(
            new PackageProgress(new CardRef(3, "Paket 1/2", BOARD), PackageState.GEZOGEN));
    assertThat(fortschritt.ketten())
        .singleElement()
        .satisfies(
            k ->
                assertThat(k.anforderung())
                    .isEqualTo(new CardRef(1, "[Fachlich] Fortschritt", BOARD)));
  }

  /**
   * Die Anforderung eines vom Lauf angelegten Plans hat der Lauf selbst nie angefasst: Der Dienst
   * holt sie über die Herkunft nach, Stufe für Stufe bis keine neue Karte mehr dazukommt.
   */
  @Test
  void holtDieHerkunftDerKartenNach() {
    gefunden(laufend(NightRunMode.CHAIN));
    when(cards.tokenActivitiesInWindow(PROJECT, TOKEN, START, JETZT))
        .thenReturn(
            List.of(new TokenActivityView(30L, "CREATED", START.plusSeconds(60), null, null)));
    when(comments.laufstaendeImProjekt(PROJECT)).thenReturn(List.of());
    LaufKarteView anforderung = karte(10L, 1, "[Fachlich] Fortschritt", null, null);
    LaufKarteView plan = karte(20L, 2, "[Plan] Fortschritt", null, 10L);
    LaufKarteView paket = karte(30L, 3, "Paket 1/1", "BACKLOG", 20L);
    when(cards.cardsByIds(any()))
        .thenAnswer(
            inv -> {
              Collection<Long> ids = inv.getArgument(0);
              return List.of(anforderung, plan, paket).stream()
                  .filter(k -> ids.contains(k.id()))
                  .toList();
            });

    service.progress(USER, PROJECT, RUN);

    // Ein ArgumentCaptor auf einen generischen Typ ist nicht typsicher erzeugbar; der Dienst
    // übergibt nur Collection<Long>.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(cards, times(3)).cardsByIds(captor.capture());
    assertThat(captor.getAllValues())
        .map(Set::copyOf)
        .containsExactly(Set.of(30L), Set.of(20L), Set.of(10L));
  }

  /**
   * E3: Der Lauf selbst steht im Ergebnis von {@code findOverlapping}; zählte er als fremdes
   * Fenster, wäre jede seiner Karten unbekannt.
   */
  @Test
  void derLaufSelbstIstKeinFremdesFenster() {
    NightRun selbst = laufend(NightRunMode.IMPLEMENTATION);
    gefunden(selbst);
    when(runs.findOverlapping(PROJECT, TOKEN, START, JETZT)).thenReturn(List.of(selbst));
    when(cards.tokenActivitiesInWindow(PROJECT, TOKEN, START, JETZT))
        .thenReturn(
            List.of(new TokenActivityView(30L, "MOVED", START.plusSeconds(60), null, null)));
    when(cards.cardsByIds(any()))
        .thenReturn(List.of(karte(30L, 3, "Paket 1/1", "IN_PROGRESS", null)));

    NightRunProgress fortschritt = service.progress(USER, PROJECT, RUN);

    assertThat(fortschritt.unbekannt()).isEmpty();
    assertThat(fortschritt.pakete())
        .extracting(PackageProgress::zustand)
        .containsExactly(PackageState.IN_UMSETZUNG);
  }

  /**
   * E3: Ein anderer Nachtlauf mit demselben Token, dessen Fenster die Lauf-Aktivität einer Karte
   * enthält, macht sie unbekannt. Sein Fenster bestimmt der Dienst nach derselben Regel — hier
   * gemeldet, also Start plus Dauer.
   */
  @Test
  void einUeberlappenderLaufMachtSeineKartenUnbekannt() {
    gefunden(laufend(NightRunMode.IMPLEMENTATION));
    NightRun fremd =
        lauf(RUN + 1, START.plusSeconds(100), NightRunMode.CHAIN, TOKEN, true, 100_000L, null);
    when(runs.findOverlapping(PROJECT, TOKEN, START, JETZT)).thenReturn(List.of(fremd));
    when(cards.tokenActivitiesInWindow(PROJECT, TOKEN, START, JETZT))
        .thenReturn(
            List.of(
                new TokenActivityView(30L, "MOVED", START.plusSeconds(150), null, null),
                new TokenActivityView(31L, "MOVED", START.plusSeconds(250), null, null)));
    when(cards.cardsByIds(any()))
        .thenReturn(
            List.of(
                karte(30L, 3, "Paket 1/2", "IN_PROGRESS", null),
                karte(31L, 4, "Paket 2/2", "IN_PROGRESS", null)));

    NightRunProgress fortschritt = service.progress(USER, PROJECT, RUN);

    assertThat(fortschritt.unbekannt()).containsExactly(new CardRef(3, "Paket 1/2", BOARD));
    assertThat(fortschritt.pakete()).extracting(p -> p.karte().number()).containsExactly(4);
  }

  /**
   * Eine Karte, die der Lauf nur über ihren Laufstand kennt — ohne eine Aktivität an ihr —, wird
   * geladen und steht im Fortschritt (Issue #1383).
   */
  @Test
  void karteNurAusDemLaufstandWirdGeladen() {
    gefunden(laufend(NightRunMode.CHAIN));
    when(comments.laufstaendeImProjekt(PROJECT))
        .thenReturn(
            List.of(
                new LaufstandView(
                    10L,
                    "## Laufstand\n\nzuletzt begonnen: plan begonnen für #1 um "
                        + START.plusSeconds(90),
                    null)));
    when(cards.cardsByIds(List.of(10L)))
        .thenReturn(List.of(karte(10L, 1, "[Fachlich] Fortschritt", null, null)));

    NightRunProgress fortschritt = service.progress(USER, PROJECT, RUN);

    verify(cards).cardsByIds(List.of(10L));
    assertThat(fortschritt.ketten())
        .singleElement()
        .satisfies(
            k ->
                assertThat(k.anforderung())
                    .isEqualTo(new CardRef(1, "[Fachlich] Fortschritt", BOARD)));
  }

  /**
   * Ist die Herkunft einer Karte schon in derselben Runde geladen, fragt der Dienst sie nicht ein
   * zweites Mal ab (Issue #1383).
   */
  @Test
  void bekannteHerkunftWirdNichtErneutGeladen() {
    gefunden(laufend(NightRunMode.CHAIN));
    when(cards.tokenActivitiesInWindow(PROJECT, TOKEN, START, JETZT))
        .thenReturn(
            List.of(
                new TokenActivityView(20L, "CREATED", START.plusSeconds(60), null, null),
                new TokenActivityView(30L, "CREATED", START.plusSeconds(120), null, null)));
    LaufKarteView plan = karte(20L, 2, "[Plan] Fortschritt", null, null);
    LaufKarteView paket = karte(30L, 3, "Paket 1/1", "BACKLOG", 20L);
    when(cards.cardsByIds(any()))
        .thenAnswer(
            inv -> {
              Collection<Long> ids = inv.getArgument(0);
              return List.of(plan, paket).stream().filter(k -> ids.contains(k.id())).toList();
            });

    service.progress(USER, PROJECT, RUN);

    verify(cards, times(1)).cardsByIds(any());
  }

  /** Ohne Aktivität und ohne Laufstand fragt der Dienst keine Karten ab. */
  @Test
  void ohneSpurenWirdNichtsGeladen() {
    gefunden(laufend(NightRunMode.CHAIN));

    assertThat(service.progress(USER, PROJECT, RUN).ketten()).isEmpty();
    verify(cards, never()).cardsByIds(any());
  }

  // --- Kettenstand einer Karte (Issue #1452, Plan #1447 E14, E15) ----------------------------

  private static LaufKarteView karteMit(
      long id,
      int nummer,
      String titel,
      List<String> labels,
      @Nullable Long herkunft,
      @Nullable String beschreibung) {
    return new LaufKarteView(
        id, nummer, titel, BOARD, "BACKLOG", labels, herkunft, "CARD", false, beschreibung);
  }

  private void karteImProjekt(LaufKarteView karte) {
    when(cards.requireProjectId(karte.id())).thenReturn(PROJECT);
    when(cards.cardsByIds(List.of(karte.id()))).thenReturn(List.of(karte));
  }

  private void laufstandAn(long cardId, String body, @Nullable Instant laufStart) {
    when(comments.laufstaendeImProjekt(PROJECT))
        .thenReturn(
            List.of(
                new LaufstandView(cardId + 1, "## Laufstand\nZiel: plan", FREMDER_LAUF),
                new LaufstandView(cardId, body, laufStart)));
  }

  private static NightRunItem anlaufAm(Instant startedAt) {
    return new NightRunItem(
        1L,
        2L,
        PROJECT,
        startedAt,
        NightRunMode.CHAIN,
        NightRunKind.NIGHT,
        NUMMER,
        "[Fachlich] Kette",
        NightRunState.GREEN,
        null,
        1000L,
        null,
        null,
        null,
        List.of());
  }

  @Test
  void kettenstandPrueftDasLeserechtDerKarte() {
    when(cards.requireProjectId(KARTEN_ID)).thenReturn(PROJECT);
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .require(USER, PROJECT, Permission.TICKET_READ);

    assertThatThrownBy(() -> service.kettenstand(USER, KARTEN_ID))
        .isInstanceOf(ProjectAccessDeniedException.class);
    verify(cards, never()).cardsByIds(any());
    verifyNoInteractions(comments, runs);
  }

  @Test
  void kettenstandLeitetDenStandAusDemLaufstandDerKarteAb() {
    karteImProjekt(karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of(), null, null));
    laufstandAn(
        KARTEN_ID, "## Laufstand\nZiel: umsetzung\nPrüfer: 2\nfertig bis umsetzung", EIGENER_LAUF);

    KettenstandDerKarte stand = service.kettenstand(USER, KARTEN_ID);

    verify(permissions).require(USER, PROJECT, Permission.TICKET_READ);
    assertThat(stand.stand().ziel()).isEqualTo(ProgressStage.UMSETZUNG);
    assertThat(stand.stand().pruefer()).isEqualTo(2);
    assertThat(stand.stand().zielErreicht()).isTrue();
    assertThat(stand.stand().stationen())
        .filteredOn(s -> s.station() == ProgressStage.VORBEREITUNG)
        .singleElement()
        .satisfies(s -> assertThat(s.zustand()).isEqualTo(StationsZustand.NICHT_VORGESEHEN));
  }

  /** Ohne Laufstand steht die Kette vor dem Start: kein Ziel, nichts übernommen. */
  @Test
  void kettenstandOhneLaufstandStehtVorDemStart() {
    karteImProjekt(karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of(), null, null));
    when(comments.laufstaendeImProjekt(PROJECT)).thenReturn(List.of());
    when(runs.findByCard(PROJECT, NUMMER)).thenReturn(List.of());

    KettenstandDerKarte stand = service.kettenstand(USER, KARTEN_ID);

    assertThat(stand.stand().ziel()).isNull();
    assertThat(stand.uebernommen()).isFalse();
    assertThat(stand.lauf()).isNull();
  }

  /** E15: Der Lauf ist der, den der Laufstand der Karte nennt — nicht der jüngste Anlauf. */
  @Test
  void laufwahlNimmtDieLaufkennungDesLaufstands() {
    karteImProjekt(karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of(), null, null));
    laufstandAn(KARTEN_ID, "## Laufstand\nZiel: plan", EIGENER_LAUF);

    assertThat(service.kettenstand(USER, KARTEN_ID).lauf()).isEqualTo(EIGENER_LAUF);
    verify(runs, never()).findByCard(anyLong(), any(Integer.class));
  }

  /**
   * #1419: Ein paralleler Runner hat die Karte später angefasst, der Laufstand gehört aber dem
   * eigenen Lauf — gewählt wird der eigene.
   */
  @Test
  void laufwahlUebergehtEinenParallelenFremdenLauf() {
    karteImProjekt(karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of(), null, null));
    laufstandAn(KARTEN_ID, "## Laufstand\nZiel: plan", EIGENER_LAUF);
    when(runs.findByCard(PROJECT, NUMMER))
        .thenReturn(List.of(anlaufAm(FREMDER_LAUF), anlaufAm(EIGENER_LAUF)));

    assertThat(service.kettenstand(USER, KARTEN_ID).lauf()).isEqualTo(EIGENER_LAUF);
  }

  /** E15: Ohne Laufkennung im Laufstand gilt der jüngste Anlauf der Karte. */
  @Test
  void laufwahlFaelltOhneLaufkennungAufDenJuengstenAnlaufZurueck() {
    karteImProjekt(karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of(), null, null));
    laufstandAn(KARTEN_ID, "## Laufstand\nZiel: plan", null);
    when(runs.findByCard(PROJECT, NUMMER))
        .thenReturn(List.of(anlaufAm(FREMDER_LAUF), anlaufAm(EIGENER_LAUF)));

    KettenstandDerKarte stand = service.kettenstand(USER, KARTEN_ID);

    assertThat(stand.lauf()).isEqualTo(FREMDER_LAUF);
    assertThat(stand.uebernommen()).isFalse();
  }

  @Test
  void uebernommenMitLaufkennungUndOhneFreigabe() {
    karteImProjekt(
        karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of("lauf:laeuft"), null, null));
    laufstandAn(KARTEN_ID, "## Laufstand\nZiel: plan", EIGENER_LAUF);

    assertThat(service.kettenstand(USER, KARTEN_ID).uebernommen()).isTrue();
  }

  /** Trägt die Karte noch {@code kit:night}, wartet sie auf den nächsten Runner. */
  @Test
  void nichtUebernommenSolangeDieFreigabeSteht() {
    karteImProjekt(
        karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of("kit:night"), null, null));
    laufstandAn(KARTEN_ID, "## Laufstand\nZiel: plan", EIGENER_LAUF);

    assertThat(service.kettenstand(USER, KARTEN_ID).uebernommen()).isFalse();
  }

  @Test
  void planReviewVorhandenAnDerAnforderungMitGeprueftemPlan() {
    karteImProjekt(karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of(), null, null));
    when(cards.derivedCards(KARTEN_ID))
        .thenReturn(
            List.of(
                karteMit(71L, 1421, "Notiz", List.of(), KARTEN_ID, "Plan-Review: fable"),
                karteMit(
                    72L, 1422, "[Plan] Kette", List.of(), KARTEN_ID, "x\nPlan-Review: fable")));

    assertThat(service.kettenstand(USER, KARTEN_ID).planReviewVorhanden()).isTrue();
  }

  @Test
  void keinPlanReviewOhneZeileImPlan() {
    karteImProjekt(karteMit(KARTEN_ID, NUMMER, "[Fachlich] Kette", List.of(), null, null));
    when(cards.derivedCards(KARTEN_ID))
        .thenReturn(
            List.of(
                karteMit(72L, 1422, "[Plan] Kette", List.of(), KARTEN_ID, "Plan-Review:  "),
                karteMit(73L, 1423, "[Plan] Zweiter", List.of(), KARTEN_ID, null)));

    assertThat(service.kettenstand(USER, KARTEN_ID).planReviewVorhanden()).isFalse();
  }

  /** Nur an einer fachlichen Anforderung — ein Plan mit geprüftem Folgeplan zählt nicht. */
  @Test
  void keinPlanReviewAnEinerKarteOhneFachlichPraefix() {
    karteImProjekt(karteMit(KARTEN_ID, NUMMER, "[Plan] Kette", List.of(), null, null));
    when(cards.derivedCards(KARTEN_ID))
        .thenReturn(
            List.of(
                karteMit(72L, 1422, "[Plan] Kette", List.of(), KARTEN_ID, "Plan-Review: fable")));

    assertThat(service.kettenstand(USER, KARTEN_ID).planReviewVorhanden()).isFalse();
  }
}
