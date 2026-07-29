package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.card.application.CardMovedConcurrentlyException;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.SortDirection;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Adapter des {@link CardRepository}-Ports auf Spring Data JPA. */
@Component
class CardRepositoryAdapter implements CardRepository {

  /** Temporärer Offset weit außerhalb des realen Positionsbereichs für den Reindex. */
  private static final int PARK_OFFSET = 100_000;

  private static final int PARK_MOVED_CARD = 999_999;

  /**
   * Prädikat des aktiven Positions-Namespace — dieselbe Bedingung, unter der die generierte Spalte
   * {@code active_position} einen Wert trägt (V16). Als Konstante, damit Lesen und Schreiben beim
   * Sortieren nachweislich dieselbe Menge treffen.
   */
  private static final String ACTIVE_NAMESPACE =
      "AND archived = false AND idea_stored = false AND deleted_at IS NULL AND type <> 'EPIC' ";

  private final CardJpaRepository jpa;
  private final JdbcTemplate jdbc;
  private final EntityManager entityManager;

  CardRepositoryAdapter(CardJpaRepository jpa, JdbcTemplate jdbc, EntityManager entityManager) {
    this.jpa = jpa;
    this.jdbc = jdbc;
    this.entityManager = entityManager;
  }

  @Override
  public Card save(Card card) {
    return toDomain(jpa.save(toEntity(card)));
  }

  @Override
  public Optional<Card> findById(long id) {
    return jpa.findById(id).map(CardRepositoryAdapter::toDomain);
  }

  @Override
  public Optional<Card> findByProjectIdAndNumber(long projectId, int number) {
    return jpa.findByProjectIdAndNumberAndDeletedAtIsNull(projectId, number)
        .map(CardRepositoryAdapter::toDomain);
  }

