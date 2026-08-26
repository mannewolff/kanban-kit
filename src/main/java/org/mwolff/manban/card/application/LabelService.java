package org.mwolff.manban.card.application;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.Label;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltung der board-scoped Labels. Anlegen/Bearbeiten/Löschen ist ein Board-Recht ({@link
 * Permission#BOARD_UPDATE}); Auflisten steht jedem Mitglied offen.
 *
 * <p>Das <em>Zuordnen</em> eines vorhandenen Labels an eine Karte ist dagegen Kartenarbeit und
 * verlangt {@link Permission#TICKET_UPDATE} (siehe {@link #addToCard}/{@link #removeFromCard}) —
 * dasselbe Recht wie {@code CardService.setLabels}.
 */
@Service
public class LabelService {

  private final LabelRepository labels;
  private final CardLabelRepository cardLabels;
  private final CardRepository cards;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final ApplicationEventPublisher events;

  public LabelService(
      LabelRepository labels,
      CardLabelRepository cardLabels,
      CardRepository cards,
      BoardService boardService,
      PermissionChecker permissions,
      ApplicationEventPublisher events) {
    this.labels = labels;
    this.cardLabels = cardLabels;
    this.cards = cards;
    this.boardService = boardService;
    this.permissions = permissions;
    this.events = events;
  }

  /**
   * Baut je Karte die Liste der Label-<em>Namen</em> aus genau zwei Batch-Abfragen auf — {@link
   * LabelRepository#findByBoardId} (Namen) und {@link CardLabelRepository#findByCardIds}
   * (Zuordnung) — unabhängig von der Kartenzahl (kein N+1). Die Reihenfolge je Karte folgt der
   * Board-Definitionsreihenfolge, nicht der Zuordnungsreihenfolge; jede übergebene Karten-ID erhält
   * einen Eintrag (leere Liste, wenn ihr kein Label zugeordnet ist).
   *
   * <p>Ohne eigene Rechteprüfung: die Karten-IDs stammen beim einzigen Aufrufer aus einer bereits
   * rechtegeprüften Board-Abfrage ({@link CardService#listBoardItems}).
   *
   * <p>Auch die Board-Zugehörigkeit der {@code cardIds} wird bewusst nicht geprüft (#472): Die
   * Namen kommen ausschließlich aus {@code labels.findByBoardId(boardId)}, das Ergebnis ist damit
   * unabhängig von der Herkunft der IDs auf die Labels <em>dieses</em> Boards begrenzt. Eine
   * board-fremde ID liefert höchstens eine leere Liste, nie fremde Namen. Wer die Methode für einen
   * weiteren Aufrufer öffnet, prüft diese Zusicherung erneut.
   */
  @Transactional(readOnly = true)
  public Map<Long, List<String>> namesByCard(long boardId, Collection<Long> cardIds) {
    List<Label> boardLabels = labels.findByBoardId(boardId);
    Map<Long, List<Long>> labelIdsByCard = cardLabels.findByCardIds(cardIds);
    Map<Long, List<String>> result = new LinkedHashMap<>();
    for (Long cardId : cardIds) {
      Set<Long> assigned = new HashSet<>(labelIdsByCard.getOrDefault(cardId, List.of()));
      result.put(
          cardId,
          boardLabels.stream()
              .filter(l -> assigned.contains(l.requireId()))
              .map(Label::name)
              .toList());
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<Label> list(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    return labels.findByBoardId(boardId);
  }

  @Transactional
  public Label create(long userId, long boardId, String name, String color) {
    permissions.require(userId, boardService.requireProjectId(boardId), Permission.BOARD_UPDATE);
    String trimmed = requireName(name);
    if (labels.existsByBoardIdAndName(boardId, trimmed)) {
      throw new InvalidLabelException("Label existiert bereits: " + trimmed);
    }
    return labels.save(new Label(null, boardId, trimmed, color));
  }

  @Transactional
  public Label update(long userId, long labelId, String name, String color) {
    Label label = labels.findById(labelId).orElseThrow(LabelNotFoundException::new);
    permissions.require(
        userId, boardService.requireProjectId(label.boardId()), Permission.BOARD_UPDATE);
    String trimmed = requireName(name);
    if (!trimmed.equals(label.name()) && labels.existsByBoardIdAndName(label.boardId(), trimmed)) {
      throw new InvalidLabelException("Label existiert bereits: " + trimmed);
    }
    return labels.save(label.withContent(trimmed, color));
  }

  @Transactional
  public void delete(long userId, long labelId) {
    Label label = labels.findById(labelId).orElseThrow(LabelNotFoundException::new);
    permissions.require(
        userId, boardService.requireProjectId(label.boardId()), Permission.BOARD_UPDATE);
    labels.deleteById(labelId);
  }

  /**
   * Ordnet der Karte genau <em>ein</em> über seinen Namen aufgelöstes Label zu und lässt alle
   * übrigen unangetastet (#574) — der Schreibweg, über den das claude-workflow-kit sein
   * Routing-Label {@code kit:nightrun} setzt.
   *
   * <p>Bewusst kein Lesen-Ändern-Zurückschreiben über {@code CardService.setLabels}: Ein Nachtlauf,
   * der parallel zu einer Bearbeitung am Board liefe, löschte damit fremde Labels stillschweigend.
   *
   * <p>Nach außen idempotent — ein bereits zugeordnetes Label erneut hinzuzufügen ist Erfolg, damit
   * ein Nachtlauf nach einem Teilfehler wiederholbar bleibt. Das Board-Ereignis entsteht nur bei
   * einer tatsächlichen Änderung; sonst meldete ein wiederholter Lauf Änderungen, die es nicht gab.
   *
   * @throws CardNotFoundException wenn die Karte fehlt
   * @throws InvalidDependencyException wenn das Item ein Vorhaben ist (400)
   * @throws LabelNotFoundException wenn das Board kein Label dieses Namens definiert (404) — ein
   *     unbekannter Name wird abgelehnt und <em>nicht</em> angelegt, sonst erzeugte ein Tippfehler
   *     im Nachtlauf dauerhaft Label-Müll
   */
  @Transactional
  public void addToCard(long userId, long cardId, String name) {
    Card card = requireLabelableCard(userId, cardId);
    long boardId = card.requireBoardId();
    if (cardLabels.addLabel(cardId, requireLabelId(boardId, name))) {
      events.publishEvent(new CardBoardActivityEvent(boardId, ActivityType.UPDATED, cardId));
    }
  }

  /**
   * Gegenstück zu {@link #addToCard}: entfernt genau die eine Zuordnung und lässt alle übrigen
   * unangetastet. Ein nicht zugeordnetes Label zu entfernen ist ebenfalls Erfolg.
   */
  @Transactional
  public void removeFromCard(long userId, long cardId, String name) {
    Card card = requireLabelableCard(userId, cardId);
    long boardId = card.requireBoardId();
    if (cardLabels.removeLabel(cardId, requireLabelId(boardId, name))) {
      events.publishEvent(new CardBoardActivityEvent(boardId, ActivityType.UPDATED, cardId));
    }
  }

  /**
   * Karte, an der Labels zulässig sind, samt Rechteprüfung. Projekt-basiertes {@link
   * Permission#TICKET_UPDATE} wie in {@code CardService.setLabels} — ausdrücklich nicht {@link
   * Permission#BOARD_UPDATE}: Jenes schützt die Label-<em>Definitionen</em> des Boards, das
   * Zuordnen an eine Karte steht jedem Mitglied ab MEMBER offen.
   */
  private Card requireLabelableCard(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Labels");
    }
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);
    return card;
  }

  /**
   * Löst einen Labelnamen am Board der Karte auf. Labelnamen sind nur <em>boardweit</em> eindeutig
   * — dieselbe Bezeichnung kann auf einem anderen Board eine andere Label-ID meinen. Der Vergleich
   * ist nach dem Trimmen case-sensitiv, genau wie die Eindeutigkeitsprüfung in {@link #create}.
   */
  private long requireLabelId(long boardId, String name) {
    String trimmed = requireName(name);
    return labels.findByBoardId(boardId).stream()
        .filter(l -> trimmed.equals(l.name()))
        .map(Label::requireId)
        .findFirst()
        .orElseThrow(LabelNotFoundException::new);
  }

  private static String requireName(String name) {
    String trimmed = name.trim();
    if (trimmed.isEmpty()) {
      throw new InvalidLabelException("Labelname darf nicht leer sein");
    }
    return trimmed;
  }
}
