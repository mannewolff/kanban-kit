package org.mwolff.manban.card.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suche nach Kartennummern (Issue #1391, Plan #1387 E1): die Karte zu einer projektweiten Nummer
 * und die projektübergreifende Nummernsuche. Einziger Karten-Dienst, der das project-Modul nach den
 * zugänglichen Projekten fragt. Rechte über den {@link PermissionChecker} bzw. die Projektauswahl
 * des {@link ProjectService}.
 */
@Service
public class CardSearchService {

  private final CardRepository cards;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final KartenSicht sicht;

  public CardSearchService(
      CardRepository cards,
      BoardService boardService,
      PermissionChecker permissions,
      ProjectService projects,
      KartenSicht sicht) {
    this.cards = cards;
    this.boardService = boardService;
    this.permissions = permissions;
    this.projects = projects;
    this.sicht = sicht;
  }

  /**
   * Löst eine projektweite Kartennummer zu ihrer {@link CardView} auf. Erfordert
   * Projekt-Mitgliedschaft (Leserecht); Nichtmitglied wie unbekannte oder gelöschte Nummer → 404
   * (kein Existenz-Leak). Basis für klickbare {@code #N}-Verweise (#403).
   */
  @Transactional(readOnly = true)
  public CardView getByNumber(long userId, long projectId, int number) {
    permissions.requireMembership(userId, projectId);
    Card card =
        cards.findByProjectIdAndNumber(projectId, number).orElseThrow(CardNotFoundException::new);
    return sicht.view(userId, card);
  }

  /**
   * Sucht eine projektweite Kartennummer über <strong>alle Projekte, in denen der Benutzer lesen
   * darf</strong>, und liefert je Treffer die Karte samt Ortsangabe (Projekt, Board, Spalte).
   *
   * <p><strong>Warum eine Liste:</strong> Kartennummern sind projektweit eindeutig, nicht global
   * ({@code uq_card_number (project_id, number)}). Dass die Projekte hier faktisch disjunkte
   * Nummernkreise haben (Startnummer-Floor aus V20), ist Konvention und keine Invariante — dieselbe
   * Nummer kann in mehreren Projekten existieren, und dann sind alle Treffer gemeint.
   *
   * <p><strong>Sichtbarkeit:</strong> Gesucht wird ausschließlich in den Projekten des Benutzers.
   * Ein fremdes Projekt macht sich in keiner Weise bemerkbar — kein 403, kein Zähler, kein
   * Unterschied im Antwortverhalten. Die leere Liste ist die Antwort sowohl für „Nummer existiert
   * nirgends" als auch für „Nummer existiert nur in fremden Projekten" (Prinzip aus {@link
   * PermissionChecker}). Ein <b>Plattform-Admin</b> findet dagegen per Definition alles: Die
   * Projektauswahl kommt von {@link ProjectService#listAccessible(long)}, das ihm wie überall sonst
   * (Projektliste, {@code requireMembership}) alle Projekte zeigt. Das ist bewusst das
   * Bestandsverhalten und keine Sonderregel dieser Suche.
   *
   * <p><strong>Was nicht gefunden wird:</strong> Karten im Papierkorb — ihre Nummer bleibt belegt
   * (sie kann wiederhergestellt werden), per Suche sind sie unsichtbar. <b>Archivierte</b> Karten
   * bleiben dagegen auffindbar, ebenso Karten auf einem <b>archivierten Board</b>: Deren Boardname
   * wird über {@link BoardService#requireBoardSummary(long)} aufgelöst, das den Archiv-Filter
   * bewusst nicht anwendet und den Zustand stattdessen mitliefert.
   */
  @Transactional(readOnly = true)
  public List<CardSearchHit> searchByNumber(long userId, int number) {
    Map<Long, String> projectNames =
        projects.listAccessible(userId).stream()
            .collect(
                Collectors.toMap(
                    ProjectService.AccessibleProject::id, ProjectService.AccessibleProject::name));
    if (projectNames.isEmpty()) {
      // Ohne Projekte gibt es nichts zu durchsuchen — und eine leere IN-Menge wäre keine sinnvolle
      // Anfrage an die Datenbank (siehe Zusicherung an CardRepository.findByNumberInProjects).
      return List.of();
    }
    return cards.findByNumberInProjects(number, List.copyOf(projectNames.keySet())).stream()
        .map(c -> hit(userId, c, Objects.requireNonNull(projectNames.get(c.projectId()))))
        .toList();
  }

  /**
   * Baut den Suchtreffer samt Ortsangabe. Board und Spalte werden über die board-Fassade aufgelöst
   * — der Boardname auch dann, wenn das Board archiviert ist.
   */
  private CardSearchHit hit(long userId, Card c, String projectName) {
    Long boardId = c.boardId();
    BoardSummary board = boardService.requireBoardSummary(boardId);
    ColumnView column = boardService.requireColumn(c.columnId(), boardId);
    return new CardSearchHit(
        sicht.view(userId, c),
        c.projectId(),
        projectName,
        boardId,
        board.name(),
        board.archived(),
        column.id(),
        column.name());
  }

  /**
   * Treffer der projektübergreifenden Nummernsuche: die Karte plus die Angabe, wo sie liegt.
   *
   * @param card die gefundene Karte
   * @param projectId Projekt der Karte (immer gesetzt)
   * @param projectName Name des Projekts — das unterscheidende Merkmal, wenn dieselbe Nummer in
   *     mehreren Projekten existiert
   * @param boardId Board der Karte (immer gesetzt)
   * @param boardName Name des Boards (immer gesetzt)
   * @param boardArchived ob das Board archiviert ist (die Karte bleibt auffindbar, das Board ist
   *     über die normale Board-API aber nicht mehr ladbar)
   * @param columnId Spalte der Karte (immer gesetzt)
   * @param columnName Name der Spalte (immer gesetzt)
   */
  public record CardSearchHit(
      CardView card,
      Long projectId,
      String projectName,
      Long boardId,
      String boardName,
      boolean boardArchived,
      Long columnId,
      String columnName) {}
}
