package org.mwolff.manban.kanbancompat.application;

import static org.mwolff.manban.kanbancompat.application.KanbanBindung.requireBound;

import java.util.List;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.attachment.application.AttachmentNotFoundException;
import org.mwolff.manban.attachment.application.AttachmentService;
import org.mwolff.manban.attachment.application.AttachmentService.AttachmentView;
import org.mwolff.manban.attachment.application.AttachmentService.Download;
import org.mwolff.manban.card.application.CardIngestService;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Lesender Zugang der Kanban-Strecke zu den Anhängen einer Karte des gebundenen Boards (Plan
 * #1694). Ein eigener Service nach dem Vorbild von {@link KanbanArchivService}.
 *
 * <p>Reichweite wie die übrigen Leseaufrufe: nur mit gebundenem Token, nur Karten des gebundenen
 * Boards, Papierkorb unsichtbar. Eine Karte außerhalb dieser Grenze und ein Token-Besitzer, der
 * kein Mitglied mehr ist, enden wie ein unbekannter Anhang, damit das Token nicht erfährt, ob Karte
 * oder Anhang existieren. Kein Schreibzugriff, kein Verlaufseintrag (A10).
 */
@Service
public class KanbanAnhangService {

  private final CardIngestService ingest;
  private final AttachmentService attachments;

  public KanbanAnhangService(CardIngestService ingest, AttachmentService attachments) {
    this.ingest = ingest;
    this.attachments = attachments;
  }

  /** Die Anhänge einer Karte des gebundenen Boards. */
  public List<AttachmentView> list(KanbanPrincipal principal, long cardId) {
    long boardId = requireBound(principal);
    try {
      ingest.requireReadableOnBoard(cardId, boardId);
      return attachments.list(principal.userId(), cardId);
    } catch (CardNotFoundException | ProjectNotFoundException e) {
      throw unerreichbar(e);
    }
  }

  /** Ein Anhang einer Karte des gebundenen Boards, über die Karte adressiert. */
  public Download download(KanbanPrincipal principal, long cardId, long attachmentId) {
    long boardId = requireBound(principal);
    try {
      ingest.requireReadableOnBoard(cardId, boardId);
      return attachments.downloadOnCard(principal.userId(), cardId, attachmentId);
    } catch (CardNotFoundException | ProjectNotFoundException e) {
      throw unerreichbar(e);
    }
  }

  /**
   * Dieselbe Antwort wie für einen unbekannten Anhang; die Ursache bleibt nur fürs Protokoll
   * erhalten.
   */
  private static AttachmentNotFoundException unerreichbar(RuntimeException ursache) {
    AttachmentNotFoundException unbekannt = new AttachmentNotFoundException();
    unbekannt.initCause(ursache);
    return unbekannt;
  }
}
