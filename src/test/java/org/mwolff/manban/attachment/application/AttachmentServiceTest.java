package org.mwolff.manban.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.mockito.InOrder;
import org.mwolff.manban.attachment.domain.Attachment;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.project.domain.Permission;

/** Verhaltenstests der Anhang-Use-Cases (Mockito an den Ports). */
class AttachmentServiceTest {

  private static final Instant FIXED = Instant.parse("2026-01-02T03:04:05Z");

  private AttachmentRepository attachments;
  private ObjectStorage storage;
  private ContentTypeDetector detector;
  private ObjectStorageProperties properties;
  private CardService cardService;
  private PermissionChecker permissions;
  private BlobDeletionScheduler blobDeletion;
  private AttachmentService service;

  private static Attachment attachment() {
    return new Attachment(7L, 5L, "note.txt", "text/plain", 3, "cards/5/key", FIXED);
  }

  @BeforeEach
  void setUp() {
    attachments = mock(AttachmentRepository.class);
    storage = mock(ObjectStorage.class);
    detector = mock(ContentTypeDetector.class);
    properties = new ObjectStorageProperties(null, null, null, null, 20);
    cardService = mock(CardService.class);
    permissions = mock(PermissionChecker.class);
    blobDeletion = mock(BlobDeletionScheduler.class);
    Clock clock = Clock.fixed(FIXED, ZoneOffset.UTC);
    service =
        new AttachmentService(
            attachments,
            storage,
            detector,
            properties,
            cardService,
            permissions,
            blobDeletion,
            clock);
  }

  private void cardResolves() {
    when(cardService.requireProjectId(5L)).thenReturn(1L);
  }

  @Test
  void upload_setsCreatedAtFromInjectedClock() {
    // Given
    cardResolves();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

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
    cardResolves();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("image/png");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

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
    cardResolves();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));
    byte[] content = {1, 2, 3};

    // When
    service.upload(1L, 5L, "note.txt", content);

    // Then
    verify(storage).put(any(), eq(content), eq("text/plain"));
  }

  @Test
  void upload_savesMetadataBeforePuttingTheBlob() {
    // Given
    cardResolves();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    service.upload(1L, 5L, "note.txt", new byte[] {1, 2, 3});

    // Then — Reihenfolge ist die Kernzusage aus Issue #503: erst die Metadaten (rollbar), der
    // Blob-Put als letzter Schritt vor dem Commit.
    InOrder inOrder = inOrder(attachments, storage);
    inOrder.verify(attachments).save(any(Attachment.class));
    inOrder.verify(storage).put(any(), any(), any());
  }

  @Test
  void upload_leavesNoBlobBehind_whenMetadataInsertFails() {
    // Given — der Insert scheitert (z. B. Constraint).
    cardResolves();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenThrow(new IllegalStateException("insert"));

    // When / Then — kein Blob wurde geschrieben, nichts verwaist (Issue #503).
    assertThatThrownBy(() -> service.upload(1L, 5L, "note.txt", new byte[] {1}))
        .isInstanceOf(IllegalStateException.class);
    verify(storage, never()).put(any(), any(), any());
  }

  @Test
  void upload_returnsViewOfPersistedAttachment() {
    // Given
    cardResolves();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    AttachmentService.AttachmentView view =
        service.upload(1L, 5L, "note.txt", new byte[] {1, 2, 3});

    // Then
    assertThat(view.filename()).isEqualTo("note.txt");
  }

  @Test
  void upload_requiresAttachmentCreatePermission() {
    // Given
    cardResolves();
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

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
            cardService,
            permissions,
            blobDeletion,
            Clock.fixed(FIXED, ZoneOffset.UTC));
    cardResolves();
    when(attachments.countByCardId(5L)).thenReturn(1L);

    // When / Then
    assertThatThrownBy(() -> service.upload(1L, 5L, "note.txt", new byte[] {1}))
        .isInstanceOf(AttachmentLimitExceededException.class);
  }

  @Test
  void upload_throwsCardNotFound_whenCardUnknown() {
    // Given: die card-Fassade meldet die unbekannte Karte — der Anhang-Service reicht sie durch.
    when(cardService.requireProjectId(5L)).thenThrow(new CardNotFoundException());

    // When / Then
    assertThatThrownBy(() -> service.upload(1L, 5L, "note.txt", new byte[] {1}))
        .isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void upload_checksPermissionAgainstProjectFromCardFacade() {
    // Given: die Projekt-ID kommt seit #458 ausschliesslich aus der card-Fassade (vorher aus dem
    // Board der Karte). Eine abweichende ID belegt, dass sie wirklich durchgereicht und nicht
    // anderweitig hergeleitet wird — und dass Anhaenge damit auch an board-losen Pool-Ideen
    // (#405) rechtegeprueft werden koennen.
    when(cardService.requireProjectId(5L)).thenReturn(42L);
    when(attachments.countByCardId(5L)).thenReturn(0L);
    when(detector.detect(any(), any())).thenReturn("text/plain");
    when(attachments.save(any(Attachment.class))).thenAnswer(inv -> saved(inv.getArgument(0)));

    // When
    service.upload(1L, 5L, "note.txt", new byte[] {1, 2, 3});

    // Then
    verify(permissions).require(1L, 42L, Permission.ATTACHMENT_CREATE);
  }

  @Test
  void list_mapsAttachmentsToViews() {
    // Given
    cardResolves();
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
    cardResolves();
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
  void delete_schedulesBlobRemovalInsteadOfDeletingDirectly() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.of(attachment()));
    cardResolves();

    // When
    service.delete(1L, 7L);

    // Then — der Blob wird über die Outbox nach dem Commit entfernt (Issue #503): Ein direktes
    // Löschen vor einem scheiternden DB-Teil hinterließe einen kaputten Download-Verweis.
    verify(blobDeletion).scheduleDelete("cards/5/key");
    verify(storage, never()).delete(any());
  }

  @Test
  void delete_removesMetadataViaRepository() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.of(attachment()));
    cardResolves();

    // When
    service.delete(1L, 7L);

    // Then
    verify(attachments).deleteById(7L);
  }

  @Test
  void delete_requiresAttachmentDeletePermission() {
    // Given
    when(attachments.findById(7L)).thenReturn(Optional.of(attachment()));
    cardResolves();

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

  /** Simuliert die DB: vergibt beim ersten Speichern eine ID (Issue #0080). */
  private static Attachment saved(Attachment a) {
    if (a.id() != null) {
      return a;
    }
    return new Attachment(
        7L, a.cardId(), a.filename(), a.contentType(), a.size(), a.objectKey(), a.createdAt());
  }
}
