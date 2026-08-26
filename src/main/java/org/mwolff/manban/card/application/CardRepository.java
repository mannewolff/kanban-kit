package org.mwolff.manban.card.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.card.domain.Card;

/** Ausgehender Port für die Persistenz von Karten. */
public interface CardRepository {

  Card save(Card card);

  Optional<Card> findById(long id);

  /**
   * Nicht-gelöschte Karte eines Projekts nach ihrer projektweiten Nummer (board-gebundene Karte
   * oder board-lose Pool-Idee). Nummern sind projektweit eindeutig; leer, wenn keine solche Karte
   * existiert.
   */
  Optional<Card> findByProjectIdAndNumber(long projectId, int number);

  /**
   * Karte eines Projekts nach ihrem externen Idempotenz-Schlüssel (Issue #534) — bewusst
   * <strong>einschließlich</strong> archivierter und Papierkorb-Karten: Ein bewusst verworfenes
   * Finding soll den wiederholten Ingest weiterhin unterdrücken. Nur endgültiges Löschen (purge)
   * entfernt die Zeile und gibt den Schlüssel frei.
   */
  Optional<Card> findByProjectIdAndExternalKey(long projectId, String externalKey);

  /**
   * Nicht-gelöschte Karten mit dieser projektweiten Nummer aus den angegebenen Projekten,
   * aufsteigend nach Projekt-ID. Dieselbe Nummer kann in mehreren Projekten existieren ({@code
   * uq_card_number (project_id, number)}), deshalb eine Liste; je Projekt kann höchstens eine Karte
   * enthalten sein.
   *
   * <p>Die Projektmenge ist der <strong>Sichtbarkeitsschnitt des Aufrufers</strong> — die Suche
   * über mehrere Projekte fragt nur die, in denen der Benutzer lesen darf (#489). Eine leere Menge
   * ist deshalb nicht vorgesehen: Wer nichts sehen darf, fragt gar nicht erst.
   */
  List<Card> findByNumberInProjects(int number, List<Long> projectIds);

  List<Card> findByBoardId(long boardId);

  /**
   * IDs <strong>aller</strong> Karten des Boards — einschließlich archivierter und
   * Papierkorb-Karten. Exakt der Umfang der Datenbank-Cascade beim Board-Hard-Delete; für die
   * Purge-Kaskade (Issue #503), nicht für Anzeige-Pfade.
   */
  List<Long> findAllIdsByBoardId(long boardId);

  /** Alle nicht-gelöschten Karten eines Projekts (board-übergreifend, inkl. board-loser Ideen). */
  List<Card> findByProjectId(long projectId);

  /**
   * Ideen-Karten eines Projekts (idea_stored), älteste zuerst — board-lose Pool-Ideen und
   * board-gebundene Legacy-Ideen. Papierkorb-Karten sind ausgenommen.
   */
  List<Card> findIdeasByProjectId(long projectId);

  /** Nicht-archivierte Karten, die vor {@code threshold} nach Done verschoben wurden. */
  List<Card> findArchivableDoneCards(Instant threshold);

  /**
   * Nächste zu vergebende projektweite Kartennummer: die Untergrenze (Floor) aus höchster bereits
   * vergebener Nummer + 1 und der optionalen Projekt-Startnummer ({@code
   * project.next_card_number}). Ohne gesetzte Startnummer schlicht {@code max(number) + 1} (bzw. 1,
   * wenn das Projekt keine nummerierten Karten hat). Eine gesetzte Startnummer wirkt nur, solange
   * sie über der höchsten vergebenen Nummer liegt.
   *
   * <p>Reine <strong>Vorschau ohne Sperre</strong> — für die Anzeige des nächsten Werts. Wer die
   * Nummer tatsächlich vergibt, nimmt {@link #allocateCardNumber(long)}.
   */
  int nextCardNumber(long projectId);

