package org.mwolff.manban.attachment.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link AttachmentEntity}. */
interface AttachmentJpaRepository extends JpaRepository<AttachmentEntity, Long> {

  List<AttachmentEntity> findByCardIdOrderByCreatedAt(Long cardId);

  long countByCardId(Long cardId);
}
