package org.mwolff.manban.comment.infrastructure.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring-Data-Repository für {@link CommentEntity}. */
interface CommentJpaRepository extends JpaRepository<CommentEntity, Long> {

    List<CommentEntity> findByCardIdOrderByCreatedAt(Long cardId);
}
