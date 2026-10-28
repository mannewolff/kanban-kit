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
import org.mwolff.manban.card.application.CardRunQueryService.KartenGruppe;
import org.mwolff.manban.comment.application.CommentService;
import org.mwolff.manban.common.Wochenfenster;
import org.mwolff.manban.nightrun.domain.Nacharbeit;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Aktivitaet;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.BoardKarte;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Eingaben;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Kartengruppe;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.Kommentar;
import org.mwolff.manban.nightrun.domain.NacharbeitErmittlung.LabelEreignis;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Use-Case der Platte „Nacharbeit“ (Issue #1757, Plan #1752): sammelt die Spuren der Runs an einem
 * Board über die Fassaden von {@code card} und {@code comment} und übergibt sie an {@link
 * NacharbeitErmittlung}.
 *
 * <p><b>Wer darf (E12):</b> Das Projekt kommt aus {@link BoardService#requireProjectId}, danach
 * {@code requireNightRunAccess} wie jeder lesende Nachtlauf-Use-Case — vor jeder weiteren Abfrage.
 *
 * <p><b>Welche Runs (E4):</b> alle aufbewahrten Läufe des Projekts mit {@code kind = NIGHT}, auch
 * eine tagsüber gestartete Kette. Die Spuren werden ab dem ältesten Laufstart innerhalb der zwölf
 * Wochenfenster gelesen; eine Spur entsteht nie vor dem Start ihres Laufs. Startete im Zeitraum
 * kein Run, liest der Dienst keine Spuren.
 */
@Service
public class NightRunReworkService {

  private final BoardService boards;
  private final PermissionChecker permissions;
  private final NightRunRepository runs;
  private final CardRunQueryService cards;
  private final CommentService comments;
  private final Clock clock;

  public NightRunReworkService(
      BoardService boards,
      PermissionChecker permissions,
      NightRunRepository runs,
      CardRunQueryService cards,
      CommentService comments,
      Clock clock) {
    this.boards = boards;
    this.permissions = permissions;
    this.runs = runs;
    this.cards = cards;
    this.comments = comments;
    this.clock = clock;
  }

  /**
   * Die Nacharbeit der Runs an diesem Board.
   *
   * @throws org.mwolff.manban.board.application.BoardNotFoundException wenn das Board nicht
   *     existiert oder archiviert ist (404)
   * @throws org.mwolff.manban.project.application.ProjectNotFoundException für Nichtmitglieder
   *     (404)
   * @throws org.mwolff.manban.project.application.ProjectAccessDeniedException ohne Recht auf die
   *     Runs (403)
   */
  @Transactional(readOnly = true)
  public Nacharbeit nacharbeit(long userId, long boardId) {
    long projectId = boards.requireProjectId(boardId);
    permissions.requireNightRunAccess(userId, projectId);
    Instant jetzt = clock.instant();
    List<Instant> laufstarts =
        runs.findByProjectAndKindOrderByStartedAtDesc(projectId, NightRunKind.NIGHT).stream()
            .map(NightRun::startedAt)
            .toList();
    Instant erfassungSeit = cards.labelErfassungSeit();
    Optional<Instant> von =
        laufstarts.stream()
            .map(start -> start.truncatedTo(ChronoUnit.MILLIS))
            .filter(start -> Wochenfenster.index(start, jetzt).isPresent())
            .min(Comparator.naturalOrder());
    if (von.isEmpty()) {
      return NacharbeitErmittlung.ermittle(
          new Eingaben(List.of(), laufstarts, erfassungSeit, List.of(), List.of(), List.of()),
          jetzt);
    }
    List<BoardKarteView> karten = cards.boardKarten(boardId);
    List<Long> ids = karten.stream().map(BoardKarteView::id).toList();
    Eingaben eingaben =
        new Eingaben(
            karten.stream()
                .map(k -> new BoardKarte(k.id(), k.number(), k.title(), gruppe(k.gruppe())))
                .toList(),
            laufstarts,
            erfassungSeit,
            cards.laufBewegungen(ids, von.get()).stream()
                .map(
                    b ->
                        new Aktivitaet(
                            b.cardId(), b.type(), b.laufStart(), b.statusAfter(), b.statusVorher()))
                .toList(),
            comments.laufKommentare(ids, von.get()).stream()
                .map(k -> new Kommentar(k.cardId(), k.laufStart()))
                .toList(),
            cards.labelEreignisse(ids, von.get()).stream()
                .map(e -> new LabelEreignis(e.cardId(), e.labelName(), e.laufStart()))
                .toList());
    return NacharbeitErmittlung.ermittle(eingaben, jetzt);
  }

  /** Paketsichtbar für {@link NightRunModelsService} (Issue #1947) — eine Abbildung, nicht zwei. */
  static Kartengruppe gruppe(KartenGruppe gruppe) {
    return switch (gruppe) {
      case PAKET -> Kartengruppe.PAKET;
      case DOKUMENT -> Kartengruppe.DOKUMENT;
      case ANDERE -> Kartengruppe.ANDERE;
    };
  }
}
