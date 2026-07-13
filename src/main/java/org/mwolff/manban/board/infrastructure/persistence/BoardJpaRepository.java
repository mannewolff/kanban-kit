package org.mwolff.manban.board.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link BoardEntity}. */
interface BoardJpaRepository extends JpaRepository<BoardEntity, Long> {

  /** Aktives Board (nicht archiviert). Das eingebaute {@code findById} schließt Archivierte ein. */
  Optional<BoardEntity> findByIdAndArchivedAtIsNull(Long id);

  /** Aktive Boards des Projekts. */
  List<BoardEntity> findByProjectIdAndArchivedAtIsNullOrderByCreatedAt(Long projectId);

  /** Archivierte Boards des Projekts. */
  List<BoardEntity> findByProjectIdAndArchivedAtIsNotNullOrderByCreatedAt(Long projectId);
}