  /**
   * Vergibt die nächste projektweite Kartennummer — dieselbe Zahl wie {@link
   * #nextCardNumber(long)}, aber gegen gleichzeitige Vergabe abgesichert.
   *
   * <p><strong>Warum das Sperren zum Lesen gehört (Issue #499):</strong> „lies das Maximum,
   * schreibe Maximum + 1" ist unter READ COMMITTED nicht atomar. Zwei gleichzeitige Anlagen lasen
   * dasselbe Maximum, rechneten dieselbe Nummer aus und schrieben beide; {@code uq_card_number}
   * verhinderte zwar falsche Daten, aber ein fachlich gültiger Request scheiterte. Genau dieser
   * Pfad läuft im Nachtbetrieb über die API, während am Board gearbeitet wird.
   *
   * <p>Serialisiert wird über die <em>Projektzeile</em>: Sie ist der natürliche Träger des
   * Nummern-Namespace ({@code uq_card_number (project_id, number)}) und hält zugleich die
   * Startnummer aus V20, die in dieselbe Rechnung eingeht. Eine Sperre auf den Kartenzeilen käme
   * nicht in Frage — die neue Nummer gehört zu einer Zeile, die es noch nicht gibt, und PostgreSQL
   * kennt keine Gap-Sperren. Der Floor-Mechanismus bleibt dadurch unverändert: Es ist dieselbe
   * Rechnung, nur unter Sperre.
   *
   * <p>Zwei Anlagen in <em>verschiedenen</em> Projekten bremsen sich nicht gegenseitig aus — die
   * Sperre liegt je Projekt.
   *
   * <p>Sperrordnung: Projekt vor Spalte. Wer in einem Aufruf beides braucht (Karte anlegen,
   * einplanen, projektübergreifend umhängen), vergibt <em>zuerst</em> die Nummer und <em>dann</em>
   * die Position — sonst könnten zwei Aufrufer über Kreuz verklemmen.
   */
  int allocateCardNumber(long projectId);

  /**
   * Nimmt dieselbe Sperre wie {@link #allocateCardNumber(long)}, ohne eine Nummer zu vergeben — für
   * Aufrufer, die die Vergabe erst später auslösen, die Sperrordnung „Projekt vor Spalte" aber
   * schon vorher einhalten müssen.
   *
   * <p>Das betrifft den Sammel-Umzug: Er sperrt die betroffenen Spalten vorab in einem Zug (siehe
   * {@link #lockColumnPositions(List)}), zieht die neuen Nummern aber erst je Karte. Ohne diesen
   * Vorgriff hielte er eine Spalte und wartete auf das Projekt, während ein gleichzeitiger
   * Einzel-Umzug das Projekt hält und auf die Spalte wartet.
   */
  void lockCardNumbers(long projectId);

  /**
   * Höchste bereits vergebene projektweite Kartennummer (0, wenn keine nummerierte Karte). Dient
   * der Validierung einer neu gesetzten Projekt-Startnummer (sie muss darüber liegen).
   */
  int highestNumberInProject(long projectId);

  /**
   * Ob die projektweite Nummer bereits vergeben ist — <strong>einschließlich archivierter und in
   * den Papierkorb verschobener Karten</strong> (Issue #565).
   *
   * <p>Bewusst nicht über {@link #findByProjectIdAndNumber(long, int)} zu erledigen: Die filtert
   * {@code deleted_at IS NULL} und übersieht damit genau die Karten, die den Unique-Constraint
   * {@code uq_card_number (project_id, number)} trotzdem belegen. Dasselbe Muster wie beim
   * Idempotenz-Check aus #534, der aus demselben Grund ohne Papierkorb-Filter arbeitet.
   *
   * <p>Aufrufer halten die Sperre aus {@link #lockCardNumbers(long)}, sonst kann zwischen Prüfung
   * und Anlage eine zweite Anlage dieselbe Nummer belegen.
   */
  boolean isNumberTaken(long projectId, int number);

  /**
   * Ob das Projekt mindestens eine Karte enthält, die <em>nicht</em> aus einem Ingest-Import stammt
   * (also ohne {@code external_key}) — einschließlich archivierter und Papierkorb-Karten.
   *
   * <p>Trägt die Vorbedingung für den Import mit vorgegebener Nummer (Issue #565): In ein
   * gewachsenes Projekt wird nicht hineinimportiert. Bewusst nicht „Projekt ist leer": Der Import
   * besteht aus vielen Aufrufen, nach dem ersten wäre das Projekt nicht mehr leer und der eigene
   * Lauf blockierte sich selbst.
   */
  boolean hasCardWithoutExternalKey(long projectId);

  /**
   * Vergibt die nächste freie aktive Position am Ende der Spalte (0 in einer leeren Spalte) und
   * sichert sie gegen gleichzeitige Vergabe ab.
   *
   * <p>Dasselbe Muster wie bei {@link #allocateCardNumber(long)}: „lies das Maximum, schreibe
   * Maximum + 1" war nicht atomar, zwei gleichzeitige Anlagen in derselben Spalte liefen in {@code
   * uq_card_active_position}. Serialisiert wird über die <em>Spaltenzeile</em> — die neue Position
   * gehört zu einer Karte, die es noch nicht gibt, und Gap-Sperren kennt PostgreSQL nicht.
   *
   * <p>Die Spalte ist damit auch die Granularität: Zwei Anlagen in <em>verschiedenen</em> Spalten —
   * auch desselben Boards — bremsen sich nicht gegenseitig aus.
   */
  int allocateActivePosition(long columnId);

