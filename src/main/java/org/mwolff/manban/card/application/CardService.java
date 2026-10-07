package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenGrundlage.normalize;
import static org.mwolff.manban.card.application.KartenGrundlage.trimToNull;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CardService {

  private final CardRepository cards;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final KartenZuordnung zuordnung;
  private final CardActivityRepository activity;
  private final KartenGrundlage grundlage;
  private final KartenSicht sicht;

  public CardService(
      CardRepository cards,
      KartenAbhaengigkeiten abhaengigkeiten,
      BoardService boardService,
      PermissionChecker permissions,
      KartenZuordnung zuordnung,
      CardActivityRepository activity,
      KartenGrundlage grundlage,
      KartenSicht sicht) {
    this.cards = cards;
    this.abhaengigkeiten = abhaengigkeiten;
    this.boardService = boardService;
    this.permissions = permissions;
    this.zuordnung = zuordnung;
    this.activity = activity;
    this.grundlage = grundlage;
    this.sicht = sicht;
  }

  /**
   * Legt eine Karte an (ohne Fälligkeit/Zuständige/Labels). Delegiert an die Voll-Signatur mit
   * {@code null} für die inhaltlichen Zusatzfelder — die schlanke Überladung für alle Aufrufer, die
   * diese Felder nicht setzen.
   */
  @Transactional
  public CardView create(
      long userId,
      long boardId,
      long columnId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable Long parentId) {
    return sicht.view(
        userId,
        grundlage.anlegen(
            userId,
            boardId,
            columnId,
            title,
            description,
            dependsOn,
            parentId,
            null,
            null,
            null,
            null,
            null,
            null));
  }

  /**
   * Legt eine Karte an. {@code dueDate}, {@code assigneeIds} und {@code labelIds} werden — sofern
   * gesetzt — atomar mit der Anlage übernommen (ein einziger {@code CREATED}-Aktivitätseintrag,
   * kein Teil-Zustand); {@code null}/leer bedeutet „nicht gesetzt". Assignees/Labels durchlaufen
   * dieselbe Prüfung wie {@link #setAssignees} / {@link #setLabels} (Mitglied im Projekt, Label des
   * Boards).
   */
  @Transactional
  public CardView create(
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
      @Nullable Integer derivedFrom) {
    return sicht.view(
        userId,
        grundlage.anlegen(
            userId,
            boardId,
            columnId,
            title,
            description,
            dependsOn,
            parentId,
            dueDate,
            assigneeIds,
            labelIds,
            null,
            null,
            derivedFrom));
  }

  /**
   * Legt mehrere Karten in einem Zug am Ende einer Board-Spalte an (Issue #1200) — der
   * board-gebundene Weg des Spezifikations-Imports. Recht: {@link Permission#TICKET_CREATE}, einmal
   * für den ganzen Stapel geprüft; importieren ist fachlich dasselbe wie Karten anlegen, nur in
   * Menge.
   *
   * <p><b>Alles-oder-nichts.</b> Die Methode läuft in einer Transaktion: schlägt eine Karte fehl,
   * entsteht keine. Ein halb importierter Spec wäre schwerer aufzuräumen (welche Abschnitte
   * fehlen?) als ein wiederholter Import. Feld- und Mengengrenzen prüft bereits die Bean-Validation
   * am Endpoint, sodass ein ungültiges Element gar nicht bis hierher gelangt.
   *
   * <p><b>Ein Ereignis je Karte</b> statt eines gebündelten: Der Stapel läuft bewusst über den
   * gemeinsamen Anlegepfad {@link KartenGrundlage#anlegen}, damit Nummern-, Positions- und
   * Transitionsvergabe unverändert greifen — das Ereignis je Karte ist dessen Nebenwirkung, und der
   * Board-Strom liefert ohnehin erst nach Commit aus.
   *
   * <p>Die Karten landen in Eingabereihenfolge am Ende der Zielspalte: {@code anlegen} holt je
   * Karte eine frische aktive Position aus {@link CardRepository#allocateActivePosition(long)}.
   */
  @Transactional
  public List<CardView> createCardsBatch(
      long userId, long boardId, long columnId, List<NewCard> neueKarten) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    // Die Spalte einmal vorab gegen das Board prüfen: ein falsches Ziel soll scheitern, bevor die
    // erste Karte eine Nummer verbraucht hat. anlegen prüft sie je Karte erneut (unverändert).
    boardService.requireColumn(columnId, boardId);
    return neueKarten.stream()
        .map(
            card ->
                sicht.view(
                    userId,
                    grundlage.anlegen(
                        userId,
                        boardId,
                        columnId,
                        card.title(),
                        card.description(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null)))
        .toList();
  }

  /**
   * Karten eines Boards (ohne Vorhaben) mit ihren Zusatzdaten — die Sicht baut {@link
   * KartenSicht#listenSicht} mit Sammelzugriffen statt je Karte (Issue #768, #771).
   *
   * <p>Gefiltert wird wie bisher <b>nur</b> nach {@link CardType#CARD}: Archivierte Karten bleiben
   * enthalten, die Reihenfolge ist die von {@link CardRepository#findByBoardId}.
   */
  @Transactional(readOnly = true)
  public List<CardView> listByBoard(long userId, long boardId) {
    long projectId = boardService.requireProjectId(boardId);
    permissions.requireMembership(userId, projectId);
    return sicht.listenSicht(
        userId,
        projectId,
        cards.findByBoardId(boardId).stream().filter(c -> c.type() == CardType.CARD).toList());
  }

  /**
   * Projekt-ID der Karte — die Auflösung, die modulfremde Rechteprüfungen (Anhänge, Kommentare)
   * brauchen, ohne das Kartenaggregat oder dessen Port zu kennen. Projekt-basiert über {@code
   * card.projectId()} (immer gesetzt, V18) statt über das Board.
   *
   * @throws CardNotFoundException wenn die Karte nicht existiert
   */
  @Transactional(readOnly = true)
  public long requireProjectId(long cardId) {
    return cards.findById(cardId).orElseThrow(CardNotFoundException::new).projectId();
  }

  /**
   * Einzelne Karte für Stellen, die nur eine Karten-ID kennen (Dashboard-Ausreißer, #515) — ohne
   * den Umweg über die komplette Board-Kartenliste. Leserecht wie bei den übrigen Lesepfaden:
   * Projekt-Mitgliedschaft über die Projekt-ID der Karte; Nichtmitglied und unbekannte Karte sind
   * nicht unterscheidbar (beide 404, kein Existenz-Leak).
   *
   * @throws CardNotFoundException wenn die Karte nicht existiert
   */
  @Transactional(readOnly = true)
  public CardView getCard(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.requireMembership(userId, card.projectId());
    return sicht.view(userId, card);
  }

  @Transactional
  public CardView update(
      long userId,
      long cardId,
      String title,
      @Nullable String description,
      @Nullable List<Integer> dependsOn,
      @Nullable String shortcode,
      @Nullable Long parentId,
      @Nullable Instant dueDate) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Card updated = card.withContent(title.trim(), normalize(description));
    if (card.type() == CardType.EPIC) {
      // Vorhaben tragen ein Kürzel, aber keinen Parent.
      updated = updated.withShortcode(trimToNull(shortcode));
    } else {
      // Karten: Vorhaben-Zuordnung im selben PUT setzen/lösen (parentId == null -> lösen).
      Long effectiveParent =
          parentId == null
              ? null
              : grundlage
                  .requireZuordenbaresVorhaben(parentId, card.boardId(), card.parentId())
                  .requireId();
      updated = updated.withParent(effectiveParent).withDueDate(dueDate);
    }
    Card saved = cards.save(grundlage.folgeArtwechsel(card, updated));
    grundlage.aktivitaet(cardId, userId, CardActivityType.UPDATED, "Karte bearbeitet");
    if (dependsOn != null) {
      abhaengigkeiten.ersetze(saved, dependsOn);
    }
    grundlage.publishChanged(saved.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, saved);
  }

  /**
   * Ersetzt die Zuständigen einer Karte. Nur Karten (keine Vorhaben); zugewiesen werden dürfen
   * ausschließlich Mitglieder des Projekts. Recht: {@link Permission#TICKET_UPDATE} (Member und
   * aufwärts).
   */
  @Transactional
  public CardView setAssignees(long userId, long cardId, List<Long> assigneeIds) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Zuständige");
    }
    // Projekt-basierte Rechte (#405), nicht über das Board.
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);

    zuordnung.ersetzeZustaendige(cardId, card.projectId(), assigneeIds);
    grundlage.aktivitaet(cardId, userId, CardActivityType.ASSIGNED, "Zuständige geändert");
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, card);
  }

  /**
   * Ersetzt die Labels einer Karte. Nur Karten (keine Vorhaben); zugeordnet werden dürfen nur
   * Labels desselben Boards. Recht: {@link Permission#TICKET_UPDATE} (Member und aufwärts). Die
   * Freigabe-Labels des Kits ändert ein Token nur in ihrer Richtung ({@link FreigabeLabels}).
   */
  @Transactional
  public CardView setLabels(long userId, long cardId, List<Long> labelIds) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Labels");
    }
    // Projekt-basierte Rechte (#405); die Labels selbst bleiben board-scoped.
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);

    zuordnung.ersetzeLabels(cardId, card.boardId(), labelIds, grundlage.herkunft());
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
    return sicht.view(userId, card);
  }

  /**
   * Setzt <b>ein</b> Label an mehreren Karten oder nimmt es ihnen ab — in einer Transaktion
   * (alles-oder-nichts). Je Karte gelten dieselben Prüfungen wie bei {@link #setLabels}; scheitert
   * eine, rollt der ganze Batch zurück.
   *
   * <p><b>Warum hinzufügen/abnehmen statt ersetzen:</b> Die Auswahl trägt in aller Regel
   * unterschiedliche Labels. Eine ersetzende Massenaktion löschte die übrigen still mit — der
   * Nutzer sähe nur das gesetzte Label und nicht, was dafür verschwunden ist (Issue #994).
   *
   * <p>Eine Karte, die das Label schon trägt (bzw. schon nicht trägt), bleibt unverändert und ist
   * kein Fehler: Die Massenaktion beschreibt einen Zielzustand, keinen Umschalter je Karte.
   */
  @Transactional
  public List<CardView> bulkLabels(
      long userId, List<Long> cardIds, long labelId, LabelAction action) {
    return cardIds.stream().map(cardId -> doLabel(userId, cardId, labelId, action)).toList();
  }

  /**
   * Setzt <b>ein</b> Label an einer Karte oder nimmt es ab; die übrigen Labels bleiben unberührt.
   * Prüfungen wie bei {@link #setLabels}, ein schon gesetztes (bzw. schon fehlendes) Label ist kein
   * Fehler.
   *
   * <p><b>Warum nicht {@link #setLabels}:</b> Die ersetzende Liste baut der Aufrufer aus dem Stand,
   * den er gerade anzeigt. Ist der veraltet, schreibt das Speichern ein inzwischen abgenommenes
   * Label zurück — etwa ein vom Runner verbrauchtes {@code kit:night} (Issue #1512). Die
   * Einzeländerung kann nichts zurückschreiben, was sie nicht meint.
   */
  @Transactional
  public CardView changeLabel(long userId, long cardId, long labelId, LabelAction action) {
    return doLabel(userId, cardId, labelId, action);
  }

  private CardView doLabel(long userId, long cardId, long labelId, LabelAction action) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() != CardType.CARD) {
      throw new InvalidDependencyException("Nur Karten haben Labels");
    }
    permissions.require(userId, card.projectId(), Permission.TICKET_UPDATE);

    long boardId = card.boardId();
    zuordnung.aendereLabel(cardId, boardId, labelId, action, grundlage.herkunft());
    grundlage.publishChanged(boardId, ActivityType.UPDATED, cardId);
    return sicht.view(userId, card);
  }

  /**
   * Eine anzulegende Board-Karte im Stapel (Issue #1200): Titel und optionale Beschreibung. Board
   * und Spalte stehen bewusst nicht hier, sondern gelten für den ganzen Stapel — er füllt genau
   * eine Spalte.
   */
  public record NewCard(String title, @Nullable String description) {}

  /**
   * Aktivitätsverlauf einer Karte (chronologisch). Erfordert Projekt-Mitgliedschaft (Leserecht),
   * geprüft über {@code card.projectId()} (#405) und nicht über das Board.
   */
  @Transactional(readOnly = true)
  public List<CardActivity> listActivity(long userId, long cardId) {
    return doListActivity(userId, cardId);
  }

  /**
   * Der Kern ohne Annotation, damit ihn {@link #listActivityViews(long, long)} rufen kann, ohne
   * über {@code this} an einer {@code @Transactional}-Methode vorbeizugehen (Sonar java:S6809) —
   * dasselbe Muster wie {@code CardArchiveService.doArchive} und {@code EpicService.doCreateEpic}.
   */
  private List<CardActivity> doListActivity(long userId, long cardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.requireMembership(userId, card.projectId());
    return activity.findByCardId(cardId);
  }

  /**
   * Derselbe Verlauf wie {@link #listActivity(long, long)}, als Fassaden-Sicht ohne Typen aus
   * {@code card.domain} — der Weg, auf dem fremde Module (heute {@code kanbancompat}, #876) den
   * Verlauf lesen. Rechte und Reihenfolge sind unverändert die von {@code listActivity}.
   */
  @Transactional(readOnly = true)
  public List<ActivityView> listActivityViews(long userId, long cardId) {
    return doListActivity(userId, cardId).stream().map(CardService::activityView).toList();
  }

  private static ActivityView activityView(CardActivity a) {
    return new ActivityView(
        a.id(),
        a.actorUserId(),
        a.type().name(),
        a.detail(),
        a.createdAt(),
        a.origin() == null ? null : a.origin().name(),
        a.tokenName(),
        a.agent());
  }

  /**
   * Ein Eintrag des Aktivitätsverlaufs als Fassaden-Sicht: {@code type} und {@code origin} sind die
   * Namen der zugehörigen Aufzählungen, damit die Sicht keinen Typ aus {@code card.domain} nach
   * außen gibt.
   *
   * <p>{@code origin} und {@code tokenName} sind serverseitig verifiziert, {@code agent} ist eine
   * Selbstauskunft des Clients — siehe Issue #517. Bei Alt-Einträgen aus der Zeit vor Migration V23
   * sind alle drei Felder {@code null}.
   */
  public record ActivityView(
      @Nullable Long id,
      @Nullable Long actorUserId,
      String type,
      String detail,
      Instant createdAt,
      @Nullable String origin,
      @Nullable String tokenName,
      @Nullable String agent) {}
}
