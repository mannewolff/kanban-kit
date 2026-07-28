package org.mwolff.manban.comment.application;

import java.util.List;
import java.util.Optional;
import org.mwolff.manban.comment.domain.Comment;

/** Ausgehender Port für die Persistenz von Kommentaren. */
public interface CommentRepository {

  Comment save(Comment comment);

  Optional<Comment> findById(long id);

  /**
   * Kommentare der Karte in chronologischer Reihenfolge; bei identischem Erstellzeitpunkt
   * entscheidet die ID (#472), damit die Reihenfolge auch dann festliegt.
   */
  List<Comment> findByCardId(long cardId);

  void deleteById(long id);
}
