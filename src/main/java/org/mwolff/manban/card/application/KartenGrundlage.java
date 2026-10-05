package org.mwolff.manban.card.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Die Helfer, die mehrere Karten-Themen teilen (Issue #1389, Plan #1387 E2): Karte laden und Rechte
 * prüfen, Aktivität schreiben und Ereignis senden, eine Karte anlegen, Status und Done-Zeitpunkt
 * aus der Spalte ableiten. Die Sicht einer Karte baut {@link KartenSicht} — getrennt entlang der
 * Helfer-Gruppen, damit keiner der beiden Bausteine eine Kopplungsausnahme braucht.
 *
 * <p>Modulintern wie {@link KartenZuordnung}: Die Klasse steht nicht auf der Fassaden-Whitelist der
 * ArchUnit-Regel {@code CARD_APPLICATION_IST_AUF_FASSADE_BEGRENZT}. Was nur ein Thema braucht,
 * bleibt bei dessen Dienst.
 *
 * <p>Ohne {@code @Transactional} (E3): Jede Methode läuft in der Transaktion des aufrufenden
 * Use-Case-Verfahrens; Rechteprüfungen bleiben, wo sie vorher standen.
 */
@Service
public final class KartenGrundlage {

  private final CardRepository cards;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final CardColumnTransitionRepository transitions;
  private final KartenZuordnung zuordnung;
  private final CardActivityRepository activity;
  private final ActorContext actor;
  private final ApplicationEventPublisher events;
  private final Clock clock;

  public KartenGrundlage(
      CardRepository cards,
      KartenAbhaengigkeiten abhaengigkeiten,
      BoardService boardService,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      ActorContext actor,
      ApplicationEventPublisher events,
      Clock clock) {
    this.cards = cards;
    this.abhaengigkeiten = abhaengigkeiten;
    this.boardService = boardService;
    this.permissions = permissions;
    this.transitions = transitions;
    this.zuordnung = zuordnung;
    this.activity = activity;
    this.actor = actor;
    this.events = events;
    this.clock = clock;
  }

  /**
   * Publiziert ein {@link CardBoardActivityEvent} für Live-Board-Updates. Die Composition-Root
   * übersetzt es in den SSE-Vertrag des board-Moduls, der Board-Event-Listener reicht es
   * transaktionsgebunden (nach Commit) an die SSE-Registry weiter — bei Rollback entsteht kein
   * Event. Wird am erfolgreichen Ende jeder board-relevanten Karten-Mutation aufgerufen.
   */
  public void publishChanged(long boardId, ActivityType type, @Nullable Long cardId) {
    events.publishEvent(new CardBoardActivityEvent(boardId, type, cardId));
  }

  /**
   * Schreibt einen Eintrag in den Aktivitätsverlauf der Karte, gestempelt mit der Herkunft der
   * laufenden Anfrage ({@link ActorContext}).
   */
  public void aktivitaet(
      long cardId, long userId, CardActivityType type, String detail, Instant zeitpunkt) {
    activity.add(cardId, userId, type, detail, zeitpunkt, actor.current());
  }

  /**
   * Kern-Logik des Anlegens einer Board-Karte, ohne eigene {@code @Transactional}: Die öffentlichen
   * Anlege-Wege rufen sie je in ihrer eigenen Transaktion auf und bauen aus der gespeicherten Karte
   * ihre Sicht ({@link KartenSicht#view(long, Card)}). {@code dueDate}, {@code assigneeIds} und
   * {@code labelIds} werden — sofern gesetzt — atomar mit der Anlage übernommen; {@code null}/leer
   * bedeutet „nicht gesetzt".
   *
   * @param givenNumber vorgegebene Nummer (#565); ersetzt die Vergabe, nicht die Sperre — die hat
   *     der Aufrufer bereits genommen und die Nummer unter ihr geprüft
   */
  public Card anlegen(
      long userId,
      long boardId,
      long columnId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable Long parentId,
      @Nullable Instant dueDate,
      @Nullable List<Long> assigneeIds,
      @Nullable List<Long> labelIds,
      @Nullable String externalKey,
      @Nullable Integer givenNumber,
      @Nullable Integer derivedFrom) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    Long herkunft = DerivedFrom.resolve(cards, projectId, derivedFrom, null);
    ColumnView column = boardService.requireColumn(columnId, boardId);
    Long effectiveParent =
        parentId == null ? null : requireEpicInBoard(parentId, boardId).requireId();

    int number = givenNumber != null ? givenNumber : cards.allocateCardNumber(projectId);
    int position = cards.allocateActivePosition(columnId);
    Instant now = clock.instant();
    Card neu =
        new Card(
            null,
            boardId,
            columnId,
            number,
            title.trim(),
            normalize(description),
            position,
            false,
            null,
            userId,
            now,
            now,
            CardType.CARD,
            effectiveParent,
            null,
            dueDate,
            projectId,
            externalKey,
            herkunft,
            null,
            null);
    // Status und Done-Zeitstempel leitet inSpalte aus der Zielspalte ab (Plan #1294, E5/E7).
    Card saved = cards.save(inSpalte(neu, column.name(), now));

    transitions.open(saved.requireId(), columnId, column.name(), now);
    aktivitaet(saved.requireId(), userId, CardActivityType.CREATED, "Karte angelegt", now);
    abhaengigkeiten.ersetze(saved, dependsOn);
    if (assigneeIds != null && !assigneeIds.isEmpty()) {
      zuordnung.ersetzeZustaendige(saved.requireId(), projectId, assigneeIds);
    }
    if (labelIds != null && !labelIds.isEmpty()) {
      zuordnung.ersetzeLabels(saved.requireId(), boardId, labelIds);
    }
    publishChanged(boardId, ActivityType.CREATED, saved.requireId());
    return saved;
  }

  /**
   * Lädt die Karte und verlangt das je nach Kartentyp (Ticket/Vorhaben) passende Recht. Die Rechte
   * sind projekt-basiert und werden über {@code card.projectId()} (immer gesetzt, V18) geprüft —
   * nicht über das Board; für board-gebundene Karten ist die Prüfung identisch (die Projekt-ID
   * stimmt mit dem Board-Projekt überein).
   */
  public Card requireCardOp(
      long userId, long cardId, Permission ticketPermission, Permission epicPermission) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(
        userId, card.projectId(), card.type() == CardType.EPIC ? epicPermission : ticketPermission);
    return card;
  }

  /** Das Vorhaben zur ID, sofern es auf diesem Board liegt. */
  public Card requireEpicInBoard(long epicId, long boardId) {
    Card epic = cards.findById(epicId).orElseThrow(CardNotFoundException::new);
    if (epic.type() != CardType.EPIC || epic.boardId() != boardId) {
      throw new InvalidDependencyException("Kein Epic dieses Boards: " + epicId);
    }
    return epic;
  }

  /**
   * Die Karte, wie sie in einer Spalte dieses Namens ankommt (Plan #1294): Status nach {@link
   * #statusIn}, Done-Zeitstempel nach {@link #doneStempel}. Gemeinsamer Weg von Anlegen und
   * Übertragen, damit beide dieselbe Regel sprechen.
   */
  public static Card inSpalte(Card card, @Nullable String spaltenname, Instant now) {
    Card mitStatus = card.withStatus(statusIn(card, spaltenname));
    return mitStatus.withMovedToDoneAt(doneStempel(mitStatus, spaltenname, now));
  }

  /**
   * Status einer Karte, die in einer Spalte dieses Namens ankommt (Plan #1294, E2/E5): Eine
   * Prozessspalte gibt ihren Status vor, eine eigene Spalte lässt den bisherigen stehen — und wer
   * noch keinen hat, bekommt {@code BACKLOG}. Vorhaben und Dokumentarten tragen keinen.
   */
  public static @Nullable CardStatus statusIn(Card card, @Nullable String spaltenname) {
    if (!Arbeitspaket.istArbeitspaket(card.type(), card.title())) {
      return null;
    }
    CardStatus bisher = card.status();
    return Arbeitspaket.statusVonSpalte(spaltenname)
        .orElse(bisher != null ? bisher : CardStatus.BACKLOG);
  }

  /**
   * Done-Zeitpunkt nach dem effektiven Maßstab (Plan #1294, E7): Gilt die Karte laut {@link
   * Arbeitspaket#effektivDone} als erledigt, bleibt ein vorhandener Zeitstempel stehen, sonst gilt
   * {@code now}; gilt sie nicht als erledigt, entfällt er. Ohne diesen Zeitstempel fiele eine
   * erledigte Karte dauerhaft aus der Done-Aufbewahrung, die ausschließlich über ihn greift ({@code
   * findArchivableDoneCards} verlangt {@code movedToDoneAt is not null}), und ebenso aus
   * Abhängigkeiten, Durchlaufzeit und Überfälligkeit.
   */
  public static @Nullable Instant doneStempel(
      Card card, @Nullable String spaltenname, Instant now) {
    if (!Arbeitspaket.effektivDone(card, spaltenname)) {
      return null;
    }
    Instant bisher = card.movedToDoneAt();
    return bisher != null ? bisher : now;
  }

  /**
   * Folge eines Artwechsels durch Umbenennen (Plan #1294): Wird eine Dokumentkarte zum
   * Arbeitspaket, bekommt sie den Status ihrer Prozessspalte, sonst {@code BACKLOG}; wird ein
   * Arbeitspaket zur Dokumentkarte, entfällt ihr Status, und wieder zählt die Spalte. Der
   * Done-Zeitstempel folgt derselben Ableitung. Ohne Artwechsel bleibt alles, wie es ist — die
   * Spalte wird dann gar nicht erst nachgeschlagen.
   */
  public Card folgeArtwechsel(Card vorher, Card nachher) {
    boolean warPaket = Arbeitspaket.istArbeitspaket(vorher.type(), vorher.title());
    if (warPaket == Arbeitspaket.istArbeitspaket(nachher.type(), nachher.title())) {
      return nachher;
    }
    String spalte = boardService.requireColumn(nachher.columnId(), nachher.boardId()).name();
    return inSpalte(nachher.withStatus(null), spalte, clock.instant());
  }

  /** Eine blanke Beschreibung gilt als keine. */
  public static @Nullable String normalize(@Nullable String description) {
    return description == null || description.isBlank() ? null : description;
  }

  /** Ein blanker Wert gilt als keiner, sonst ohne Rand-Leerzeichen. */
  public static @Nullable String trimToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
