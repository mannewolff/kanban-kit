package org.mwolff.manban.backup.infrastructure.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring-Data-Repository für {@link BackupRunEntity} (Lesepfad).
 *
 * <p>Tie-Break auf der ID wie bei {@code NightRunJpaRepository}: Zwei Läufe derselben Art können
 * denselben Startzeitpunkt tragen — die Reihenfolge liegt trotzdem fest, statt von der physischen
 * Zeilenlage abzuhängen.
 */
interface BackupRunJpaRepository extends JpaRepository<BackupRunEntity, Long> {

  Optional<BackupRunEntity> findFirstByKindOrderByStartedAtDescIdDesc(String kind);

  Optional<BackupRunEntity> findFirstByKindAndOutcomeOrderByStartedAtDescIdDesc(
      String kind, String outcome);
}
