package org.mwolff.manban.card.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mwolff.manban.card.application.CardRepository;
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
  public List<Card> findByBoardId(long boardId) {
    return jpa.findByBoardIdAndDeletedAtIsNullOrderByNumber(boardId).stream()
        .map(CardRepositoryAdapter::toDomain)
        .toList();
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
        .findByProjectIdAndIdeaStoredTrueAndDeletedAtIsNullOrderByCreatedAtDesc(projectId)
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
  public int highestNumberInProject(long projectId) {
    return java.util.Objects.requireNonNull(
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(number), 0) FROM card WHERE project_id = ?",
            Integer.class,
            projectId));
  }

  @Override
  public int maxActivePositionInColumn(long columnId) {
    return jpa.maxActivePositionInColumn(columnId);
  }

  @Override
  public void move(long cardId, long newColumnId, int newPosition) {
    entityManager.flush();

    Long oldColumnId =
        jdbc.queryForObject("SELECT column_id FROM card WHERE id = ?", Long.class, cardId);
    if (oldColumnId == null) {
      return;
    }

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

  @Override
  public void transfer(long cardId, long targetBoardId, long targetColumnId, int newNumber) {
    entityManager.flush();

    Long oldColumnId =
        jdbc.queryForObject("SELECT column_id FROM card WHERE id = ?", Long.class, cardId);
    if (oldColumnId == null) {
      return;
    }

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
