package org.mwolff.manban.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.attachment.domain.Attachment;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.BoardRepository;
import org.mwolff.manban.board.domain.Board;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;

/** Verhaltenstests der Anhang-Use-Cases (Mockito an den Ports). */
class AttachmentServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AttachmentRepository attachments;
  private ObjectStorage storage;
  private ContentTypeDetector detector;
  private ObjectStorageProperties properties;
  private CardRepository cards;
  private BoardRepository boards;
  private PermissionChecker permissions;
  private AttachmentService service;

  private static Card card() {
    return new Card(
        5L, 10L, 20L, 1, "T", null, 0, false, null, 1L, FIXED, FIXED, CardType.CARD, null, null);
  }

  private static Attachment attachment() {
    return new Attachment(7L, 5L, "note.txt", "text/plain", 3, "cards/5/key", FIXED);
  }

  @BeforeEach
  void setUp() {
    attachments = mock(AttachmentRepository.class);
    storage = mock(ObjectStorage.class);
    detector = mock(ContentTypeDetector.class);
    properties = new ObjectStorageProperties(null, null, null, null, 20);
    cards = mock(CardRepository.class);
    boards = mock(BoardRepository.class);
    permissions = mock(PermissionChecker.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new AttachmentService(
            attachments, storage, detector, properties, cards, boards, permissions, clock);
  }

  private void cardAndBoardResolve() {
    when(cards.findById(5L)).thenReturn(Optional.of(card()));
    when(boards.findById(10L)).thenReturn(Optional.of(new Board(10L, 1L, "B", FIXED)));
  }

  @Test
  void upload_setsCreatedAtFromInjectedClock() {
    // Given
    cardAndBoardResolve();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
    service.upload(1L, 5L, "note.txt", new byte[] {1, 2, 3});

    // Then
    verify(attachments).save(captor.capture());
    assertThat(captor.getValue().createdAt()).isEqualTo(FIXED);
  }

  @Test
  void upload_storesBlobWithDetectedContentType() {
    // Given
    cardAndBoardResolve();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("image/png");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    ArgumentCaptor<Attachment> captor = ArgumentCaptor.forClass(Attachment.class);
    service.upload(1L, 5L, "logo.png", new byte[] {1, 2, 3});

    // Then
    verify(attachments).save(captor.capture());
    assertThat(captor.getValue().contentType()).isEqualTo("image/png");
  }

  @Test
  void upload_putsBlobIntoObjectStorage() {
    // Given
    cardAndBoardResolve();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));
    byte[] content = {1, 2, 3};

    // When
    service.upload(1L, 5L, "note.txt", content);

    // Then
    verify(storage).put(any(), eq(content), eq("text/plain"));
  }

  @Test
  void upload_returnsViewOfPersistedAttachment() {
    // Given
    cardAndBoardResolve();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    AttachmentService.AttachmentView view =
        service.upload(1L, 5L, "note.txt", new byte[] {1, 2, 3});

    // Then
    assertThat(view.filename()).isEqualTo("note.txt");
  }

  @Test
  void upload_requiresAttachmentCreatePermission() {
    // Given
    cardAndBoardResolve();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> inv.getArgument(0));

    // When
    service.upload(1L, 5L, "note.txt", new byte[] {1, 2, 3});

    // Then
    verify(permissions).require(1L, 1L, Permission.ATTACHMENT_CREATE);
  }

  @Test
  void upload_throwsLimitExceeded_whenCardAtCapacity() {
    // Given
    properties = new ObjectStorageProperties(null, null, null, null, 1);
    service =
        new AttachmentService(
            attachments,
            storage,
            detector,
            properties,
            cards,
            boards,
            permissions,
            Clock.fixed(FIXED, ZoneOffset.UTC));
    cardAndBoardResolve();
    when(attachments.countByCardId(5L)).thenReturn(1L);

    // When / Then
    assertThatThrownBy(() -> service.upload(1L, 5L, "note.txt", new byte[] {1}))
        .isInstanceOf(AttachmentLimitExceededException.class);
  }

  @Test
  void upload_throwsCardNotFound_whenCardUnknown() {
    // Given
    when(cards.findById(5L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.upload(1L, 5L, "note.txt", new byte[] {1}))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void upload_throwsBoardNotFound_whenBoardUnknown() {
    // Given
    when(cards.findById(5L)).thenReturn(Optional.of(card()));
    when(boards.findById(10L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.upload(1L, 5L, "note.txt", new byte[] {1}))
        .isInstanceOf(BoardNotFoundException.class);
  }

  @Test
  void list_mapsAttachmentsToViews() {
    // Given
    cardAndBoardResolve();
    when(attachments.findByCardId(5L)).thenReturn(List.of(attachment()));

    // When
    List<AttachmentService.AttachmentView> views = service.list(1L, 5L);

    // Then
    assertThat(views)
        .singleElement()
        .extracting(AttachmentService.AttachmentView::filename)
        .isEqualTo("note.txt");
  }

  @Test
  void download_returnsMetadataAndStream() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.of(attachment()));
    cardAndBoardResolve();
    when(storage.get("cards/5/key")).thenReturn(new ByteArrayInputStream(new byte[] {1}));

    // When
    AttachmentService.Download download = service.download(1L, 7L);

    // Then
    assertThat(download.filename()).isEqualTo("note.txt");
  }

  @Test
  void download_throwsNotFound_whenAttachmentUnknown() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.download(1L, 7L))
        .isInstanceOf(AttachmentNotFoundException.class);
  }

  @Test
  void delete_removesBlobAndMetadata() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.of(attachment()));
    cardAndBoardResolve();

    // When
    service.delete(1L, 7L);

    // Then
    verify(storage).delete("cards/5/key");
  }

  @Test
  void delete_removesMetadataViaRepository() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.of(attachment()));
    cardAndBoardResolve();

    // When
    service.delete(1L, 7L);

    // Then
    verify(attachments).deleteById(7L);
  }

  @Test
  void delete_requiresAttachmentDeletePermission() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.of(attachment()));
    cardAndBoardResolve();

    // When
    service.delete(1L, 7L);

    // Then
    verify(permissions).require(1L, 1L, Permission.ATTACHMENT_DELETE);
  }

  @Test
  void delete_throwsNotFound_whenAttachmentUnknown() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.empty());

    // When / Then
    assertThatThrownBy(() -> service.delete(1L, 7L))
        .isInstanceOf(AttachmentNotFoundException.class);
  }
}
