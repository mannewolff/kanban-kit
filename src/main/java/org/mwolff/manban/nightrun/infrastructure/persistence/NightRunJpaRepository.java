package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
