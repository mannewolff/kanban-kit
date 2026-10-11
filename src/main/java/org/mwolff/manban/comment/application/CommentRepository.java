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
   * Je Karte des Projekts der Kommentar, dessen Text mit {@code ## Laufstand} oder dem englischen
   * {@code ## Run status} beginnt (Issue #1373, Plan #1372 E5; Issue #1590, Plan #1582 E12). Tragen
   * mehrere Kommentare einer Karte den Anker, gilt der jüngste nach Erstellzeitpunkt, bei
   * Gleichstand nach ID; sortiert nach Karten-ID.
   *
   * <p>{@code updatedAt} spielt keine Rolle: {@code CommentService.update} schreibt ihn nicht fort,
   * und das Kit ersetzt den Laufstand über genau diesen Weg.
   */
  List<Comment> findLaufstaendeImProjekt(long projectId);

  /**
   * Je Karte des Boards der erste Kommentar (kleinste ID), dessen Text den Suchbegriff ohne
   * Rücksicht auf Groß- und Kleinschreibung enthält (Plan #1574, E3, E4, E16); sortiert nach
   * Karten-ID. Betrachtet werden nur Karten mit diesem Projekt und Board, archivierte
   * eingeschlossen, ohne Papierkorb. {@code %} und {@code _} gelten wörtlich.
   *
   * @param q Suchbegriff, getrimmt und nicht leer
   */
  List<Comment> findFirstMatchPerCardOnBoard(long projectId, long boardId, String q);
}
