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

  /**
   * Je Karte des Projekts der Kommentar, dessen Text mit {@code ## Laufstand} beginnt (Issue #1373,
   * Plan #1372 E5). Tragen mehrere Kommentare einer Karte den Anker, gilt der jüngste nach
   * Erstellzeitpunkt, bei Gleichstand nach ID; sortiert nach Karten-ID.
   *
   * <p>{@code updatedAt} spielt keine Rolle: {@code CommentService.update} schreibt ihn nicht fort,
   * und das Kit ersetzt den Laufstand über genau diesen Weg.
   */
  List<Comment> findLaufstaendeImProjekt(long projectId);
}
