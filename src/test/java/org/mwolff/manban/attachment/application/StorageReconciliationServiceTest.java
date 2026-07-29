package org.mwolff.manban.attachment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminAccessDeniedException;
import org.mwolff.manban.auth.application.PlatformAdminChecker;

/** Abgleich zwischen Anhang-Metadaten und Objektspeicher (Issue #503). */
class StorageReconciliationServiceTest {

  private AttachmentRepository attachments;
  private ObjectStorage storage;
  private PlatformAdminChecker admins;
  private StorageReconciliationService service;

  @BeforeEach
  void setUp() {
    attachments = mock(AttachmentRepository.class);
    storage = mock(ObjectStorage.class);
    admins = mock(PlatformAdminChecker.class);
    service = new StorageReconciliationService(attachments, storage, admins);
  }

  @Test
  void report_findsOrphanedAndMissingObjects() {
    // Given — "beide" ist referenziert und vorhanden, "waise" nur im Speicher, "weg" nur in der DB.
    when(admins.isPlatformAdmin(1L)).thenReturn(true);
    when(storage.listKeys()).thenReturn(List.of("cards/1/beide", "cards/1/waise"));
    when(attachments.findAllObjectKeys()).thenReturn(List.of("cards/1/beide", "cards/2/weg"));

    // When
    StorageReconciliationService.ReconciliationReport report = service.report(1L);

    // Then
    assertThat(report.orphanedObjects()).containsExactly("cards/1/waise");
    assertThat(report.missingObjects()).containsExactly("cards/2/weg");
  }

  @Test
  void report_isEmpty_whenStorageAndMetadataAgree() {
    // Given
    when(admins.isPlatformAdmin(1L)).thenReturn(true);
    when(storage.listKeys()).thenReturn(List.of("cards/1/a"));
    when(attachments.findAllObjectKeys()).thenReturn(List.of("cards/1/a"));

    // When
    StorageReconciliationService.ReconciliationReport report = service.report(1L);

    // Then
    assertThat(report.orphanedObjects()).isEmpty();
    assertThat(report.missingObjects()).isEmpty();
  }

  @Test
  void report_sortsBothListsForStableOutput() {
    // Given
    when(admins.isPlatformAdmin(1L)).thenReturn(true);
    when(storage.listKeys()).thenReturn(List.of("cards/2/z", "cards/1/a"));
    when(attachments.findAllObjectKeys()).thenReturn(List.of("cards/9/y", "cards/3/b"));

    // When
    StorageReconciliationService.ReconciliationReport report = service.report(1L);

    // Then
    assertThat(report.orphanedObjects()).containsExactly("cards/1/a", "cards/2/z");
    assertThat(report.missingObjects()).containsExactly("cards/3/b", "cards/9/y");
  }

  @Test
  void report_rejectsNonAdmins() {
    // Given
    when(admins.isPlatformAdmin(1L)).thenReturn(false);

    // When / Then
    assertThatThrownBy(() -> service.report(1L)).isInstanceOf(AdminAccessDeniedException.class);
    verifyNoInteractions(storage);
  }
}
