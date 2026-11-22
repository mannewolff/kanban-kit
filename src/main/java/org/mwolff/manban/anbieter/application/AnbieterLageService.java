package org.mwolff.manban.anbieter.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mwolff.manban.accesstoken.application.ZugangsNutzung;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.LabelService;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ob das Rechenzentrum die Karten der Boards eines Projekts liest (Issue #2216, Plan #2206 E12,
 * Fachplan #2202 Kriterium 10).
 *
 * <p>Ein Board gilt als „gerade nicht erreichbar“, wenn das jüngste Lesen durch das Rechenzentrum
 * ({@link ZugangsNutzung}) länger als {@link AnbieterProperties#abholfrist()} zurückliegt. Ohne
 * einen solchen Zeitpunkt — vor dem ersten Lesen — gibt es keinen Hinweis. Den Hinweis sieht nur
 * die Rolle Kunde, und nur an Boards, an denen ein Label der Sache {@link Sache#ANBIETER}
 * existiert: Dort arbeitet ein Anbieter. Jedes andere Mitglied bekommt eine Lage ohne Hinweis.
 */
@Service
public class AnbieterLageService {

  private final PermissionChecker permissions;
  private final BoardService boards;
  private final LabelService labels;
  private final ZugangsNutzung nutzung;
  private final Clock clock;
  private final AnbieterProperties properties;

  public AnbieterLageService(
      PermissionChecker permissions,
      BoardService boards,
      LabelService labels,
      ZugangsNutzung nutzung,
      Clock clock,
      AnbieterProperties properties) {
    this.permissions = permissions;
    this.boards = boards;
    this.labels = labels;
    this.nutzung = nutzung;
    this.clock = clock;
    this.properties = properties;
  }

  /**
   * Die Lage des Projekts für den Aufrufer.
   *
   * @throws org.mwolff.manban.project.application.ProjectNotFoundException wenn der Aufrufer kein
   *     Mitglied ist (404)
   */
  @Transactional(readOnly = true)
  public AnbieterLage lage(long userId, long projectId) {
    permissions.requireMembership(userId, projectId);
    if (!permissions.istEchterKunde(userId, projectId)) {
      return new AnbieterLage(false, List.of());
    }
    List<Long> mitAnbieter =
        boards.listActiveBoardIds(projectId).stream()
            .filter(board -> traegtAnbieterLabel(userId, board))
            .toList();
    Map<Long, Instant> gelesen = nutzung.juengsteNutzungDesRechenzentrums(mitAnbieter);
    Instant grenze = clock.instant().minus(properties.abholfrist());
    List<BoardLage> lagen =
        mitAnbieter.stream()
            .flatMap(
                board ->
                    Optional.ofNullable(gelesen.get(board))
                        .map(zuletzt -> new BoardLage(board, zuletzt.isBefore(grenze), zuletzt))
                        .stream())
            .toList();
    return new AnbieterLage(lagen.stream().anyMatch(BoardLage::nichtErreichbar), lagen);
  }

  private boolean traegtAnbieterLabel(long userId, long boardId) {
    return labels.listNamesAndColors(userId, boardId).stream()
        .anyMatch(
            l -> KitNamen.STANDARD.sache(l.name()).filter(Sache.ANBIETER::equals).isPresent());
  }

  /** Die Lage eines Projekts. */
  @Schema(description = "Ob das Rechenzentrum des Anbieters die Boards des Projekts liest.")
  public record AnbieterLage(
      @Schema(
              description =
                  "Ob an mindestens einem Board „gerade nicht erreichbar“ steht; nur für die"
                      + " Rolle Kunde je wahr.",
              example = "false")
          boolean nichtErreichbar,
      @Schema(
              description =
                  "Je Board mit Label der Sache Anbieter, das das Rechenzentrum schon gelesen hat,"
                      + " seine Lage; für andere Rollen als Kunde leer.")
          List<BoardLage> boards) {}

  /** Die Lage eines Boards. */
  @Schema(description = "Ob das Rechenzentrum die Karten eines Boards liest.")
  public record BoardLage(
      @Schema(description = "Interne ID des Boards.", example = "3") long boardId,
      @Schema(
              description = "Ob das letzte Lesen länger als die Abholfrist zurückliegt.",
              example = "false")
          boolean nichtErreichbar,
      @Schema(
              description = "Wann das Rechenzentrum die Karten zuletzt gelesen hat.",
              example = "2026-11-22T10:00:00Z")
          Instant zuletztGelesen) {}
}
