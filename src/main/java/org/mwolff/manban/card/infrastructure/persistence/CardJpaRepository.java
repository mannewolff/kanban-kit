package org.mwolff.manban.card.infrastructure.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Spring-Data-Repository für {@link CardEntity}. */
interface CardJpaRepository extends JpaRepository<CardEntity, Long> {

  /** Aktive (nicht gelöschte) Karten des Boards. */
  List<CardEntity> findByBoardIdAndDeletedAtIsNullOrderByNumber(Long boardId);

  /**
   * IDs aller Karten des Boards — bewusst ohne jeden Filter (auch archivierte und
   * Papierkorb-Karten), exakt der Umfang der Datenbank-Cascade (Issue #503).
   */
  @Query("select c.id from CardEntity c where c.boardId = ?1")
  List<Long> findAllIdsByBoardId(long boardId);

  /**
   * Boards mit mindestens einem Vorhaben — archivierte eingeschlossen, Papierkorb-Vorhaben nicht
   * (Plan #1504, E6).
   */
  @Query(
      "select distinct c.boardId from CardEntity c where c.type = 'EPIC' and c.deletedAt is null")
  List<Long> findBoardIdsWithEpics();

  List<CardEntity> findByProjectIdAndDeletedAtIsNull(Long projectId);

  // Herkunfts-Kinder: bewusst OHNE DeletedAtIsNull-Filter. Beim Aufraeumen des
  // Projektwechsels muessen auch archivierte und geloeschte Kinder ihren Verweis verlieren.
  List<CardEntity> findByDerivedFromCardId(Long cardId);

  List<CardEntity> findByRequirementCardId(Long cardId);

  /** Nicht-gelöschte Karte eines Projekts nach projektweiter Nummer (projektweit eindeutig). */
  Optional<CardEntity> findByProjectIdAndNumberAndDeletedAtIsNull(Long projectId, Integer number);

  /**
   * Nur die vorhandenen Nummern — Projektion über die Spalten {@code card.project_id} und {@code
   * card.number}, ohne Karteninhalte (Issue #1169). Derselbe Lösch-Filter wie {@link
   * #findByProjectIdAndNumberAndDeletedAtIsNull}: archivierte Karten zählen mit, Papierkorb-Karten
   * nicht.
   */
  @Query(
      "select c.number from CardEntity c where c.projectId = ?1 and c.number in ?2 "
          + "and c.deletedAt is null")
  List<Integer> findExistingNumbers(Long projectId, Collection<Integer> numbers);

  // Bewusst ohne DeletedAt-Filter: der Idempotenz-Check (#534) sieht auch Papierkorb-Karten.
  Optional<CardEntity> findByProjectIdAndExternalKey(Long projectId, String externalKey);

  /**
   * Nicht-gelöschte Karten mit dieser Nummer aus den angegebenen Projekten, aufsteigend nach
   * Projekt-ID (stabile Trefferreihenfolge für die projektübergreifende Suche, #489).
   */
  List<CardEntity> findByNumberAndProjectIdInAndDeletedAtIsNullOrderByProjectId(
      Integer number, List<Long> projectIds);

  /** Karten im Papierkorb des Boards. */
  List<CardEntity> findByBoardIdAndDeletedAtIsNotNullOrderByNumber(Long boardId);

  /** Karten, die vor dem Zeitpunkt gelöscht wurden (Papierkorb-Retention). */
  List<CardEntity> findByDeletedAtNotNullAndDeletedAtBefore(Instant threshold);

  @Query(
      "select c from CardEntity c where c.archived = false and c.deletedAt is null "
          + "and c.movedToDoneAt is not null and c.movedToDoneAt < ?1")
  List<CardEntity> findArchivableDoneCards(Instant threshold);

  @Query(
      "select coalesce(max(c.positionInColumn), -1) from CardEntity c "
          + "where c.columnId = ?1 and c.archived = false "
          + "and c.deletedAt is null and c.type <> 'EPIC'")
  int maxActivePositionInColumn(Long columnId);

  /**
   * Karten-ID und verlorene Herkunftsnummer der Karten des Boards (Plan #1574, E12/E17). Die Spalte
   * {@code derived_from_lost_number} bildet {@link CardEntity} bewusst nicht ab, darum nativ.
   */
  @Query(
      value =
          "select id, derived_from_lost_number from card where board_id = ?1 "
              + "and derived_from_lost_number is not null and derived_from_card_id is null",
      nativeQuery = true)
  List<Object[]> findVerloreneHerkunftsnummern(long boardId);

  /**
   * Karten-ID und Fundstelle ({@code TITLE} vor {@code BODY}) des Suchbegriffs in den Karten des
   * Boards ohne Papierkorb (Plan #1574, E3, E4). {@code strpos} statt {@code LIKE}, damit {@code %}
   * und {@code _} wörtlich gelten; der Suchbegriff geht nur als gebundener Parameter.
   */
  @Query(
      value =
          "select id, case when strpos(lower(title), lower(?2)) > 0 then 'TITLE' else 'BODY' end "
              + "from card where board_id = ?1 and deleted_at is null "
              + "and (strpos(lower(title), lower(?2)) > 0 "
              + "or strpos(lower(coalesce(description, '')), lower(?2)) > 0)",
      nativeQuery = true)
  List<Object[]> findTextFundstellen(long boardId, String q);

  /**
   * Schreibt an alle Kinder der genannten Karten die Nummer des Vorfahren (Plan #1574, E18). Vorher
   * flushen: Eine im selben Persistenzkontext gesetzte Herkunft muss in der Tabelle stehen.
   */
  @Transactional
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "update card child set derived_from_lost_number = parent.number from card parent "
              + "where child.derived_from_card_id = parent.id and parent.id in (?1)",
      nativeQuery = true)
  void bewahreHerkunftsnummern(Collection<Long> ids);

  /** Setzt die verlorene Herkunftsnummer einer Karte zurück, deren Herkunft neu gesetzt ist. */
  @Transactional
  @Modifying
  @Query(
      value =
          "update card set derived_from_lost_number = null "
              + "where id = ?1 and derived_from_lost_number is not null",
      nativeQuery = true)
  void setzeVerloreneHerkunftsnummerZurueck(long id);
}
