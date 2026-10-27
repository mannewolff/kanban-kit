package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardRunQueryService;
import org.mwolff.manban.card.application.CardRunQueryService.BoardKarteView;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.nightrun.domain.Modellvergleich;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung.Eingaben;
import org.mwolff.manban.nightrun.domain.ModellvergleichErmittlung.Vorgang;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Aktivitaet;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.BoardKarte;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.LabelEreignis;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Case der Platte „Modelle“ (Issue #1947, Plan #1905 E9): lädt die gemeldeten Vorgänge des
 * Projekts und die Spuren der Runs an einem Board über die Fassade von {@code card} und übergibt
 * sie an {@link ModellvergleichErmittlung}. Muster ist {@link NightRunReworkService}.
 *
 * <p><b>Wer darf (E13):</b> Das Projekt kommt aus {@link BoardService#requireProjectId}, danach
 * {@code requireNightRunAccess} wie die Platte „Nacharbeit“ — vor jeder weiteren Abfrage.
 *
 * <p><b>Welche Vorgänge (E7):</b> die der Pakete mit Start ab dem Beginn des ältesten der zwölf
 * {@link Wochenfenster}; Gattung, Abschluss und Board filtert die Ermittlung. Die Spuren werden ab
 * dem frühesten Laufstart eines Nachtlauf-Vorgangs gelesen, auf Millisekunden gekürzt wie die
 * Laufkennung; ohne einen solchen liest der Dienst keine Spuren.
 */
@Service
public class NightRunModelsService {

  private final BoardService boards;
  private final PermissionChecker permissions;
  private final NightRunRepository runs;
  private final CardRunQueryService cards;
  private final Clock clock;

  public NightRunModelsService(
      BoardService boards,
      PermissionChecker permissions,
      NightRunRepository runs,
      CardRunQueryService cards,
      Clock clock) {
    this.boards = boards;
    this.permissions = permissions;
    this.runs = runs;
    this.cards = cards;
    this.clock = clock;
  }

  /**
   * Der Modellvergleich der Runs an diesem Board.
   *
   * @throws org.mwolff.manban.board.application.BoardNotFoundException wenn das Board nicht
   *     existiert oder archiviert ist (404)
   * @throws org.mwolff.manban.project.application.ProjectNotFoundException für Nichtmitglieder
   *     (404)
   * @throws org.mwolff.manban.project.application.ProjectAccessDeniedException ohne Recht auf die
   *     Runs (403)
   */
  @Transactional(readOnly = true)
  public Modellvergleich modelle(long userId, long boardId) {
    long projectId = boards.requireProjectId(boardId);
    permissions.requireNightRunAccess(userId, projectId);
    Instant jetzt = clock.instant();
    List<Vorgang> vorgaenge = runs.findAttemptsSince(projectId, Wochenfenster.beginn(0, jetzt));
    Optional<Instant> von =
        vorgaenge.stream()
            .filter(v -> v.kind() == NightRunKind.NIGHT)
            .map(v -> v.laufStart().truncatedTo(ChronoUnit.MILLIS))
            .min(Comparator.naturalOrder());
    if (von.isEmpty()) {
      return ModellvergleichErmittlung.ermittle(
          new Eingaben(List.of(), vorgaenge, List.of(), List.of()), jetzt);
    }
    List<BoardKarteView> karten = cards.boardKarten(boardId);
    List<Long> ids = karten.stream().map(BoardKarteView::id).toList();
    Eingaben eingaben =
        new Eingaben(
            karten.stream()
                .map(
                    k ->
                        new BoardKarte(
                            k.id(),
                            k.number(),
                            k.title(),
                            NightRunReworkService.gruppe(k.gruppe())))
                .toList(),
            vorgaenge,
            cards.laufBewegungen(ids, von.get()).stream()
                .map(
                    b ->
                        new Aktivitaet(
                            b.cardId(), b.type(), b.laufStart(), b.statusAfter(), b.statusVorher()))
                .toList(),
            cards.labelEreignisse(ids, von.get()).stream()
                .map(e -> new LabelEreignis(e.cardId(), e.labelName(), e.laufStart()))
                .toList());
    return ModellvergleichErmittlung.ermittle(eingaben, jetzt);
  }
}
