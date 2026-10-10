package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenSicht.statusName;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.BoardSummary;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardIngestService.BoardItemView;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Kartenart;
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
  private final CardBestandRepository bestand;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final ProjectService projects;
  private final KartenSicht sicht;

  public CardSearchService(
      CardRepository cards,
      CardBestandRepository bestand,
      BoardService boardService,
      PermissionChecker permissions,
      ProjectService projects,
      KartenSicht sicht) {
    this.cards = cards;
    this.bestand = bestand;
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
   * Die Karte mit dieser projektweiten Nummer auf genau diesem Board, <strong>archivierte
   * eingeschlossen</strong> — der Einzelabruf der Kanban-Strecke (Plan #1574, A2, A3).
   *
   * <p>Rechte wie {@link CardIngestService#listBoardItems}: Projekt-Mitgliedschaft (Leserecht), vor
   * jedem Lookup. Betrachtet werden nur Karten mit {@code board_id} = {@code boardId} und {@code
   * deleted_at IS NULL}. Eine unbekannte Nummer, eine Nummer auf einem anderen Board und eine im
   * Papierkorb enden gleich mit {@link CardNotFoundException} — kein Existenz-Leak über die
   * Board-Grenze.
   */
  @Transactional(readOnly = true)
  public BoardCardView findOnBoardByNumber(long userId, long boardId, int number) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.requireMembership(userId, projectId);
    Card c =
        cards
            .findByProjectIdAndNumber(projectId, number)
            // Wertvergleich der Board-IDs (Long), wie in CardIngestService.requireOnBoard.
            .filter(k -> Long.valueOf(boardId).equals(k.boardId()))
            .orElseThrow(CardNotFoundException::new);
    String status = statusName(c);
    String spalte = boardService.requireColumn(c.columnId(), boardId).name();
    return new BoardCardView(
        new BoardItemView(
            c.requireId(),
            c.number(),
            c.title(),
            c.description(),
            c.columnId(),
            c.positionInColumn(),
            c.type() == CardType.EPIC,
            c.externalKey(),
            sicht.herkunftsnummer(c),
            status),
        Kartenart.von(c.type(), c.title()).name(),
        CardIngestService.kanbanSchluessel(status, spalte),
        c.archived(),
        c.createdAt(),
        c.movedToDoneAt());
  }

  /**
   * Durchsucht die Karten genau dieses Boards, <strong>archivierte eingeschlossen</strong> — die
   * Suche der Kanban-Strecke (Plan #1574, Kriterien 2 und 4): nach Text, Art, Vorhaben und Zeitraum
   * des Anlagedatums, nach Nummer absteigend, in Teilen (E6). Die Rechnung liegt in {@link
   * KartenBestandSuche}. Titel und Text gleicht die Datenbank nur bei gesetztem Suchbegriff ab
   * (E3); die Kommentar-Fundstellen bringt der Aufrufer mit, weil {@code card} nicht von {@code
   * comment} abhängt (A5).
   *
   * <p>Die Eingaben werden vor jedem Lookup geprüft: Eine unbekannte Art endet mit {@link
   * UnknownCardKindException} (E2), ein Beginn, der nicht vor dem Ende liegt, mit {@link
   * InvalidPeriodBoundsException}; eine einzelne Grenze ist erlaubt (E7). Rechte und Reichweite wie
   * {@link #findOnBoardByNumber}: Projekt-Mitgliedschaft, nur {@code board_id} = {@code boardId},
   * Papierkorb unsichtbar (A3).
   *
   * @param kommentarFunde erster passender Kommentar je Karten-ID; ohne Textsuche leer
   */
  @Transactional(readOnly = true)
  public SuchSeite searchOnBoard(
      long userId, long boardId, Suchanfrage anfrage, Map<Long, KommentarFund> kommentarFunde) {
    KartenBestandSuche.Anfrage geprueft = KartenBestandSuche.pruefe(anfrage);
    long projectId = boardService.requireProjectId(boardId);
    permissions.requireMembership(userId, projectId);
    Map<Long, String> spalten =
        boardService.listColumns(boardId).stream()
            .collect(Collectors.toMap(ColumnView::id, ColumnView::name));
    String q = geprueft.q();
    Map<Long, String> textFundstellen =
        q == null ? Map.of() : bestand.textFundstellenAufBoard(boardId, q);
    return KartenBestandSuche.alsSicht(
        KartenBestandSuche.suche(
            cards.findByBoardId(boardId), geprueft, textFundstellen, kommentarFunde),
        spalten);
  }

  /**
   * Die Herkunftskette der Karte mit dieser Nummer auf genau diesem Board — Vorfahren bis zur
   * Wurzel, Nachfahren bis zu den Blättern, <strong>archivierte eingeschlossen</strong> (Plan
   * #1574, Kriterium 3, E11–E13). Die Rechnung liegt in {@link HerkunftsKette}; Vorfahren außerhalb
   * der Board-Sicht lädt sie einzeln über {@code findByIds} nach.
   *
   * <p>Rechte und Reichweite wie {@link #findOnBoardByNumber}: Projekt-Mitgliedschaft vor jedem
   * Lookup; eine Nummer im Papierkorb, auf einem anderen Board oder eine unbekannte enden gleich
   * mit {@link CardNotFoundException} (A3).
   */
  @Transactional(readOnly = true)
  public Kette lineageOnBoard(long userId, long boardId, int number) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.requireMembership(userId, projectId);
    List<Card> boardKarten = cards.findByBoardId(boardId);
    Card karte =
        boardKarten.stream()
            .filter(c -> c.number() == number)
            .findFirst()
            .orElseThrow(CardNotFoundException::new);
    Map<Long, String> spalten =
        boardService.listColumns(boardId).stream()
            .collect(Collectors.toMap(ColumnView::id, ColumnView::name));
    return HerkunftsKette.berechne(
        karte,
        boardKarten,
        id -> cards.findByIds(List.of(id)).stream().findFirst(),
        bestand.verloreneHerkunftsnummern(boardId),
        spalten);
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
  @Schema(description = "Ein Treffer der Nummernsuche: die Karte und wo sie liegt.")
  public record CardSearchHit(
      @Schema(description = "Die gefundene Karte.") CardView card,
      @Schema(description = "Interne ID des Projekts.", example = "1") Long projectId,
      @Schema(description = "Name des Projekts.", example = "kanban-kit") String projectName,
      @Schema(description = "Interne ID des Boards.", example = "3") Long boardId,
      @Schema(description = "Name des Boards.", example = "Entwicklung") String boardName,
      @Schema(
              description =
                  "Ob das Board archiviert ist; die Karte bleibt auffindbar, das Board über die"
                      + " Board-Aufrufe aber nicht.")
          boolean boardArchived,
      @Schema(description = "Interne ID der Spalte.", example = "17") Long columnId,
      @Schema(description = "Name der Spalte.", example = "In Arbeit") String columnName) {}

  /**
   * Eine Karte des Boards für die Kanban-Strecke (Plan #1574, A4): die schlanke Projektion, die
   * {@link CardIngestService#listBoardItems} für aktive Karten liefert, plus das, was das Archiv
   * ausmacht. Art und Spaltenschlüssel gehen als Text, weil {@code card.domain} modulintern bleibt.
   *
   * @param item Karte in derselben Form wie in der Board-Übersicht
   * @param kind Konstantenname der {@link Kartenart}
   * @param column Kanban-Schlüssel nach {@link CardIngestService#kanbanSchluessel}
   * @param archived ob die Karte archiviert ist
   * @param createdAt Anlagezeitpunkt
   * @param movedToDoneAt Zeitpunkt des Zugs nach Done, sonst {@code null}
   */
  public record BoardCardView(
      BoardItemView item,
      String kind,
      String column,
      boolean archived,
      Instant createdAt,
      @Nullable Instant movedToDoneAt) {}

  /**
   * Die Filter der Suche auf dem Board (Plan #1574, E2, E3, E6, E7). Arten gehen als
   * Konstantennamen der Kartenart, weil {@code card.domain} modulintern bleibt.
   *
   * @param q Suchbegriff für Titel, Text und Kommentare; wird getrimmt, leer oder {@code null}
   *     heißt kein Textfilter
   * @param arten verlangte Arten, ODER-verknüpft; leer heißt jede Art
   * @param epic Nummer eines Vorhabens des Boards, sonst {@code null}
   * @param createdFrom Beginn des Anlagezeitraums, eingeschlossen, sonst {@code null}
   * @param createdTo Ende des Anlagezeitraums, ausgeschlossen, sonst {@code null}
   * @param cursor Nummer des letzten Treffers des vorigen Teils, sonst {@code null}
   * @param limit höchste Trefferzahl dieses Teils, mindestens 1
   */
  public record Suchanfrage(
      @Nullable String q,
      Set<String> arten,
      @Nullable Integer epic,
      @Nullable Instant createdFrom,
      @Nullable Instant createdTo,
      @Nullable Integer cursor,
      int limit) {}

  /**
   * Eine Fundstelle in einem Kommentar, die den Auszug eines Treffers bestimmt (Plan #1574, A5).
   *
   * @param commentId der Kommentar
   * @param body sein Text
   */
  public record KommentarFund(long commentId, String body) {}

  /**
   * Ein Treffer der Suche auf dem Board (Plan #1574, E4, E5, E9).
   *
   * @param kind Konstantenname der Kartenart
   * @param column Kanban-Schlüssel nach {@link CardIngestService#kanbanSchluessel}
   * @param auszug höchstens 200 Zeichen, Zeilenumbrüche als Leerzeichen
   * @param fundstelle {@code TITLE}, {@code BODY} oder {@code COMMENT}
   * @param kommentarId der Kommentar bei {@code COMMENT}, sonst {@code null}
   */
  public record SuchTreffer(
      long id,
      int number,
      String title,
      String kind,
      String column,
      boolean archived,
      Instant createdAt,
      @Nullable Instant movedToDoneAt,
      String auszug,
      String fundstelle,
      @Nullable Long kommentarId) {}

  /**
   * Ein Teil des Suchergebnisses (E6).
   *
   * @param naechsterCursor Nummer des letzten Treffers, wenn danach noch Treffer folgen, sonst
   *     {@code null}
   */
  public record SuchSeite(List<SuchTreffer> treffer, @Nullable Integer naechsterCursor) {}

  /**
   * Ein Glied der Herkunftskette (Plan #1574, E10–E13). Eine Lücke steht für eine Karte, die im
   * Papierkorb, auf einem anderen Board oder endgültig gelöscht ist: Dann sind nur {@code number}
   * und {@code depth} gesetzt.
   *
   * @param kind Konstantenname der Kartenart, bei einer Lücke {@code null}
   * @param column Kanban-Schlüssel nach {@link CardIngestService#kanbanSchluessel}, bei einer Lücke
   *     {@code null}
   * @param depth Abstand zur Karte: 0 für die Karte selbst, 1 für Vorfahr oder Nachfahr erster
   *     Stufe
   */
  public record KettenGlied(
      int number,
      @Nullable String title,
      @Nullable String kind,
      @Nullable String column,
      boolean archived,
      int depth,
      boolean gap) {}

  /**
   * Die Herkunftskette einer Karte.
   *
   * @param vorfahren von der Karte aufwärts bis zur Wurzel
   * @param nachfahren in Präorder, Geschwister nach Nummer
   */
  public record Kette(
      KettenGlied karte, List<KettenGlied> vorfahren, List<KettenGlied> nachfahren) {}
}
