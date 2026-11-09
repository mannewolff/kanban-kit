package org.mwolff.manban.nightrun.application;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.nightrun.domain.NightRun;
import org.mwolff.manban.nightrun.domain.NightRunItem;
import org.mwolff.manban.nightrun.domain.NightRunKind;
import org.mwolff.manban.project.application.ProjectService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lesedienst der Benachrichtigungen (Issue #2074, Plan #2070 E2): die Läufe der Art {@code NIGHT}
 * der letzten 48 Stunden über alle Projekte, samt Paketen, Projekt und Board.
 *
 * <p><b>Ohne Rechteprüfung.</b> Der Job fragt ohne Anfrage und ohne Aufrufer; wem eine Mail gilt,
 * prüft er selbst je Person. Darum geht diese Sicht nie an einen Endpunkt.
 *
 * <p><b>Warum neben {@link MeldbareLaeufe}.</b> Die Übersicht kommt nach jedem Lauf, darum filtert
 * diese Sicht nicht auf Läufe mit Grund. Und der Runner läuft zu jeder Tageszeit: Statt der
 * Nachtgrenze um 12:00 gilt ein rollendes Fenster zur Uhr der Anwendung.
 */
@Service
public class BenachrichtigbareLaeufe {

  private static final Duration FENSTER = Duration.ofDays(2);

  private final NightRunRepository runs;
  private final ProjectService projects;
  private final BoardService boards;
  private final Clock clock;

  public BenachrichtigbareLaeufe(
      NightRunRepository runs, ProjectService projects, BoardService boards, Clock clock) {
    this.runs = runs;
    this.projects = projects;
    this.boards = boards;
    this.clock = clock;
  }

  /**
   * Ein Lauf im Fenster.
   *
   * @param lauf der Lauf
   * @param pakete seine Pakete, in Laufreihenfolge
   * @param projectName Name seines Projekts
   * @param boardName Name des Boards seines Zugangsschlüssels; {@code null} ohne Board
   */
  public record BenachrichtigbarerLauf(
      NightRun lauf, List<NightRunItem> pakete, String projectName, @Nullable String boardName) {}

  /** Die Läufe im Fenster, ältester zuerst; Läufe verschwundener Projekte fallen heraus. */
  @Transactional(readOnly = true)
  public List<BenachrichtigbarerLauf> laeufe() {
    List<NightRun> laeufe =
        runs.findByKindStartedSince(NightRunKind.NIGHT, clock.instant().minus(FENSTER));
    if (laeufe.isEmpty()) {
      return List.of();
    }
    Map<Long, List<NightRunItem>> pakete =
        runs.findItemsByRunIds(laeufe.stream().map(NightRun::requireId).toList()).stream()
            .collect(Collectors.groupingBy(item -> Objects.requireNonNull(item.nightRunId())));
    List<BenachrichtigbarerLauf> benachrichtigbar = new ArrayList<>();
    for (NightRun run : laeufe) {
      projects
          .findName(run.projectId())
          .ifPresent(
              projektName ->
                  benachrichtigbar.add(
                      new BenachrichtigbarerLauf(
                          run,
                          pakete.getOrDefault(run.requireId(), List.of()),
                          projektName,
                          boardName(run))));
    }
    return benachrichtigbar;
  }

  /** Die Pakete der Karte über alle Läufe, auch verdrängte; jüngster Lauf zuerst (Issue #2078). */
  @Transactional(readOnly = true)
  public List<NightRunItem> paketeDerKarte(long projectId, int cardNumber) {
    return runs.findByCard(projectId, cardNumber);
  }

  private @Nullable String boardName(NightRun run) {
    Long boardId = run.boardId();
    return boardId == null ? null : boards.requireBoardSummary(boardId).name();
  }
}
