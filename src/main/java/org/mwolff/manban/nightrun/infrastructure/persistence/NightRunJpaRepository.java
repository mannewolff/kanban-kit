package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring-Data-Repository für {@link NightRunEntity} (Lesepfad). */
interface NightRunJpaRepository extends JpaRepository<NightRunEntity, Long> {

  /**
   * Läufe einer Gattung in einem Projekt, jüngster Startzeitpunkt zuerst.
   *
   * <p>Tie-Break auf der ID wie bei {@code CommentRepository#findByCardId} (#472): Zwei Läufe
   * können denselben Startzeitpunkt nur in verschiedenen Projekten tragen — die Reihenfolge liegt
   * trotzdem fest, statt von der physischen Zeilenlage abzuhängen.
   *
   * <p>Die Gattung kommt als {@link String} und nicht als Enum: Die Spalte ist ein {@code varchar}
   * + {@code CHECK} (V34), und die Entity bildet sie als {@code String} ab (Issue #1012).
   */
  List<NightRunEntity> findByProjectIdAndKindOrderByStartedAtDescIdDesc(
      long projectId, String kind);

  Optional<NightRunEntity> findByIdAndProjectId(long id, long projectId);

  /**
   * Die Nachtläufe eines Tokens, die das Fenster {@code [von, bis]} berühren (Issue #1373). Natives
   * SQL, weil das Ende eines gemeldeten Laufs aus Start und Dauer in Millisekunden entsteht.
   */
  @Query(
      value =
          "SELECT * FROM night_run WHERE project_id = :projectId AND kind = 'NIGHT'"
              + " AND token_name = :tokenName AND started_at <= :bis"
              + " AND (complete = false"
              + " OR started_at + duration_ms * INTERVAL '1 millisecond' >= :von)"
              + " ORDER BY started_at, id",
      nativeQuery = true)
  List<NightRunEntity> findOverlapping(
      @Param("projectId") long projectId,
      @Param("tokenName") String tokenName,
      @Param("von") Instant von,
      @Param("bis") Instant bis);
}
