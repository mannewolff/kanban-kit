package org.mwolff.manban.anbieter.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardIngestService;
import org.mwolff.manban.card.application.CardIngestService.BoardItemView;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.comment.application.RueckfrageService;
import org.mwolff.manban.comment.application.RueckfrageService.RueckfrageView;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.RueckfrageZustand;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.project.application.BoardQuelle;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Was im Projekt auf den Kunden wartet (Issue #2216, Plan #2206 E14, Fachplan #2202 Kriterium 3):
 * Karten mit einer offenen Rückfrage der KI und Karten, deren Freigabe aussteht.
 *
 * <p>Eine Freigabe steht aus, wenn an der Karte eine Freigabe-Rückfrage (mit Stand zur Freigabe)
 * offen ist oder die Karte ein Label der Sache {@link Sache#FREIGABE} trägt. Das Label zählt nicht,
 * sobald an der Karte eine Freigabe-Rückfrage steht, die nicht mehr offen ist — beantwortet oder
 * überholt: Dann gibt es für den Kunden nichts zu klicken, bis die KI eine neue stellt.
 *
 * <p>Die Rückfragen wertet allein {@link RueckfrageService} aus, damit Karte, App und dieser
 * Abschnitt denselben Stand sehen. Karten, die der Aufrufer nicht sieht, fehlen ({@link
 * CardIngestService#listBoardItems}); archivierte Karten ebenso. Ein Projekt mit fremdem Board
 * liefert nichts (E15).
 */
@Service
public class WartetAufDichService {

  private final PermissionChecker permissions;
  private final BoardQuelle boardQuelle;
  private final BoardService boards;
  private final CardIngestService cards;
  private final LabelService labels;
  private final RueckfrageService rueckfragen;

  public WartetAufDichService(
      PermissionChecker permissions,
      BoardQuelle boardQuelle,
      BoardService boards,
      CardIngestService cards,
      LabelService labels,
      RueckfrageService rueckfragen) {
    this.permissions = permissions;
    this.boardQuelle = boardQuelle;
    this.boards = boards;
    this.cards = cards;
    this.labels = labels;
    this.rueckfragen = rueckfragen;
  }

  /**
   * Die wartenden Karten des Projekts, Boards und Karten in der Reihenfolge des Boards.
   *
   * @throws org.mwolff.manban.project.application.ProjectNotFoundException wenn der Aufrufer kein
   *     Mitglied ist (404)
   */
  @Transactional(readOnly = true)
  public List<WartendeKarte> liste(long userId, long projectId) {
    permissions.requireMembership(userId, projectId);
    if (boardQuelle.istFremd(projectId)) {
      return List.of();
    }
    List<Kandidat> kandidaten = new ArrayList<>();
    for (long boardId : boards.listActiveBoardIds(projectId)) {
      List<BoardItemView> karten = cards.listBoardItems(userId, boardId);
      Map<Long, List<String>> namen =
          labels.namesByCard(boardId, karten.stream().map(BoardItemView::id).toList());
      karten.forEach(
          k -> kandidaten.add(new Kandidat(boardId, k, namen.getOrDefault(k.id(), List.of()))));
    }
    Map<Long, List<RueckfrageView>> staende =
        rueckfragen.staendeJeKarte(
            userId, projectId, kandidaten.stream().map(k -> k.karte().id()).toList());
    List<WartendeKarte> wartend = new ArrayList<>();
    for (Kandidat k : kandidaten) {
      grund(k.labels(), staende.getOrDefault(k.karte().id(), List.of()))
          .ifPresent(
              grund ->
                  wartend.add(
                      new WartendeKarte(
                          k.karte().id(),
                          k.boardId(),
                          k.karte().number(),
                          k.karte().title(),
                          grund)));
    }
    return wartend;
  }

  /** Warum die Karte auf den Kunden wartet; leer, wenn sie es nicht tut. */
  static Optional<Grund> grund(List<String> labelNamen, List<RueckfrageView> rueckfragen) {
    List<RueckfrageView> offen =
        rueckfragen.stream().filter(r -> r.zustand() == RueckfrageZustand.OFFEN).toList();
    if (offen.stream().anyMatch(r -> !istFreigabe(r))) {
      return Optional.of(Grund.RUECKFRAGE);
    }
    if (!offen.isEmpty()) {
      return Optional.of(Grund.FREIGABE);
    }
    boolean freigabeLabel =
        labelNamen.stream()
            .anyMatch(n -> KitNamen.STANDARD.sache(n).filter(Sache.FREIGABE::equals).isPresent());
    if (freigabeLabel && rueckfragen.stream().noneMatch(WartetAufDichService::istFreigabe)) {
      return Optional.of(Grund.FREIGABE);
    }
    return Optional.empty();
  }

  private static boolean istFreigabe(RueckfrageView r) {
    return r.commit() != null;
  }

  private record Kandidat(long boardId, BoardItemView karte, List<String> labels) {}

  /** Warum eine Karte auf den Kunden wartet. */
  public enum Grund {
    /** Eine Rückfrage der KI ist offen. */
    RUECKFRAGE,
    /** Eine Freigabe des Kunden steht aus. */
    FREIGABE
  }

  /** Eine Karte, die auf den Kunden wartet. */
  @Schema(description = "Eine Karte, an der der Kunde etwas tun muss.")
  public record WartendeKarte(
      @Schema(description = "Interne ID der Karte.", example = "812") long cardId,
      @Schema(description = "Interne ID des Boards.", example = "3") long boardId,
      @Schema(description = "Projektweite Nummer der Karte.", example = "1404") int number,
      @Schema(description = "Titel der Karte.", example = "Export als CSV") String title,
      @Schema(
              description =
                  "RUECKFRAGE (eine Rückfrage der KI ist offen) oder FREIGABE (eine Freigabe"
                      + " steht aus).",
              example = "RUECKFRAGE")
          Grund grund) {}
}
