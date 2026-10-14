package org.mwolff.manban.kanbancompat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.attachment.application.AttachmentNotFoundException;
import org.mwolff.manban.attachment.application.AttachmentService;
import org.mwolff.manban.attachment.application.AttachmentService.AttachmentView;
import org.mwolff.manban.attachment.application.AttachmentService.Download;
import org.mwolff.manban.card.application.CardIngestService;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Unit-Tests des lesenden Anhangzugangs der Kanban-Strecke (Plan #1694). */
class KanbanAnhangServiceTest {

  private static final long BOARD = 10L;
  private static final long USER = 1L;
  private static final long CARD = 812L;
  private static final long ANHANG = 91L;

  private CardIngestService ingest;
  private AttachmentService attachments;
  private KanbanAnhangService service;

  private static KanbanPrincipal bound() {
    return new KanbanPrincipal(USER, 2L, 5L, BOARD, "Token");
  }

  private static KanbanPrincipal unbound() {
    return new KanbanPrincipal(USER, 2L, null, null, "Token");
  }

  @BeforeEach
  void setUp() {
    ingest = mock(CardIngestService.class);
    attachments = mock(AttachmentService.class);
    service = new KanbanAnhangService(ingest, attachments);
  }

  @Test
  void listOhneBindungWirftTokenNotBoundOhneWeitereAufrufe() {
    assertThatThrownBy(() -> service.list(unbound(), CARD))
        .isInstanceOf(TokenNotBoundException.class);
    verifyNoInteractions(ingest, attachments);
  }

  @Test
  void downloadOhneBindungWirftTokenNotBoundOhneWeitereAufrufe() {
    assertThatThrownBy(() -> service.download(unbound(), CARD, ANHANG))
        .isInstanceOf(TokenNotBoundException.class);
    verifyNoInteractions(ingest, attachments);
  }

  @Test
  void listPrueftDieBoardGrenzeUndLiefertDieAnhaengeDerKarte() {
    List<AttachmentView> erwartet =
        List.of(
            new AttachmentView(
                ANHANG,
                CARD,
                "skizze.png",
                "image/png",
                48_213L,
                Instant.parse("2026-10-01T08:00:00Z")));
    when(attachments.list(USER, CARD)).thenReturn(erwartet);

    assertThat(service.list(bound(), CARD)).isSameAs(erwartet);
    verify(ingest).requireReadableOnBoard(CARD, BOARD);
  }

  @Test
  void downloadPrueftDieBoardGrenzeUndLiefertDenAnhangDerKarte() {
    Download erwartet =
        new Download("skizze.png", "image/png", 3L, new ByteArrayInputStream(new byte[3]));
    when(attachments.downloadOnCard(USER, CARD, ANHANG)).thenReturn(erwartet);

    assertThat(service.download(bound(), CARD, ANHANG)).isSameAs(erwartet);
    verify(ingest).requireReadableOnBoard(CARD, BOARD);
  }

  @Test
  void listAufUnerreichbarerKarteAntwortetWieEinUnbekannterAnhang() {
    doThrow(new CardNotFoundException()).when(ingest).requireReadableOnBoard(CARD, BOARD);

    assertThatThrownBy(() -> service.list(bound(), CARD))
        .isInstanceOf(AttachmentNotFoundException.class)
        .hasMessage(new AttachmentNotFoundException().getMessage())
        .hasCauseInstanceOf(CardNotFoundException.class);
    verifyNoInteractions(attachments);
  }

  @Test
  void downloadAufUnerreichbarerKarteAntwortetWieEinUnbekannterAnhang() {
    doThrow(new CardNotFoundException()).when(ingest).requireReadableOnBoard(CARD, BOARD);

    assertThatThrownBy(() -> service.download(bound(), CARD, ANHANG))
        .isInstanceOf(AttachmentNotFoundException.class)
        .hasMessage(new AttachmentNotFoundException().getMessage())
        .hasCauseInstanceOf(CardNotFoundException.class);
    verifyNoInteractions(attachments);
  }

  @Test
  void listOhneMitgliedschaftDesBesitzersAntwortetWieEinUnbekannterAnhang() {
    when(attachments.list(USER, CARD)).thenThrow(new ProjectNotFoundException());

    assertThatThrownBy(() -> service.list(bound(), CARD))
        .isInstanceOf(AttachmentNotFoundException.class)
        .hasCauseInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void downloadOhneMitgliedschaftDesBesitzersAntwortetWieEinUnbekannterAnhang() {
    when(attachments.downloadOnCard(USER, CARD, ANHANG)).thenThrow(new ProjectNotFoundException());

    assertThatThrownBy(() -> service.download(bound(), CARD, ANHANG))
        .isInstanceOf(AttachmentNotFoundException.class)
        .hasCauseInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void unbekannterAnhangWirdUnveraendertDurchgereicht() {
    AttachmentNotFoundException unbekannt = new AttachmentNotFoundException();
    when(attachments.downloadOnCard(USER, CARD, ANHANG)).thenThrow(unbekannt);

    assertThatThrownBy(() -> service.download(bound(), CARD, ANHANG)).isSameAs(unbekannt);
  }
}
