package org.mwolff.manban.comment.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.comment.domain.Comment;

/** Ausgehender Port für die Persistenz von Kommentaren. */
public interface CommentRepository {

    Comment save(Comment comment);

    Optional<Comment> findById(long id);

    List<Comment> findByCardId(long cardId);

    void deleteById(long id);
}
