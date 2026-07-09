package org.mwolff.manban.comment.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.comment.application.CommentRepository;
import org.mwolff.manban.comment.domain.Comment;
import org.springframework.stereotype.Component;

/** Adapter des {@link CommentRepository}-Ports auf Spring Data JPA. */
@Component
class CommentRepositoryAdapter implements CommentRepository {

    private final CommentJpaRepository jpa;

    CommentRepositoryAdapter(CommentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Comment save(Comment comment) {
        return toDomain(jpa.save(toEntity(comment)));
    }

    @Override
    public Optional<Comment> findById(long id) {
        return jpa.findById(id).map(CommentRepositoryAdapter::toDomain);
    }

    @Override
    public List<Comment> findByCardId(long cardId) {
        return jpa.findByCardIdOrderByCreatedAt(cardId).stream().map(CommentRepositoryAdapter::toDomain).toList();
    }

    @Override
    public void deleteById(long id) {
        jpa.deleteById(id);
    }

    private static CommentEntity toEntity(Comment c) {
        return new CommentEntity(c.id(), c.cardId(), c.authorUserId(), c.authorName(), c.body(),
                c.createdAt(), c.updatedAt());
    }

    private static Comment toDomain(CommentEntity e) {
        return new Comment(e.getId(), e.getCardId(), e.getAuthorUserId(), e.getAuthorName(), e.getBody(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
