package org.mwolff.manban.attachment.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring-Data-Repository für {@link AttachmentEntity}. */
interface AttachmentJpaRepository extends JpaRepository<AttachmentEntity, Long> {

  List<AttachmentEntity> findByCardIdOrderByCreatedAt(Long cardId);

  List<AttachmentEntity> findByCardIdIn(Collection<Long> cardIds);

  long countByCardId(Long cardId);

  @Query("select a.objectKey from AttachmentEntity a")
  List<String> findAllObjectKeys();
}
