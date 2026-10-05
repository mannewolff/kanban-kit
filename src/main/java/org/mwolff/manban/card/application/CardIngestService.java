package org.mwolff.manban.card.application;

import static org.mwolff.manban.card.application.KartenGrundlage.normalize;
import static org.mwolff.manban.card.application.KartenSicht.statusName;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardBoardActivityEvent.ActivityType;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kanban-kompatibles Einliefern (Issue #1392, Plan #1387 E1): die schlanke Board-Liste, der
 * Board-Guard, der schmale Schreibweg für Titel und Beschreibung, das idempotente Anlegen direkt in
 * einer Spalte und das Ersetzen der Abhängigkeiten ohne Existenzprüfung. Einziger Nutzer ist der
 * {@code kanbancompat}-Ingest. Rechte über den {@link PermissionChecker} bzw. {@link
 * KartenGrundlage#requireCardOp}, Abhängigkeiten über {@link KartenAbhaengigkeiten}.
 */
@Service
public class CardIngestService {

  private final CardRepository cards;
  private final KartenAbhaengigkeiten abhaengigkeiten;
  private final BoardService boardService;
  private final PermissionChecker permissions;
  private final KartenGrundlage grundlage;
  private final KartenSicht sicht;
  private final Clock clock;

  public CardIngestService(
      CardRepository cards,
      KartenAbhaengigkeiten abhaengigkeiten,
      BoardService boardService,
      PermissionChecker permissions,
      KartenGrundlage grundlage,
      KartenSicht sicht,
      Clock clock) {
    this.cards = cards;
    this.abhaengigkeiten = abhaengigkeiten;
    this.boardService = boardService;
    this.permissions = permissions;
    this.grundlage = grundlage;
    this.sicht = sicht;
    this.clock = clock;
  }

  /**
   * Sichtbare Board-Items (Karten <em>und</em> Vorhaben) als schlanke Projektion für modulfremde
   * Aufrufer: ohne archivierte Karten, nach Position in der Spalte sortiert. Erfordert
   * Projekt-Mitgliedschaft (Leserecht).
   *
   * <p>Bewusst nicht {@link CardView}: diese Projektion kommt mit einer einzigen Abfrage aus,
   * während {@code sicht.view(...)} je Karte Abhängigkeiten, Zuständige und Labels nachlädt (N+1).
   * Der {@code kanbancompat}-Ingest listet ganze Boards und braucht davon nichts.
   */
  @Transactional(readOnly = true)
  public List<BoardItemView> listBoardItems(long userId, long boardId) {
    permissions.requireMembership(userId, boardService.requireProjectId(boardId));
    List<Card> sichtbar =
        cards.findByBoardId(boardId).stream()
            .filter(c -> !c.archived())
            .sorted(Comparator.comparingInt(Card::positionInColumn))
            .toList();
    Map<Long, Integer> herkunftsnummern = sicht.herkunftsnummern(sichtbar);
    return sichtbar.stream()
        .map(
            c ->
                new BoardItemView(
                    c.requireId(),
                    c.number(),
                    c.title(),
                    c.description(),
                    c.columnId(),
                    c.positionInColumn(),
                    c.type() == CardType.EPIC,
                    c.externalKey(),
                    c.derivedFromCardId() == null
                        ? null
                        : herkunftsnummern.get(c.derivedFromCardId()),
                    statusName(c)))
        .toList();
  }

  /**
   * Sichert zu, dass die Karte auf dem angegebenen Board liegt — der Board-Guard des
   * token-gebundenen {@code kanbancompat}-Zugriffs (#44).
   *
   * @throws CardNotFoundException wenn die Karte fehlt oder auf einem anderen Board liegt
   */
  @Transactional(readOnly = true)
  public void requireOnBoard(long cardId, long boardId) {
    Card card = cards.findById(cardId).orElseThrow(CardNotFoundException::new);
    // Wertvergleich der Board-IDs (Long): '!=' würde Referenzen vergleichen und bei IDs
    // jenseits des Long-Caches (> 127) falsch schlagen.
    if (!Long.valueOf(boardId).equals(card.boardId())) {
      throw new CardNotFoundException();
    }
  }

  /**
   * Ersetzt ausschließlich Titel und Beschreibung — der schmale Schreibweg für den
   * kanbancompat-Ingest (#571).
   *
   * <p>Abgrenzung zu {@link CardService#update}: Jene Methode ist ein Voll-Update und löscht bei
   * {@code null} die Vorhaben-Zuordnung, das Fälligkeitsdatum und (bei Vorhaben) das Kürzel. Ein
   * Aufrufer, der nur Titel und Rumpf kennt, kann sie deshalb nicht gefahrlos benutzen. Hier bleibt
   * alles andere stehen; Rechteprüfung, Aktivitätseintrag und Board-Ereignis sind identisch, damit
   * dieser Weg kein Schlupfloch am Audit und an den Rechten vorbei öffnet.
   *
   * <p>{@code description == null} heißt „nicht ändern"; ein blanker Wert löscht die Beschreibung
   * ({@link KartenGrundlage#normalize}). So vernichtet ein Aufrufer, der das Feld weglässt, keinen
   * Inhalt.
   *
   * <p>Rückgabe ist die {@link BoardItemView} — dieselbe domain-freie Form, die {@link
   * #listBoardItems} liefert. Ein {@link CardView} wäre hier unbrauchbar: Er trägt {@code CardType}
   * aus {@code card.domain}, und das Modul ist außerhalb von {@code card} nicht sichtbar
   * (ArchUnit-Regel {@code CARD_DOMAIN_IST_MODULINTERN}).
   */
  @Transactional
  public BoardItemView updateContent(
      long userId, long cardId, String title, @Nullable String description) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    Card saved =
        cards.save(
            grundlage.folgeArtwechsel(
                card,
                card.withContent(
                    title.trim(),
                    description == null ? card.description() : normalize(description))));
    grundlage.aktivitaet(
        cardId, userId, CardActivityType.UPDATED, "Karte bearbeitet", clock.instant());
    grundlage.publishChanged(saved.boardId(), ActivityType.UPDATED, cardId);
    return new BoardItemView(
        saved.requireId(),
        saved.number(),
        saved.title(),
        saved.description(),
        saved.columnId(),
        saved.positionInColumn(),
        saved.type() == CardType.EPIC,
        saved.externalKey(),
        sicht.herkunftsnummer(saved),
        statusName(saved));
  }

  /**
   * Legt eine Karte direkt in einer Spalte des Boards an — idempotent über den optionalen {@code
   * externalKey} (#535, Ingest auf ein Board). Existiert im Projekt bereits eine Karte mit diesem
   * Schlüssel — gleich ob auf einem Board, archiviert oder im Papierkorb —, wird nichts angelegt
   * und die bestehende Karte mit {@code created=false} zurückgegeben. Anlage,
   * Nummern-/Positionsvergabe, Spalten-Transition und Rechteprüfung laufen über den normalen
   * Anlege-Pfad; beim Duplikat entsteht nichts (kein Aktivitätseintrag, kein Event).
   *
   * <p>Nebenläufigkeit: bewusst SELECT-first statt Insert-and-catch — nach einer
   * Constraint-Verletzung wäre die Transaktion rollback-only, ein Nachlesen in ihr unmöglich. Den
   * seltenen Wettlauf zweier gleichzeitiger Erst-Ingests fängt der partielle Unique-Index (V24) als
   * Backstop; er endet als 409 über die bestehende {@code DataIntegrityViolationException}-
   * Behandlung (#496), und der Wiederholungs-Request trifft dann den SELECT.
   */
  @Transactional
  public CardCreation createDirect(long userId, long boardId, long columnId, DirectCard card) {
    long projectId = boardService.requireProjectId(boardId);
    // Rechte VOR dem Duplikat-Check: der Rückgabepfad darf Unberechtigten keine Existenz leaken.
    permissions.require(userId, projectId, Permission.TICKET_CREATE);
    // Die beiden Identitaetsfelder einmal in lokale Variablen: die Null-Pruefungen unten gelten
    // sonst nur fuer den jeweiligen Aufruf, nicht fuer den danach (SpotBugs
    // NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE).
    String externalKey = card.externalKey();
    Integer givenNumber = card.givenNumber();
    if (externalKey != null) {
      Optional<Card> existing = cards.findByProjectIdAndExternalKey(projectId, externalKey);
      if (existing.isPresent()) {
        requireMatchingNumber(existing.get(), givenNumber);
        // Idempotenz-Treffer: derivedFrom wird ignoriert wie Titel und Rumpf auch. Anders als
        // number, das requireMatchingNumber als Identitaetsfeld verifiziert (#565).
        return new CardCreation(sicht.view(userId, existing.get()), false);
      }
    }
    if (givenNumber != null) {
      requireNumberAvailable(projectId, givenNumber);
    }
    CardView created =
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
                externalKey,
                givenNumber,
                card.derivedFrom()));
    return new CardCreation(created, true);
  }

  /**
   * Der Idempotenz-Treffer muss dieselbe Identität liefern, die angefordert wurde (#565). Eine
   * abweichende Nummer stillschweigend zurückzugeben wäre das Gegenteil des Zwecks: Der Aufrufer
   * bekäme eine fremde Identität und merkte es erst, wenn migrierte Abhängigkeiten ins Leere
   * zeigen.
   */
  private static void requireMatchingNumber(Card existing, @Nullable Integer givenNumber) {
    if (givenNumber != null && !givenNumber.equals(existing.number())) {
      throw new CardNumberConflictException(
          "Der Schluessel gehoert bereits zu Karte #"
              + existing.number()
              + ", angefordert war #"
              + givenNumber
              + ".");
    }
  }

  /**
   * Prüft unter der Nummern-Sperre, ob eine vorgegebene Nummer vergeben werden darf (#565).
   *
   * <p>Reihenfolge ist wesentlich: erst sperren, dann prüfen. Läuft die Prüfung vor der Sperre,
   * rutscht eine gleichzeitige Anlage zwischen Prüfung und Schreiben — und genau diese Vorbedingung
   * ist der einzige Schutz davor, in ein gewachsenes Projekt hineinzuimportieren.
   */
  private void requireNumberAvailable(long projectId, int number) {
    cards.lockCardNumbers(projectId);
    if (cards.hasCardWithoutExternalKey(projectId)) {
      throw new CardNumberConflictException(
          "Das Projekt enthaelt Karten ausserhalb eines Imports — eine vorgegebene Nummer wird nur"
              + " in ein Projekt ohne importfremde Karten uebernommen.");
    }
    if (cards.isNumberTaken(projectId, number)) {
      throw new CardNumberConflictException("Nummer #" + number + " ist bereits vergeben.");
    }
  }

  /**
   * Ersetzt die Abhängigkeiten einer Karte, <strong>ohne</strong> die Zielnummern auf Existenz zu
   * prüfen (Issue #566) — für den Import aus einem anderen Tracker.
   *
   * <p>Der Unterschied zum UI-Pfad ist Absicht und der Kern dieser Aufgabe: {@link
   * KartenAbhaengigkeiten#ersetze(Card, List)} lehnt unbekannte Nummern hart ab, weil dort ein
   * Tippfehler wahrscheinlicher ist als eine noch nicht angelegte Karte. Beim Import ist es
   * umgekehrt — die Zielkarte kommt oft erst später an, und eine Reihenfolge zu erzwingen hieße,
   * den Import über die Abhängigkeitsgraphen zu sortieren. Die Datenbank trägt das: {@code
   * card_dependency.depends_on_card_number} ist eine Zahl ohne Fremdschlüssel, der Verweis heilt,
   * sobald die Zielkarte existiert.
   *
   * <p>{@code expectedProjectId} bindet die Karte an das Projekt des Aufrufers und ist bewusst Teil
   * dieser Methode statt eines eigenen Guards: Ein separater Aufruf ließe sich vergessen. Die
   * Prüfung ist projekt- und nicht boardbezogen — der Ingest kennt sein Projekt, nicht zwingend das
   * Board der Zielkarte.
   *
   * <p>Geteilt bleibt die Selbstverweis-Prüfung — sie hängt nicht am Wissen über andere Karten
   * ({@link KartenAbhaengigkeiten#ersetzeOhneExistenzpruefung}).
   */
  @Transactional
  public void replaceDependenciesFromIngest(
      long userId, long cardId, long expectedProjectId, @Nullable List<Integer> dependsOn) {
    Card card =
        grundlage.requireCardOp(userId, cardId, Permission.TICKET_UPDATE, Permission.EPIC_UPDATE);
    if (card.projectId() != expectedProjectId) {
      throw new CardNotFoundException();
    }
    abhaengigkeiten.ersetzeOhneExistenzpruefung(card, dependsOn);
  }

  /** Ergebnis eines idempotenten Ingests (#534): die Karte plus ob sie neu angelegt wurde. */
  public record CardCreation(CardView view, boolean created) {}

  /**
   * Die Nutzdaten einer direkt in einer Board-Spalte anzulegenden Karte (#535). Das Routing —
   * Benutzer, Board, Spalte — steht bewusst nicht hier, sondern bleibt skalar an {@link
   * #createDirect}, damit die Rechteprüfung vor dem Duplikat-Check lesbar bleibt.
   *
   * <p>{@code externalKey} und {@code givenNumber} sind Identitätsfelder (#565): Der Schlüssel
   * entscheidet über den Idempotenz-Treffer, und eine vorgegebene Nummer wird gegen die bestehende
   * Karte verifiziert. {@code derivedFrom} dagegen wird beim Idempotenz-Treffer ignoriert — wie
   * Titel und Rumpf auch.
   */
  public record DirectCard(
      String title,
      @Nullable String description,
      @Nullable String externalKey,
      @Nullable Integer givenNumber,
      @Nullable Integer derivedFrom) {}

  /**
   * Schlanke Board-Projektion einer Karte oder eines Vorhabens — ohne Abhängigkeiten, Zuständige
   * und Labels. {@code epic} unterscheidet die beiden Ausprägungen, ohne den Kartentyp aus {@code
   * card.domain} nach außen zu geben.
   *
   * <p>{@code externalKey} ist der Idempotenz-Schlüssel eines Automatik-Ingests (#534). Er wird
   * seit #573 mitgeliefert, damit ein Importwerkzeug seine eigenen Karten wiedererkennt, ohne dafür
   * schreiben zu müssen; {@code null} für alles, was nicht aus einem Ingest stammt.
   *
   * <p>{@code status} ist der eigene Status als Konstantenname von {@code CardStatus} — als Text,
   * weil {@code card.domain} modulintern bleibt (Plan #1294, E24); {@code null} bei Vorhaben und
   * Dokumentarten.
   */
  public record BoardItemView(
      long id,
      int number,
      String title,
      @Nullable String description,
      @Nullable Long columnId,
      int positionInColumn,
      boolean epic,
      @Nullable String externalKey,
      @Nullable Integer derivedFrom,
      @Nullable String status) {}
}
