package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenGrundlage.doneStempel;
import static org.mwolff.manban.card.application.KartenGrundlage.inSpalte;
import static org.mwolff.manban.card.application.KartenGrundlage.statusIn;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.board.application.BoardService.ColumnView;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Arbeitspaket;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verschieben, Status und Umzug (Issue #1395, Plan #1387 E1): Karten innerhalb eines Boards
 * verschieben, den Status eines Arbeitspakets setzen, eine Spalte nach Kartennummer ordnen und
 * Karten — einzeln oder gesammelt — auf ein anderes Board umziehen. Transaktionen, Sperren,
 * Rechteprüfung, Spaltenverlauf und Ereignisse sind unverändert die aus {@link CardService} (E3);
 * die Abhängigkeiten einer Karte, die das Projekt wechselt, räumt {@link KartenAbhaengigkeiten}
 * (E11).
 */
@Service
public class CardMoveService {

  /**
   * Zielposition „ans Ende der Spalte" für {@link #doMove(long, long, long, int)}. {@link
   * CardRepository#move(long, long, int)} begrenzt die Zielposition auf das aktive Band der
   * Zielspalte — ein Wert oberhalb jeder erreichbaren Spaltenlänge landet damit hinter der letzten
   * Karte, ohne die Spalte vorher zählen zu müssen. Ein zusätzlicher Zählzugriff wäre nicht nur
   * überflüssig: Er nähme eine zweite Spaltensperre und verstieße damit gegen die Regel „eine
   * Transaktion nimmt ihre Spaltensperren in einem Aufruf" ({@link
   * CardRepository#lockColumnPositions(List)}).
   */
  private static final int POSITION_AM_ENDE = Integer.MAX_VALUE;

  private final CardRepository cards;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final CardColumnTransitionRepository transitions;
  private final KartenZuordnung zuordnung;
  private final KartenGrundlage grundlage;
  private final KartenSicht sicht;
  private final Clock clock;

  public CardMoveService(
      CardRepository cards,
      KartenAbhaengigkeiten abhaengigkeiten,
      BoardService boardService,
      PermissionChecker permissions,
      CardColumnTransitionRepository transitions,
      KartenZuordnung zuordnung,
      KartenGrundlage grundlage,
      KartenSicht sicht,
      Clock clock) {
    this.cards = cards;
    this.abhaengigkeiten = abhaengigkeiten;
    this.boardService = boardService;
    this.permissions = permissions;
    this.transitions = transitions;
    this.zuordnung = zuordnung;
    this.grundlage = grundlage;
    this.sicht = sicht;
    this.clock = clock;
  }

  @Transactional
  public CardView move(long userId, long cardId, long targetColumnId, int targetPosition) {
    return doMove(userId, cardId, targetColumnId, targetPosition);
  }

  private CardView doMove(long userId, long cardId, long targetColumnId, int targetPosition) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics werden nicht auf dem Board positioniert");
    }
    permissions.require(
        userId, boardService.requireProjectId(card.boardId()), Permission.CARD_MOVE);

    ColumnView target = boardService.requireColumn(targetColumnId, card.boardId());

    cards.move(cardId, targetColumnId, targetPosition);

    // Spaltenverlauf: nur bei echtem Spaltenwechsel (kein Eintrag bei reinem Reindex). Ein einziger
    // Zeitstempel schließt die verlassene und eröffnet die Ziel-Spalte lückenlos.
    long fromColumn = card.columnId();
    if (fromColumn != targetColumnId) {
      Instant switchedAt = clock.instant();
      transitions.closeOpen(cardId, switchedAt);
      transitions.open(cardId, targetColumnId, target.name(), switchedAt);
      grundlage.aktivitaet(
          cardId, userId, CardActivityType.MOVED, "Verschoben nach " + target.name(), switchedAt);
    }

    // Status nur bei echtem Spaltenwechsel (Plan #1294): Umsortieren ändert ihn nicht. Der
    // Done-Zeitstempel folgt dem effektiven Maßstab — bei Arbeitspaketen dem Status (E7).
    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (fromColumn != targetColumnId) {
      moved = moved.withStatus(statusIn(moved, target.name()));
    }
    // CARD_MOVE ist oben geprüft, und die Karte bleibt im Projekt — die Sicht braucht keine zweite
    // Prüfung.
    CardView result =
        sicht.view(
            cards.save(moved.withMovedToDoneAt(doneStempel(moved, target.name(), clock.instant()))),
            true);
    grundlage.publishChanged(card.boardId(), ActivityType.MOVED, cardId);
    return result;
  }

  /**
   * Setzt den eigenen Status eines Arbeitspakets (Plan #1294, E9) und legt es in die Prozessspalte
   * dieses Status (Korrektur von #787 am 2026-10-01, Issue #1326). Recht wie beim Verschieben:
   * {@link Permission#CARD_MOVE}, geprüft vor jeder Auswertung der Eingabe.
   *
   * <p>Hat das Board eine Spalte, deren Name nach {@link Arbeitspaket#statusVonSpalte} den neuen
   * Status ergibt, und liegt die Karte nicht schon darin, wandert sie über {@code doMove} ans Ende
   * der ersten solchen Spalte in Board-Reihenfolge; Status, Done-Zeitstempel, Aufenthalt und
   * Verlauf folgen dort aus der Zielspalte. Sonst — keine passende Spalte, oder die Karte liegt
   * schon darin — wird nur der Status gesetzt, Spalte und Position bleiben.
   *
   * <p>Nebenwirkungen eines reinen Statuswechsels, alle mit demselben Zeitstempel:
   *
   * <ul>
   *   <li>der Done-Zeitstempel wird aus dem neuen Status abgeleitet (E7);
   *   <li>der Aufenthaltsverlauf schließt den offenen Aufenthalt und öffnet einen neuen in der
   *       tatsächlichen Spalte, benannt mit dem kanonischen Prozessnamen (E25) — sonst hätte ein
   *       Paket, das in einer eigenen Spalte per Status durch In progress läuft, keine
   *       Implementierungszeit;
   *   <li>ein Verlaufseintrag {@code STATUS_CHANGED} „Status auf &lt;Prozessname&gt;" (E14);
   *   <li>ein {@code UPDATED}-Ereignis für offene Boards.
   * </ul>
   *
   * <p>Der bisherige Status noch einmal gesetzt ist kein Wechsel und hinterlässt keine Spur.
   *
   * @param status Konstantenname von {@code CardStatus} (E24) — ein Anzeigename gilt nicht
   * @throws CardNotFoundException wenn die Karte nicht existiert
   * @throws InvalidStatusException bei unbekanntem Wert oder einer Karte ohne eigenen Status
   *     (Vorhaben, Dokumentart)
   */
  @Transactional
  public void setStatus(long userId, long cardId, String status) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    permissions.require(userId, card.projectId(), Permission.CARD_MOVE);
    CardStatus neu = statusAus(status);
    if (!Arbeitspaket.istArbeitspaket(card.type(), card.title())) {
      throw new InvalidStatusException("Diese Karte trägt keinen eigenen Status");
    }
    if (neu == card.status()) {
      return;
    }
    Optional<ColumnView> prozessspalte =
        boardService.listColumns(card.boardId()).stream()
            .filter(
                spalte ->
                    Arbeitspaket.statusVonSpalte(spalte.name()).filter(neu::equals).isPresent())
            .findFirst();
    if (prozessspalte.isPresent() && prozessspalte.get().id().longValue() != card.columnId()) {
      doMove(userId, cardId, prozessspalte.get().id().longValue(), POSITION_AM_ENDE);
      return;
    }
    Instant jetzt = clock.instant();
    Card gesetzt = card.withStatus(neu);
    // Bei einem Arbeitspaket entscheidet allein der Status über Done — die Spalte geht nicht ein.
    cards.save(gesetzt.withMovedToDoneAt(doneStempel(gesetzt, null, jetzt)));
    transitions.closeOpen(cardId, jetzt);
    transitions.open(cardId, card.columnId(), neu.anzeigename(), jetzt);
    grundlage.aktivitaet(
        cardId, userId, CardActivityType.STATUS_CHANGED, "Status auf " + neu.anzeigename(), jetzt);
    grundlage.publishChanged(card.boardId(), ActivityType.UPDATED, cardId);
  }

  /**
   * Der Status zu seinem Konstantennamen. Die Meldung wiederholt die Eingabe bewusst nicht — sie
   * stammt vom Client.
   */
  private static CardStatus statusAus(String status) {
    try {
      return CardStatus.valueOf(status);
    } catch (IllegalArgumentException e) {
      throw new InvalidStatusException("Unbekannter Status", e);
    }
  }

  /**
   * Ordnet die aktiven Karten einer Spalte nach ihrer Kartennummer — {@link SortDirection#ASC}
   * kleinste zuerst, {@link SortDirection#DESC} größte zuerst. Gedacht für Spalten, in die mehrere
   * Karten am Stück gezogen wurden und die deshalb ungeordnet dastehen.
   *
   * <p>Fachlich ist das ein <em>Massen-Verschieben innerhalb</em> der Spalte und keine
   * Strukturänderung am Board, deshalb genügt {@link Permission#CARD_MOVE} — dasselbe Recht wie für
   * das Verschieben einer einzelnen Karte. Karten außerhalb des aktiven Positions-Namespace
   * (archiviert, Papierkorb) und Vorhaben bleiben unberührt; Details am Port {@link
   * CardRepository#sortActiveByNumber(long, SortDirection)}.
   *
   * <p>Bewusst ohne {@link CardActivity}-Eintrag: Die Umsortierung ändert nur die Anordnung
   * innerhalb der Spalte, keine Karte wechselt Spalte oder Zustand — ein Audit-Eintrag pro
   * betroffener Karte würde den Aktivitätsverlauf fluten, ohne eine fachliche Änderung zu
   * dokumentieren. Offene Boards erfahren von der neuen Anordnung über das SSE-Event.
   */
  @Transactional
  public void sortColumnByNumber(long userId, long columnId, SortDirection direction) {
    long boardId = boardService.boardIdOfColumn(columnId);
    permissions.require(userId, boardService.requireProjectId(boardId), Permission.CARD_MOVE);

    cards.sortActiveByNumber(columnId, direction);

    // Ohne Karten-Bezug: betroffen ist die ganze Spalte, offene Boards laden über SSE neu.
    grundlage.publishChanged(boardId, ActivityType.MOVED, null);
  }

  /**
   * Verschiebt eine Karte in eine Spalte eines Boards; die Karte landet am Ende der Zielspalte.
   *
   * <p><b>Dasselbe Board</b> (Issue #1043): kein Umzug, sondern ein Spaltenwechsel — die Karte
   * behält Nummer, Vorhaben-Zuordnung, Abhängigkeiten und Zuständige, bekommt einen Verlaufseintrag
   * {@code MOVED} und ihren Done-Zeitpunkt nach denselben Regeln wie {@link #move(long, long, long,
   * int)}, an das dieser Fall delegiert. Liegt die Karte bereits in der Zielspalte, bleibt sie an
   * ihrem Platz, ohne Eintrag und ohne Fehler.
   *
   * <p><b>Anderes Board</b> — Rechte und Nebenwirkungen sind richtungsabhängig:
   *
   * <ul>
   *   <li><b>Selbes Projekt:</b> es genügt {@link Permission#CARD_MOVE} — dasselbe Recht wie für
   *       das Verschieben innerhalb eines Boards. Die Nummer bleibt erhalten (projektweit ohnehin
   *       eindeutig), Abhängigkeiten und Zuständige wandern mit — so brechen Querverweise beim
   *       Board-Wechsel nicht.
   *   <li><b>Anderes Projekt:</b> der Benutzer muss im Quell- <em>und</em> im Zielprojekt OWNER
   *       (oder Plattform-Admin) sein. Die Karte erhält eine neue projekt-scoped Nummer;
   *       Abhängigkeiten und Zuständige (projekt-lokal) werden entfernt.
   * </ul>
   *
   * <p>Die board-lokale Vorhaben-Zuordnung wird beim Board-Wechsel in beiden Fällen entfernt (das
   * Ziel-Board hat eigene Vorhaben). Kommentare und Anhänge wandern immer mit (an der Karten-ID).
   */
  @Transactional
  public CardView transfer(long userId, long cardId, long targetBoardId, long targetColumnId) {
    return doTransfer(userId, cardId, targetBoardId, targetColumnId);
  }

  private CardView doTransfer(long userId, long cardId, long targetBoardId, long targetColumnId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    if (card.type() == CardType.EPIC) {
      throw new InvalidDependencyException("Epics können nicht verschoben werden");
    }
    // Zielboard == Board der Karte: Das ist kein Umzug, sondern ein Spaltenwechsel, und dessen
    // Regeln stehen vollständig in doMove — Spaltenverlauf nur bei echtem Wechsel, Verlaufseintrag
    // MOVED, Status nur bei echtem Wechsel, Vorhaben-Zuordnung bleibt. Der Umzugspfad unten ist
    // auf den Board-Wechsel gebaut: Er leert parentId (das Ziel-Board hat eigene Vorhaben) und
    // schreibt Spaltenverlauf und Status auch dann fort, wenn die Spalte dieselbe bleibt. Auf dem
    // eigenen Board wäre jede dieser Wirkungen falsch.
    if (targetBoardId == card.boardId()) {
      return doMove(userId, cardId, targetColumnId, POSITION_AM_ENDE);
    }
    long sourceProjectId = boardService.requireProjectId(card.boardId());
    long targetProjectId = boardService.requireProjectId(targetBoardId);
    ColumnView targetColumn = boardService.requireColumn(targetColumnId, targetBoardId);

    boolean sameProject = sourceProjectId == targetProjectId;
    // Projektintern ist der Board-Wechsel nur ein Verschieben und verlangt daher CARD_MOVE. Über
    // Projektgrenzen bleibt es bei der strengen Eigentümer-Prüfung in beiden Projekten.
    if (sameProject) {
      permissions.require(userId, sourceProjectId, Permission.CARD_MOVE);
    } else {
      permissions.requireOwner(userId, sourceProjectId);
      permissions.requireOwner(userId, targetProjectId);
    }

    // Innerhalb desselben Projekts bleibt die Nummer erhalten (projektweit ohnehin eindeutig) und
    // Abhängigkeiten/Zuständige wandern mit — nur so bleiben Querverweise beim Board-Wechsel
    // stabil.
    // Nur über Projektgrenzen wird neu nummeriert und werden die projekt-lokalen Verknüpfungen
    // (Abhängigkeiten, Zuständige) entfernt.
    int newNumber = sameProject ? card.number() : cards.allocateCardNumber(targetProjectId);
    cards.transfer(cardId, targetBoardId, targetColumnId, newNumber);
    if (!sameProject) {
      abhaengigkeiten.entferne(cardId);
      zuordnung.entferneZustaendige(cardId);
    }

    // Spaltenverlauf: der board-/spaltenübergreifende Umzug zählt als Spaltenwechsel.
    Instant switchedAt = clock.instant();
    transitions.closeOpen(cardId, switchedAt);
    transitions.open(cardId, targetColumnId, targetColumn.name(), switchedAt);

    Card moved = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    // Status wie bei doMove aus der Zielspalte; der Done-Zeitstempel wird daraus neu abgeleitet
    // statt gelöscht — sonst fiele ein Paket mit Status DONE aus jeder Auswertung (Plan E18).
    Card cleaned = inSpalte(moved.withParent(null), targetColumn.name(), switchedAt);
    if (!sameProject) {
      // Die Herkunft ist projekt-lokal: Der Vorfahr bleibt zurueck, und ein Verweis ueber die
      // Projektgrenze zeigte auf eine Nummer, die dort einer anderen Karte gehoeren kann.
      // Anders als withParent(null) gilt das NUR beim Projektwechsel — innerhalb des Projekts
      // ueberlebt die Kette den Board-Wechsel.
      cleaned = cleaned.withDerivedFrom(null);
      // Dieselbe Begruendung fuer die Anforderung: Sie ist board- und damit projekt-lokal.
      cleaned = cleaned.withRequirement(null);
    }
    CardView result = sicht.view(userId, cards.save(cleaned));
    if (!sameProject) {
      // Gegenrichtung: Auch die Kinder verlieren ihren Verweis. Sonst zeigten sie auf die NEUE
      // Nummer der abgewanderten Karte — im eigenen Projekt womoeglich eine fremde. Bewusst ohne
      // Ausnahme fuer bulkTransfer: Wandern Vorfahr und Kind im selben Batch, haenge das Ergebnis
      // sonst von der Reihenfolge innerhalb des Batches ab.
      for (Card kind : cards.findByDerivedFrom(cardId)) {
        cards.save(kind.withDerivedFrom(null));
      }
      // Gegenrichtung der Anforderung: Wandert die Anforderungskarte ab, verliert das
      // zurueckbleibende Vorhaben seinen Verweis — sonst zeigte er ueber die Projektgrenze.
      for (Card vorhaben : cards.findByRequirementCard(cardId)) {
        cards.save(vorhaben.withRequirement(null));
      }
    }
    // Board-übergreifend: Quell- und Ziel-Board müssen beide live nachziehen.
    grundlage.publishChanged(card.boardId(), ActivityType.MOVED, cardId);
    grundlage.publishChanged(targetBoardId, ActivityType.MOVED, cardId);
    return result;
  }

  /**
   * Verschiebt mehrere Karten in einer Transaktion auf dasselbe Zielboard und dieselbe Zielspalte
   * (alles-oder-nichts). Nutzt je Karte die Einzel-Logik von {@link #transfer(long, long, long,
   * long)} inklusive der richtungsabhängigen Rechteprüfung ({@link Permission#CARD_MOVE} innerhalb
   * des Projekts, OWNER in Quell- und Zielprojekt darüber hinaus) sowie Vorhaben-Ausschluss;
   * scheitert eine Karte, rollt der gesamte Batch zurück. Die Karten landen in Eingabereihenfolge
   * am Ende der Zielspalte, jede Quellspalte wird dabei lückenlos nachgezogen. Das gilt auch, wenn
   * das Zielboard das Board der Karten ist — dann ist der Sammel-Umzug ein Sammel-Spaltenwechsel
   * (Issue #1043), und Karten, die schon in der Zielspalte liegen, bleiben unberührt.
   *
   * <p>Die Spaltensperren nimmt der Batch <strong>vorab in einem Zug</strong> (Issue #499): Nähme
   * jeder Einzel-Umzug seine beiden Sperren für sich, könnten zwei gleichzeitige Sammel-Umzüge mit
   * überlappenden Quellspalten dieselben Spalten in unterschiedlicher Reihenfolge greifen und
   * verklemmen. Ein sortierter Aufruf über die Vereinigung schließt das aus; die Sperren der
   * Einzel-Umzüge sind danach wirkungslose Wiederholungen.
   *
   * <p>Enthält der Batch eine Karte aus einem <em>anderen</em> Projekt, wird zuvor der
   * Nummern-Namespace des Zielprojekts gesperrt: Nur so bleibt die Ordnung „Projekt vor Spalte"
   * gewahrt, die jeder Einzel-Umzug einhält. Innerhalb eines Projekts entfällt diese Sperre — dort
   * wird keine Nummer neu vergeben, und ein Sammel-Umzug soll die Karten-Anlage im selben Projekt
   * nicht für die Dauer des Batches ausbremsen.
   */
  @Transactional
  public List<CardView> bulkTransfer(
      long userId, List<Long> cardIds, long targetBoardId, long targetColumnId) {
    long targetProjectId = boardService.requireProjectId(targetBoardId);
    List<Card> batch = cardIds.stream().map(cards::findById).flatMap(Optional::stream).toList();
    if (batch.stream().anyMatch(card -> !Objects.equals(card.projectId(), targetProjectId))) {
      cards.lockCardNumbers(targetProjectId);
    }
    List<Long> affectedColumns = new ArrayList<>();
    affectedColumns.add(targetColumnId);
    batch.forEach(card -> Optional.ofNullable(card.columnId()).ifPresent(affectedColumns::add));
    cards.lockColumnPositions(affectedColumns);
    return cardIds.stream()
        .map(cardId -> doTransfer(userId, cardId, targetBoardId, targetColumnId))
        .toList();
  }
}
