package org.mwolff.manban.card.application;

import java.util.List;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Der feste Satz an Labels, die das claude-workflow-kit, die Laufsteuerung und die Stufenleiste auf
 * jedem Board erwarten, und sein Anlegen auf allen aktiven Boards der Installation (Issue #1485).
 * Beides verlangt einen Plattform-Admin.
 *
 * <p>Das Anlegen ist idempotent: Ein Label, das ein Board schon trägt, bleibt mit Farbe und
 * Zuordnungen unverändert und zählt als übersprungen — auch, wenn ein gleichzeitiger zweiter Aufruf
 * es gerade angelegt hat ({@link LabelRepository#insertIfAbsent}).
 */
@Service
public class StandardLabelService {

  private static final String KIT = "Kit";
  private static final String LAUF = "Lauf";
  private static final String REVIEW = "Review";
  private static final String STUFENLEISTE = "Stufenleiste";

  private static final String FARBE_KIT = "#6a1b9a";
  private static final String FARBE_LAUF = "#1565c0";
  private static final String FARBE_REVIEW = "#2e7d32";
  private static final String FARBE_STUFENLEISTE = "#ef6c00";

  /** Der Standardsatz in der Reihenfolge, in der die Oberfläche ihn zeigt. */
  private static final List<StandardLabel> SATZ =
      List.of(
          new StandardLabel("kit:durchziehen", KIT, FARBE_KIT),
          new StandardLabel("kit:geschuetzt", KIT, FARBE_KIT),
          new StandardLabel("kit:klaeren", KIT, FARBE_KIT),
          new StandardLabel("kit:night", KIT, FARBE_KIT),
          new StandardLabel("kit:nightrun", KIT, FARBE_KIT),
          new StandardLabel("kit:pruefen", KIT, FARBE_KIT),
          new StandardLabel("kit:nightplan", KIT, FARBE_KIT),
          new StandardLabel("kit:nightreview", KIT, FARBE_KIT),
          new StandardLabel("kit:nightissues", KIT, FARBE_KIT),
          new StandardLabel("lauf:abgebrochen", LAUF, FARBE_LAUF),
          new StandardLabel("lauf:laeuft", LAUF, FARBE_LAUF),
          new StandardLabel("lauf:wartet", LAUF, FARBE_LAUF),
          new StandardLabel("review:offen", REVIEW, FARBE_REVIEW),
          new StandardLabel("review:fertig", REVIEW, FARBE_REVIEW),
          new StandardLabel("ziel:plan", STUFENLEISTE, FARBE_STUFENLEISTE),
          new StandardLabel("ziel:pakete", STUFENLEISTE, FARBE_STUFENLEISTE),
          new StandardLabel("ziel:umsetzung", STUFENLEISTE, FARBE_STUFENLEISTE),
          new StandardLabel("ziel:push-vorbereitet", STUFENLEISTE, FARBE_STUFENLEISTE),
          new StandardLabel("planreview:1", STUFENLEISTE, FARBE_STUFENLEISTE),
          new StandardLabel("planreview:2", STUFENLEISTE, FARBE_STUFENLEISTE));

  private final LabelRepository labels;
  private final BoardService boardService;
  private final PlatformAdminChecker platformAdminChecker;

  public StandardLabelService(
      LabelRepository labels,
      BoardService boardService,
      PlatformAdminChecker platformAdminChecker) {
    this.labels = labels;
    this.boardService = boardService;
    this.platformAdminChecker = platformAdminChecker;
  }

  /** Der Standardsatz — nur für Plattform-Admins. */
  public List<StandardLabel> standardsatz(long actorUserId) {
    requireAdmin(actorUserId);
    return SATZ;
  }

  /**
   * Legt jedes fehlende Label des Standardsatzes auf jedem aktiven Board aller Projekte an.
   *
   * @return Zahl der Boards, der neu angelegten und der übersprungenen Labels
   */
  @Transactional
  public Ergebnis aufAlleBoardsAnlegen(long actorUserId) {
    requireAdmin(actorUserId);
    List<BoardSummary> boards = boardService.listAllActiveBoards();
    int angelegt = 0;
    for (BoardSummary board : boards) {
      for (StandardLabel label : SATZ) {
        if (labels.insertIfAbsent(board.id(), label.name(), label.farbe())) {
          angelegt++;
        }
      }
    }
    return new Ergebnis(boards.size(), angelegt, boards.size() * SATZ.size() - angelegt);
  }

  private void requireAdmin(long actorUserId) {
    if (!platformAdminChecker.isPlatformAdmin(actorUserId)) {
      throw new AdminAccessDeniedException();
    }
  }

  /**
   * Ein Label des Standardsatzes.
   *
   * @param gruppe Kit, Lauf, Review oder Stufenleiste — für die Anzeige nach Gruppen
   * @param farbe CSS-Farbwert, mit dem es neu angelegt wird
   */
  public record StandardLabel(String name, String gruppe, String farbe) {}

  /**
   * Ergebnis eines Anlegens.
   *
   * @param boards Zahl der aktiven Boards, auf denen angelegt wurde
   * @param angelegt Zahl der neu angelegten Labels über alle Boards
   * @param uebersprungen Zahl der Labels, die ein Board schon trug
   */
  public record Ergebnis(int boards, int angelegt, int uebersprungen) {}
}