  @Override
  public List<Card> findByBoardId(long boardId) {
    return jpa.findByBoardIdAndDeletedAtIsNullOrderByNumber(boardId).stream()
        .map(CardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Long> findAllIdsByBoardId(long boardId) {
    return jpa.findAllIdsByBoardId(boardId);
  }

  @Override
  public List<Card> findByProjectId(long projectId) {
    return jpa.findByProjectIdAndDeletedAtIsNull(projectId).stream()
        .map(CardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Card> findIdeasByProjectId(long projectId) {
    return jpa
        .findByProjectIdAndIdeaStoredTrueAndDeletedAtIsNullOrderByCreatedAtAsc(projectId)
        .stream()
        .map(CardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Card> findTrashByBoardId(long boardId) {
    return jpa.findByBoardIdAndDeletedAtIsNotNullOrderByNumber(boardId).stream()
        .map(CardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Card> findPurgeableTrash(java.time.Instant threshold) {
    return jpa.findByDeletedAtNotNullAndDeletedAtBefore(threshold).stream()
        .map(CardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public List<Card> findArchivableDoneCards(java.time.Instant threshold) {
    return jpa.findArchivableDoneCards(threshold).stream()
        .map(CardRepositoryAdapter::toDomain)
        .toList();
  }

  @Override
  public int nextCardNumber(long projectId) {
    // Untergrenze aus max(number)+1 und der optionalen Projekt-Startnummer. COALESCE fängt leeres
    // Projekt bzw. nicht gesetzte Startnummer (NULL) ab; GREATEST liefert stets einen Wert.
    return java.util.Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT GREATEST("
                + "COALESCE((SELECT MAX(number) FROM card WHERE project_id = ?), 0) + 1, "
                + "COALESCE((SELECT next_card_number FROM project WHERE id = ?), 0))",
            Integer.class,
            projectId,
            projectId));
  }

  @Override
  public int allocateCardNumber(long projectId) {
    // Dieselbe Rechnung wie nextCardNumber — nur unter Sperre auf der Projektzeile, damit zwei
    // gleichzeitige Anlagen nicht dieselbe Nummer ausrechnen (Issue #499, Begründung am Port).
    // Der Floor aus V20 bleibt dadurch unangetastet: Die Formel ändert sich nicht.
    lockCardNumbers(projectId);
    return nextCardNumber(projectId);
  }

  @Override
  public void lockCardNumbers(long projectId) {
    jdbc.queryForList("SELECT id FROM project WHERE id = ? FOR UPDATE", Long.class, projectId);
  }

  @Override
  public int highestNumberInProject(long projectId) {
    return java.util.Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(number), 0) FROM card WHERE project_id = ?",
            Integer.class,
            projectId));
  }

  @Override
  public int allocateActivePosition(long columnId) {
    lockColumnPositions(List.of(columnId));
    return jpa.maxActivePositionInColumn(columnId) + 1;
  }

  /**
   * Sperrt die Spaltenzeilen aufsteigend nach ID (Issue #499, Begründung am Port). Erwartet
   * mindestens eine ID — die Aufrufer kennen die betroffenen Spalten und übergeben sie vollständig.
   *
   * <p>Bewusst eine Skalar-Projektion per {@link JdbcTemplate}: Gäbe die Abfrage Spalten-Entities
   * zurück, lieferte Hibernate für bereits im Persistenzkontext liegende Zeilen die
   * zwischengespeicherte Instanz — und die Sperre bliebe wirkungslos für das, was danach gelesen
   * wird.
   */
  // Sonar java:S2077 (Issue #508, GitHub #402): Der konkatenierte Teil ist ausschließlich
  // "placeholders" — eine Aneinanderreihung des Literals "?" per Collections.nCopies, deren
  // einzige variable Größe die Anzahl der IDs ist. Kein Zeichen der übergebenen Werte gelangt in
  // den SQL-Text; die IDs selbst sind typisierte Long-Bindeparameter. Damit ist die Konkatenation
  // injektionsfest — die Regel greift bereits auf die String-Verknüpfung, ohne den Datenfluss zu
  // prüfen. Ein Umbau auf NamedParameterJdbcTemplate (wie in Issue #473) scheidet hier aus: er
  // würde die Sperr-Abfrage auf ein zweites JDBC-Template umhängen, ohne etwas sicherer zu machen.
  @SuppressWarnings("java:S2077")
  @Override
  public void lockColumnPositions(List<Long> columnIds) {
    List<Long> ordered = columnIds.stream().distinct().sorted().toList();
    String placeholders = String.join(", ", Collections.nCopies(ordered.size(), "?"));
    jdbc.queryForList(
        "SELECT id FROM board_column WHERE id IN (" + placeholders + ") ORDER BY id FOR UPDATE",
        Long.class,
        ordered.toArray());
  }

  @Override
  public void move(long cardId, long newColumnId, int newPosition) {
    entityManager.flush();

    Long oldColumnId = columnIdOf(cardId);
    if (oldColumnId == null) {
      return;
    }
    // Quelle und Ziel in EINEM sortierten Aufruf sperren — erst danach steht der Reindex beider
    // Spalten unter Kontrolle. Der Nachlesen-Schritt deckt den Fall ab, dass die Karte die
    // Quellspalte verlassen hat, während wir auf die Sperren warteten.
    lockColumnPositions(List.of(oldColumnId, newColumnId));
    requireStillIn(cardId, oldColumnId);

    List<Long> targetActive = activeCardIds(newColumnId, cardId);
    int index = Math.clamp(newPosition, 0, targetActive.size());
    List<Long> targetOrder = new ArrayList<>(targetActive);
    targetOrder.add(index, cardId);

    List<Long> sourceOrder = oldColumnId == newColumnId ? null : activeCardIds(oldColumnId, cardId);

    // Phase 1 — parken: die verschobene Karte auf einen eindeutigen Temp-Platz in der
    // Zielspalte, alle anderen aktiven Karten der betroffenen Spalten weit nach oben.
    jdbc.update(
        "UPDATE card SET column_id = ?, position_in_column = ? WHERE id = ?",
        newColumnId,
        PARK_MOVED_CARD,
        cardId);
    jdbc.update(
        "UPDATE card SET position_in_column = position_in_column + ? "
            + "WHERE archived = false AND idea_stored = false AND type <> 'EPIC' "
            + "AND id <> ? AND column_id IN (?, ?)",
        PARK_OFFSET,
        cardId,
        oldColumnId,
        newColumnId);

    // Phase 2 — finale, lückenlose Positionen (jeweils < PARK_OFFSET, kollisionsfrei).
    if (sourceOrder != null) {
      assignPositions(sourceOrder);
    }
    assignPositions(targetOrder);

    // Direkt-SQL umging den JPA-Kontext -> Cache leeren, damit Folge-Reads frisch sind.
    entityManager.clear();
  }

  // Sonar java:S2077 (Issue #508): Konkateniert werden nur das Literal des Prädikats
  // ACTIVE_NAMESPACE und das Ergebnis von orderKeyword — einer über das geschlossene Enum
  // SortDirection erschöpfenden Abbildung auf genau zwei SQL-Schlüsselwörter. Es gibt keinen Pfad,
  // auf dem ein Aufrufer eigene Zeichen in den SQL-Text bringt; Spalten-ID und Werte sind
  // Bindeparameter. SQL kennt für ASC/DESC keine Bindung, ein Umbau auf Parameter scheidet daher
  // aus. Der erschöpfende switch statt eines Ternärs macht die Vollständigkeit der Abbildung zur
  // Compiler-Bedingung: Ein neuer Enum-Wert bricht den Build, statt still auf DESC zu fallen.
  @SuppressWarnings("java:S2077")
  @Override
  public void sortActiveByNumber(long columnId, SortDirection direction) {
    entityManager.flush();

    // Erst sperren, dann lesen: die neue Ordnung entsteht aus dem gelesenen Bestand (#499).
    lockColumnPositions(List.of(columnId));

    List<Long> sorted =
        jdbc.queryForList(
            "SELECT id FROM card WHERE column_id = ? "
                + ACTIVE_NAMESPACE
                + " ORDER BY number "
                + orderKeyword(direction),
            Long.class,
            columnId);

    // Phase 1 — parken: alle betroffenen Karten weit außerhalb des realen Bereichs, damit die
    // finale Vergabe nicht transient mit einer noch belegten Position kollidiert.
    jdbc.update(
        "UPDATE card SET position_in_column = position_in_column + ? WHERE column_id = ? "
            + ACTIVE_NAMESPACE,
        PARK_OFFSET,
        columnId);

    // Phase 2 — finale, lückenlose Positionen 0..n-1 in der gewünschten Reihenfolge.
    assignPositions(sorted);

    // Direkt-SQL umging den JPA-Kontext -> Cache leeren, damit Folge-Reads frisch sind.
    entityManager.clear();
  }

  @Override
  public void transfer(long cardId, long targetBoardId, long targetColumnId, int newNumber) {
    entityManager.flush();

    Long oldColumnId = columnIdOf(cardId);
    if (oldColumnId == null) {
      return;
    }
    lockColumnPositions(List.of(oldColumnId, targetColumnId));
    requireStillIn(cardId, oldColumnId);

    // Ans Ende der Zielspalte (hinter deren aktive Karten) — kollisionsfrei zum
    // active_position-Unique.
    int endPosition = activeCardIds(targetColumnId, cardId).size();
    // project_id muss mit dem Ziel-Board wandern (bei board-/projektübergreifendem Umzug): sonst
    // bliebe project_id am Quellprojekt und die projektweite Nummer (uq (project_id, number)) würde
    // gegen die falsche Menge geprüft.
    jdbc.update(
        "UPDATE card SET board_id = ?, column_id = ?, number = ?, position_in_column = ?, "
            + "project_id = (SELECT project_id FROM board WHERE id = ?) WHERE id = ?",
        targetBoardId,
        targetColumnId,
        newNumber,
        endPosition,
        targetBoardId,
        cardId);

    // Quellspalte lückenlos nachziehen (die verschobene Karte ist dort nicht mehr enthalten).
    assignPositions(activeCardIds(oldColumnId, cardId));

    entityManager.clear();
  }

  /**
   * Übersetzt die Sortierrichtung in das SQL-Schlüsselwort. Erschöpfender Switch über ein
   * geschlossenes Enum: Der Compiler erzwingt für jede Richtung eine eigene, literale Entsprechung.
   * Es gibt damit keinen Zweig, über den etwas anderes als die beiden Schlüsselwörter in den
   * SQL-Text gelangen kann — Grundlage der S2077-Bewertung an {@link #sortActiveByNumber}.
   */
  private static String orderKeyword(SortDirection direction) {
    return switch (direction) {
      case ASC -> "ASC";
      case DESC -> "DESC";
    };
  }

  private @Nullable Long columnIdOf(long cardId) {
    return jdbc.queryForObject("SELECT column_id FROM card WHERE id = ?", Long.class, cardId);
  }

  /**
   * Stellt sicher, dass die Karte noch in der Spalte liegt, für die die Sperren genommen wurden.
   * Andernfalls deckt die Sperrmenge den tatsächlichen Umzug nicht ab — nachträglich erweitern
   * würde die Sortierordnung und damit die Deadlock-Freiheit brechen, also bricht der Aufruf ab.
   */
  private void requireStillIn(long cardId, long expectedColumnId) {
    if (!Long.valueOf(expectedColumnId).equals(columnIdOf(cardId))) {
      throw new CardMovedConcurrentlyException();
    }
  }

  private List<Long> activeCardIds(long columnId, long excludeCardId) {
    return jdbc.queryForList(
        "SELECT id FROM card WHERE column_id = ? AND archived = false AND idea_stored = false "
            + "AND type <> 'EPIC' AND id <> ? ORDER BY position_in_column",
        Long.class,
        columnId,
        excludeCardId);
  }

  private void assignPositions(List<Long> orderedIds) {
    for (int i = 0; i < orderedIds.size(); i++) {
      jdbc.update("UPDATE card SET position_in_column = ? WHERE id = ?", i, orderedIds.get(i));
    }
  }

  @Override
  public void softDelete(long cardId, java.time.Instant when) {
    jdbc.update(
        "UPDATE card SET deleted_at = ? WHERE id = ?", java.sql.Timestamp.from(when), cardId);
  }

  @Override
  public void restoreFromTrash(long cardId, int newPosition) {
    jdbc.update(
        "UPDATE card SET deleted_at = NULL, position_in_column = ? WHERE id = ?",
        newPosition,
        cardId);
  }

  @Override
  public void deleteById(long id) {
    jpa.deleteById(id);
  }

  private static CardEntity toEntity(Card c) {
    return new CardEntity(c);
  }

  private static Card toDomain(CardEntity e) {
    return new Card(
        e.getId(),
        e.getBoardId(),
        e.getColumnId(),
        e.getNumber(),
        e.getTitle(),
        e.getDescription(),
        e.getPositionInColumn(),
        e.isArchived(),
        e.isIdeaStored(),
        e.getMovedToDoneAt(),
        e.getCreatedBy(),
        e.getCreatedAt(),
        e.getUpdatedAt(),
        CardType.valueOf(e.getType()),
        e.getParentId(),
        e.getShortcode(),
        e.getDueDate(),
        e.getProjectId(),
        e.getTargetBoardId());
  }
}
