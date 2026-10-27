package org.mwolff.manban.nightrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.BoardKarteView;
import org.mwolff.manban.card.application.CardRunQueryService.KartenGruppe;
import org.mwolff.manban.card.application.CardRunQueryService.LabelEreignisView;
import org.mwolff.manban.card.application.CardRunQueryService.LaufBewegungView;
import org.mwolff.manban.nightrun.domain.Modellvergleich;
import org.mwolff.manban.nightrun.domain.Modellvergleich.Zeile;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung.Vorgang;
import org.mwolff.manban.nightrun.domain.NightRunAttempt;
import org.mwolff.manban.nightrun.domain.NightRunAttemptStage;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/**
 * Der Dienst der Platte „Modelle“ (Issue #1947, Plan #1905 E7, E9, E13): Recht vor jeder Abfrage,
 * Projekt über die board-Fassade, Vorgänge der zwölf Wochenfenster aus dem Port und die Abbildung
 * der Fassaden-Sichten auf die Eingaben der Ermittlung.
 */
class NightRunModelsServiceTest {

  private static final long USER = 7L;
  private static final long BOARD = 11L;
  private static final long PROJECT = 42L;
  private static final Instant JETZT = Instant.parse("2026-10-15T06:00:00Z");

  /** Beginn des ältesten der zwölf Wochenfenster. */
  private static final Instant FENSTER = JETZT.minus(Duration.ofDays(84));

  /** Ein Laufstart mit Mikrosekunden, wie Postgres ihn liefert; die Spuren tragen Millisekunden. */
  private static final Instant LAUF = Instant.parse("2026-10-14T22:00:00.123456Z");

  private static final Instant LAUF_MS = Instant.parse("2026-10-14T22:00:00.123Z");
  private static final Instant FRUEHER_LAUF = Instant.parse("2026-10-07T22:00:00.5Z");

  private static final int PAKET = 1701;
  private static final int FACHPLAN = 1702;
  private static final int PLAN_KARTE = 1703;

  private final BoardService boards = mock(BoardService.class);
  private final PermissionChecker permissions = mock(PermissionChecker.class);
  private final NightRunRepository runs = mock(NightRunRepository.class);
  private final CardRunQueryService cards = mock(CardRunQueryService.class);
  private final NightRunModelsService service =
      new NightRunModelsService(
          boards, permissions, runs, cards, Clock.fixed(JETZT, ZoneOffset.UTC));

  private static NightRunAttempt umsetzung(String model) {
    return new NightRunAttempt(
        NightRunAttemptStage.UMSETZUNG, PAKET, model, null, true, null, List.of());
  }

  private static List<Vorgang> fuenfUmsetzungen() {
    return IntStream.range(0, 5)
        .mapToObj(i -> new Vorgang(LAUF, NightRunKind.NIGHT, true, i, umsetzung("fable")))
        .toList();
  }

  private void vorgaenge(List<Vorgang> vorgaenge) {
    when(boards.requireProjectId(BOARD)).thenReturn(PROJECT);
    when(runs.findAttemptsSince(PROJECT, FENSTER)).thenReturn(vorgaenge);
  }

  private void boardKarten() {
    when(cards.boardKarten(BOARD))
        .thenReturn(
            List.of(
                new BoardKarteView(1L, PAKET, "Paket", KartenGruppe.PAKET),
                new BoardKarteView(2L, FACHPLAN, "[Requirement] Fachplan", KartenGruppe.DOKUMENT),
                new BoardKarteView(3L, PLAN_KARTE, "[Plan] Angehalten", KartenGruppe.DOKUMENT),
                new BoardKarteView(9L, 1709, "[Idee] Andere", KartenGruppe.ANDERE)));
  }

  private static Zeile zeile(Modellvergleich vergleich, NightRunAttemptStage stufe) {
    return vergleich.stufen().stream()
        .filter(s -> s.stufe() == stufe)
        .findFirst()
        .orElseThrow()
        .zeilen()
        .getFirst();
  }

  @Test
  @DisplayName("Szenario: Ohne Recht auf die Läufe")
  void ohneRechtAufDieLaeufe() {
    when(boards.requireProjectId(BOARD)).thenReturn(PROJECT);
    doThrow(new ProjectAccessDeniedException())
        .when(permissions)
        .requireNightRunAccess(USER, PROJECT);

    assertThatThrownBy(() -> service.modelle(USER, BOARD))
        .isInstanceOf(ProjectAccessDeniedException.class);

    verifyNoInteractions(runs, cards);
  }

  @Test
  void einUnbekanntesBoardEndetVorDerRechtepruefung() {
    when(boards.requireProjectId(BOARD)).thenThrow(new BoardNotFoundException());

    assertThatThrownBy(() -> service.modelle(USER, BOARD))
        .isInstanceOf(BoardNotFoundException.class);

    verifyNoInteractions(permissions, runs, cards);
  }

  @Test
  void dasProjektKommtAusDerBoardFassadeUndDasRechtVorDenVorgaengen() {
    vorgaenge(List.of());

    service.modelle(USER, BOARD);

    InOrder reihenfolge = inOrder(boards, permissions, runs);
    reihenfolge.verify(boards).requireProjectId(BOARD);
    reihenfolge.verify(permissions).requireNightRunAccess(USER, PROJECT);
    reihenfolge.verify(runs).findAttemptsSince(PROJECT, FENSTER);
  }

