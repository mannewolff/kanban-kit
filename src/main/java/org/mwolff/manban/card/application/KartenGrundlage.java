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
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.BoardQuelle;
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
 *
 * <p>Schreibsperre bei fremdem Board (Issue #2161, Plan #2152 A10): {@link #anlegen} und {@link
 * #requireCardOp} lehnen nach der Rechteprüfung ab, wenn das Projekt sein Board in einem fremden
 * System führt; die übrigen Schreibwege der Dienste fragen {@link #pruefeSchreibbar(long)}. Der
 * Spiegel schreibt nicht über diese Helfer.
 */
// PMD.CouplingBetweenObjects: Mit Issue #2161 kommt der Port BoardQuelle dazu. Die Sperre gehört
// genau hierher, weil Anlegen und Kartenzugriff mit Rechteprüfung für alle Dienste nur hier stehen;
// ein eigener Baustein dafür hieße, jeden Dienst um eine weitere Abhängigkeit zu erweitern.
@SuppressWarnings("PMD.CouplingBetweenObjects")
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
  private final BoardQuelle boardQuelle;
  private final Anbietersicht anbieter;

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
      Clock clock,
      BoardQuelle boardQuelle,
      Anbietersicht anbieter) {
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
    this.boardQuelle = boardQuelle;
    this.anbieter = anbieter;
  }

  /**
   * Lehnt einen Schreibweg ab, wenn das Projekt ein fremdes Board hat (Issue #2161). Aufzurufen
   * nach der Rechteprüfung, damit ein Unberechtigter nichts über das Projekt erfährt.
   *
   * @throws org.mwolff.manban.project.application.FremdesBoardException bei fremdem Board
   */
  public void pruefeSchreibbar(long projectId) {
    boardQuelle.pruefeSchreibbar(projectId);
  }

  /**
   * Verlangt, dass die Karte für den Aufrufer sichtbar ist ({@link Anbietersicht}, Issue #2211) —
   * für die Dienste, die eine Karte selbst laden statt über {@link #requireCardOp}.
   *
   * @throws CardNotFoundException wenn die Karte dem Aufrufer verborgen ist
   */
  public void pruefeSichtbar(long userId, Card card) {
    anbieter.pruefeSichtbar(userId, card);
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
   * Serververifizierte Herkunft der laufenden Anfrage ({@link ActorContext}) — die Grundlage der
   * Richtungsregel für die Freigabe-Labels ({@link FreigabeLabels}, Issue #1421).
   */
  public @Nullable CardActivityOrigin herkunft() {
    return actor.current().origin();
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
   * Wie {@link #aktivitaet(long, long, CardActivityType, String, Instant)}, dazu mit dem Status der
   * Karte nach der Bewegung (Issue #1426); {@code null}, wenn die Aktivität keinen festhält.
   */
  public void aktivitaet(
      long cardId,
      long userId,
      CardActivityType type,
      String detail,
      Instant zeitpunkt,
      @Nullable CardStatus statusAfter) {
    activity.add(cardId, userId, type, detail, zeitpunkt, actor.current(), statusAfter);
  }

  /**
   * Wie {@link #aktivitaet(long, long, CardActivityType, String, Instant)}, gestempelt mit jetzt.
   */
  public void aktivitaet(long cardId, long userId, CardActivityType type, String detail) {
    aktivitaet(cardId, userId, type, detail, clock.instant());
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
    boardQuelle.pruefeSchreibbar(projectId);
    Long herkunft = DerivedFrom.resolve(cards, projectId, derivedFrom, null);
    ColumnView column = boardService.requireColumn(columnId, boardId);
    Long effectiveParent =
        parentId == null ? null : requireEpicInBoard(parentId, boardId).requireId();

    int number = givenNumber != null ? givenNumber : cards.allocateCardNumber(projectId);
    int position = cards.allocateActivePosition(columnId);
    // Ein Zeitpunkt von außen (Issue #2159) gilt für Anlage und ersten Spalteneintrag gleich.
    Instant now = FlussZeitpunkt.jetzt(clock);
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
    // Status und Done-Zeitstempel leitet KartenStatus aus der Lage der Zielspalte ab (Plan #1294,
    // E5/E7, Issue #1474).
    Card saved =
        cards.save(
            KartenStatus.inSpalte(
                neu, KartenStatus.bis(boardService.listColumns(boardId), column), now));

    transitions.open(saved.requireId(), columnId, column.name(), now);
    aktivitaet(saved.requireId(), userId, CardActivityType.CREATED, "Karte angelegt", now);
    abhaengigkeiten.ersetze(saved, dependsOn);
    if (assigneeIds != null && !assigneeIds.isEmpty()) {
      zuordnung.ersetzeZustaendige(saved.requireId(), projectId, assigneeIds, userId);
    }
    if (labelIds != null && !labelIds.isEmpty()) {
      zuordnung.ersetzeLabels(saved.requireId(), boardId, labelIds, herkunft(), userId);
    }
    publishChanged(boardId, ActivityType.CREATED, saved.requireId());
    return saved;
  }

  /**
   * Lädt die Karte und verlangt das je nach Kartentyp (Ticket/Vorhaben) passende Recht. Die Rechte
   * sind projekt-basiert und werden über {@code card.projectId()} (immer gesetzt, V18) geprüft —
   * nicht über das Board; für board-gebundene Karten ist die Prüfung identisch (die Projekt-ID
   * stimmt mit dem Board-Projekt überein).
   *
   * <p>Eine Karte, die der Aufrufer nicht sieht, endet vor der Rechteprüfung mit {@link
   * CardNotFoundException} ({@link Anbietersicht}, Issue #2211) — eine 403 verriete, dass es sie
   * gibt.
   */
  public Card requireCardOp(
      long userId, long cardId, Permission ticketPermission, Permission epicPermission) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    anbieter.pruefeSichtbar(userId, card);
    permissions.require(
        userId, card.projectId(), card.type() == CardType.EPIC ? epicPermission : ticketPermission);
    boardQuelle.pruefeSchreibbar(card.projectId());
    return card;
  }

  /**
   * Das Vorhaben zur ID, sofern es auf diesem Board liegt und nicht archiviert ist — ein
   * archiviertes Vorhaben lässt sich keiner Karte mehr zuordnen (Issue #1494, AK 5).
   */
  public Card requireEpicInBoard(long epicId, long boardId) {
    return requireZuordenbaresVorhaben(epicId, boardId, null);
  }

  /**
   * Wie {@link #requireEpicInBoard}, aber für eine bestehende Karte mit der Zuordnung {@code
   * bisher}: Bleibt die Zuordnung unverändert, entfällt die Archiv-Prüfung (Plan #1504, E4). Das
   * Bearbeiten-Formular schickt die Zuordnung im Voll-Update immer mit; ohne diese Ausnahme wäre
   * eine Karte eines archivierten Vorhabens nicht mehr bearbeitbar.
   */
  public Card requireZuordenbaresVorhaben(long epicId, long boardId, @Nullable Long bisher) {
    Card epic = cards.findById(epicId).orElseThrow(CardNotFoundException::new);
    if (epic.type() != CardType.EPIC || epic.boardId() != boardId) {
      throw InvalidDependencyException.keinEpicDesBoards(epicId);
    }
    if (epic.archived() && !Long.valueOf(epicId).equals(bisher)) {
      throw InvalidDependencyException.vorhabenArchiviert(epicId);
    }
    return epic;
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
   * Arbeitspaket, bekommt sie den Status nach der Lage ihrer Spalte ({@link KartenStatus}); wird
   * ein Arbeitspaket zur Dokumentkarte, entfällt ihr Status, und wieder zählt die Spalte. Der
   * Done-Zeitstempel folgt derselben Ableitung. Ohne Artwechsel bleibt alles, wie es ist — die
   * Spalte wird dann gar nicht erst nachgeschlagen.
   */
  public Card folgeArtwechsel(Card vorher, Card nachher) {
    boolean warPaket = Arbeitspaket.istArbeitspaket(vorher.type(), vorher.title());
    if (warPaket == Arbeitspaket.istArbeitspaket(nachher.type(), nachher.title())) {
      return nachher;
    }
    ColumnView spalte = boardService.requireColumn(nachher.columnId(), nachher.boardId());
    return KartenStatus.inSpalte(
        nachher.withStatus(null),
        KartenStatus.bis(boardService.listColumns(nachher.boardId()), spalte),
        clock.instant());
  }

  /**
   * Hält eine Änderung am Wegbeleg der Karte fest (Plan #2108 E14). Jeder Weg, der Titel oder
   * Beschreibung ändert, läuft hierüber, damit die Herkunft der Änderung keine Rolle spielt.
   */
  public Card folgeWegBeleg(Card vorher, Card nachher) {
    return nachher.withWegBelegVergleich(vorher, clock.instant());
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
