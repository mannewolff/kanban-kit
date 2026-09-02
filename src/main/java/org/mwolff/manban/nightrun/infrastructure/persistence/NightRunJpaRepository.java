package org.mwolff.manban.nightrun.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link NightRunEntity} (Lesepfad). */
interface NightRunJpaRepository extends JpaRepository<NightRunEntity, Long> {

  /**
   * Läufe eines Projekts, jüngster Startzeitpunkt zuerst.
   *
   * <p>Tie-Break auf der ID wie bei {@code CommentRepository#findByCardId} (#472): Zwei Läufe
   * können denselben Startzeitpunkt nur in verschiedenen Projekten tragen — die Reihenfolge liegt
   * trotzdem fest, statt von der physischen Zeilenlage abzuhängen.
   */
  List<NightRunEntity> findByProjectIdOrderByStartedAtDescIdDesc(long projectId);
}