  @Test
  void ohneVorgangWerdenKeineSpurenGelesen() {
    vorgaenge(List.of());

    Modellvergleich vergleich = service.modelle(USER, BOARD);

    assertThat(vergleich.kopf().datenbeginn()).isNull();
    assertThat(vergleich.stufen()).hasSize(5).allSatisfy(s -> assertThat(s.zeilen()).isEmpty());
    verify(cards, never()).boardKarten(anyLong());
    verify(cards, never()).laufBewegungen(anyCollection(), any());
    verify(cards, never()).labelEreignisse(anyCollection(), any());
  }

  @Test
  void dieSpurenWerdenAbDemFruehestenLaufstartGelesen_aufMillisekundenGekuerzt() {
    vorgaenge(
        List.of(
            new Vorgang(LAUF, NightRunKind.NIGHT, true, 0, umsetzung("fable")),
            new Vorgang(FRUEHER_LAUF, NightRunKind.NIGHT, true, 1, umsetzung("fable"))));
    boardKarten();

    service.modelle(USER, BOARD);

    verify(cards).laufBewegungen(List.of(1L, 2L, 3L, 9L), FRUEHER_LAUF);
    verify(cards).labelEreignisse(List.of(1L, 2L, 3L, 9L), FRUEHER_LAUF);
  }

  @Test
  void derBeginnDerSpurenIstAufMillisekundenGekuerzt() {
    vorgaenge(List.of(new Vorgang(LAUF, NightRunKind.NIGHT, true, 0, umsetzung("fable"))));
    when(cards.boardKarten(BOARD)).thenReturn(List.of());

    service.modelle(USER, BOARD);

    verify(cards).laufBewegungen(List.of(), LAUF_MS);
    verify(cards).labelEreignisse(List.of(), LAUF_MS);
  }

  @Test
  @DisplayName("Szenario: Eine interaktive Sitzung zählt nicht")
  void eineInteraktiveSitzungZaehltNicht() {
    vorgaenge(fuenfUmsetzungen());
    boardKarten();
    Modellvergleich ohneSitzung = service.modelle(USER, BOARD);

    List<Vorgang> mitSitzung = new ArrayList<>(fuenfUmsetzungen());
    mitSitzung.add(
        new Vorgang(FRUEHER_LAUF, NightRunKind.INTERACTIVE, true, 5, umsetzung("gpt-astra")));
    vorgaenge(mitSitzung);
    Modellvergleich mitInteraktiverSitzung = service.modelle(USER, BOARD);

    assertThat(mitInteraktiverSitzung).isEqualTo(ohneSitzung);
    assertThat(zeile(mitInteraktiverSitzung, NightRunAttemptStage.UMSETZUNG).vorgaenge())
        .isEqualTo(5);
    // Die Sitzung verschiebt auch den Beginn der Spuren nicht: gelesen wird ab dem Laufstart.
    verify(cards, never()).laufBewegungen(anyCollection(), eq(FRUEHER_LAUF));
  }

  @Test
  void dieSichtenDerFassadenGehenAlsEingabenInDieErmittlung() {
    List<Vorgang> vorgaenge = new ArrayList<>(fuenfUmsetzungen());
    vorgaenge.add(
        new Vorgang(
            LAUF,
            NightRunKind.NIGHT,
            true,
            5,
            new NightRunAttempt(
                NightRunAttemptStage.PLAN,
                FACHPLAN,
                "claude-opus-5-5",
                null,
                true,
                null,
                List.of(PLAN_KARTE))));
    vorgaenge(vorgaenge);
    boardKarten();
    when(cards.laufBewegungen(List.of(1L, 2L, 3L, 9L), LAUF_MS))
        .thenReturn(
            List.of(new LaufBewegungView(1L, "MOVED", LAUF_MS, LAUF_MS, "READY", "IN_PROGRESS")));
    when(cards.labelEreignisse(List.of(1L, 2L, 3L, 9L), LAUF_MS))
        .thenReturn(List.of(new LabelEreignisView(3L, "kit:clarify", LAUF_MS, LAUF_MS)));

    Modellvergleich vergleich = service.modelle(USER, BOARD);

    assertThat(vergleich.kopf().datenbeginn()).isEqualTo(LAUF);
    // Der Rückzug trifft die erste Umsetzung des Pakets, der Halt den Plan-Vorgang, der die Karte
    // schrieb — beide Signale kommen also über die Fassaden an.
    Zeile umsetzung = zeile(vergleich, NightRunAttemptStage.UMSETZUNG);
    assertThat(umsetzung.vorgaenge()).isEqualTo(5);
    assertThat(umsetzung.erfolgreich()).isEqualTo(4);
    Zeile plan = zeile(vergleich, NightRunAttemptStage.PLAN);
    assertThat(plan.model()).isEqualTo("claude-opus-5-5");
    assertThat(plan.erfolgreich()).isZero();
  }
}