  /**
   * Sperrt die angegebenen Spalten für positionsändernde Zugriffe bis zum Transaktionsende.
   *
   * <p>Für Operationen, die <em>mehrere</em> Spalten anfassen (Umzug: Quelle und Ziel). Die Sperren
   * werden in aufsteigender Spalten-ID genommen — eine feste Reihenfolge, in der zwei Aufrufer
   * dieselbe Menge nie über Kreuz greifen können.
   *
   * <p><strong>Regel:</strong> Eine Transaktion nimmt ihre Spaltensperren in <em>einem</em> Aufruf.
   * Zwei aufeinanderfolgende Aufrufe mit überlappenden Mengen wären wieder über Kreuz greifbar und
   * damit verklemmungsfähig — deshalb sperrt {@link org.mwolff.manban.card.application.CardService
   * CardService} bei Sammel-Umzügen die Vereinigung aller betroffenen Spalten vorab.
   */
  void lockColumnPositions(List<Long> columnIds);

  /**
   * Verschiebt eine Karte in eine Spalte an eine Zielposition und reindiziert die betroffenen
   * Spalten kollisionsfrei (Zwei-Phasen). Archivierte Karten bleiben unberührt (außerhalb des
   * aktiven Positions-Namespace). Quell- und Zielspalte werden dabei gesperrt (siehe {@link
   * #lockColumnPositions(List)}).
   *
   * @throws CardMovedConcurrentlyException wenn die Karte die Quellspalte verlassen hat, während
   *     die Sperren erworben wurden
   */
  void move(long cardId, long newColumnId, int newPosition);

  /**
   * Ordnet die aktiven Karten einer Spalte nach ihrer projektweiten Kartennummer und vergibt die
   * Positionen lückenlos neu ({@code 0..n-1}); {@link SortDirection#ASC} setzt die kleinste Nummer
   * nach vorn, {@link SortDirection#DESC} die größte.
   *
   * <p>Betroffen ist ausschließlich der <strong>aktive Positions-Namespace</strong> der Spalte
   * ({@code archived = false}, {@code idea_stored = false}, {@code deleted_at IS NULL}, {@code type
   * <> 'EPIC'}) — genau die Menge, für die {@code active_position} gesetzt ist. Archivierte,
   * gelöschte und Ideen-Speicher-Karten sowie Vorhaben behalten ihre Position: Sie halten keinen
   * aktiven Anspruch, und eine Neuvergabe würde ihre Rückkehr-Position ohne Grund verwerfen.
   *
   * <p>Die Spaltenzeile wird gesperrt (siehe {@link #lockColumnPositions(List)}) — die neue Ordnung
   * entsteht aus dem gelesenen Bestand, eine parallel angehängte Karte bliebe sonst außerhalb der
   * Neuvergabe zurück. Vergeben wird in zwei Phasen (erst parken, dann final setzen), weil {@code
   * uq_card_active_position} nicht deferrable ist: Eine direkte Neuvergabe kollidierte transient
   * mit den noch belegten Positionen.
   */
  void sortActiveByNumber(long columnId, SortDirection direction);

  /**
   * Hängt eine Karte board-/spaltenübergreifend um: setzt Board, Spalte und eine neue board-scoped
   * Nummer, hängt sie ans Ende der Zielspalte und reindiziert die Quellspalte lückenlos. Quell- und
   * Zielspalte werden dabei gesperrt (siehe {@link #lockColumnPositions(List)}).
   *
   * @throws CardMovedConcurrentlyException wenn die Karte die Quellspalte verlassen hat, während
   *     die Sperren erworben wurden
   */
  void transfer(long cardId, long targetBoardId, long targetColumnId, int newNumber);

  /** Verschiebt eine Karte in den Papierkorb (Soft-Delete): setzt {@code deleted_at}. */
  void softDelete(long cardId, Instant when);

  /**
   * Holt eine Karte aus dem Papierkorb zurück: löscht {@code deleted_at} und setzt sie an die
   * angegebene (freie) Position.
   */
  void restoreFromTrash(long cardId, int newPosition);

  /** Karten im Papierkorb eines Boards (deleted_at gesetzt), aufsteigend nach Nummer. */
  List<Card> findTrashByBoardId(long boardId);

  /** Karten, die vor {@code threshold} gelöscht wurden (für die Papierkorb-Retention). */
  List<Card> findPurgeableTrash(Instant threshold);

  /** Entfernt eine Karte endgültig (Hard-Delete). */
  void deleteById(long id);
}
