package org.mwolff.manban.attachment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.attachment.application.AttachmentService;
import org.mwolff.manban.attachment.application.AttachmentService.AttachmentView;
import org.mwolff.manban.attachment.application.AttachmentService.Download;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

/** Unit-Tests des Anhang-Controllers (Service gemockt). */
class AttachmentControllerTest {

  private AttachmentService service;
  private AttachmentController controller;

  @BeforeEach
  void setUp() {
    service = mock(AttachmentService.class);
    controller = new AttachmentController(service);
  }

  @Test
  void upload_usesOriginalFilename_whenPresent() throws IOException {
    // Given
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn("report.pdf");
    when(file.getBytes()).thenReturn(new byte[] {1, 2});
    AttachmentView view =
        new AttachmentView(1L, 5L, "report.pdf", "application/pdf", 2L, Instant.EPOCH);
    when(service.upload(eq(3L), eq(5L), eq("report.pdf"), any(byte[].class))).thenReturn(view);

    // When
    AttachmentView result = controller.upload(3L, 5L, file);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void upload_fallsBackToDefaultName_whenOriginalFilenameNull() throws IOException {
    // Given
    MultipartFile file = mock(MultipartFile.class);
    when(file.getOriginalFilename()).thenReturn(null);
    when(file.getBytes()).thenReturn(new byte[] {9});
    AttachmentView view =
        new AttachmentView(1L, 5L, "datei", "application/octet-stream", 1L, Instant.EPOCH);
    when(service.upload(eq(3L), eq(5L), eq("datei"), any(byte[].class))).thenReturn(view);

    // When
    AttachmentView result = controller.upload(3L, 5L, file);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<AttachmentView> views =
        List.of(new AttachmentView(1L, 5L, "a.txt", "text/plain", 3L, Instant.EPOCH));
    when(service.list(3L, 5L)).thenReturn(views);

    // When
    List<AttachmentView> result = controller.list(3L, 5L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void download_buildsAttachmentResponseWithMetadata() {
    // Given
    var content = new ByteArrayInputStream("hi".getBytes(StandardCharsets.UTF_8));
    var download = new Download("report.pdf", "application/pdf", 2L, content);
    when(service.download(3L, 8L)).thenReturn(download);

    // When
    ResponseEntity<InputStreamResource> response = controller.download(3L, 8L);

    // Then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .contains("attachment")
        .contains("report.pdf");
  }

  @Test
  void download_setsContentTypeAndLength() {
    // Given
    var content = new ByteArrayInputStream("hi".getBytes(StandardCharsets.UTF_8));
    var download = new Download("report.pdf", "application/pdf", 2L, content);
    when(service.download(3L, 8L)).thenReturn(download);

    // When
    ResponseEntity<InputStreamResource> response = controller.download(3L, 8L);

    // Then
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(2L);
  }

  @Test
  void delete_delegatesToService() {
    // When
    controller.delete(3L, 8L);

    // Then
    verify(service).delete(3L, 8L);
  }
}
