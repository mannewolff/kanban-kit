package org.mwolff.manban.attachment.web;

import java.io.IOException;
import java.util.List;
import org.mwolff.manban.attachment.application.AttachmentService;
import org.mwolff.manban.attachment.application.AttachmentService.AttachmentView;
import org.mwolff.manban.attachment.application.AttachmentService.Download;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Karten-Anhänge: Upload (multipart), Download (immer als Attachment), Liste, Löschen. */
@RestController
class AttachmentController {

    private final AttachmentService attachments;

    AttachmentController(AttachmentService attachments) {
        this.attachments = attachments;
    }

    @PostMapping("/api/cards/{cardId}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    AttachmentView upload(@AuthenticationPrincipal Long userId, @PathVariable long cardId,
                          @RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "datei" : file.getOriginalFilename();
        return attachments.upload(userId, cardId, filename, file.getBytes());
    }

    @GetMapping("/api/cards/{cardId}/attachments")
    List<AttachmentView> list(@AuthenticationPrincipal Long userId, @PathVariable long cardId) {
        return attachments.list(userId, cardId);
    }

    /**
     * Download. Immer {@code Content-Disposition: attachment} — getarnte HTML/SVG werden
     * so heruntergeladen statt inline gerendert (Anti-XSS).
     */
    @GetMapping("/api/attachments/{id}")
    ResponseEntity<InputStreamResource> download(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        Download download = attachments.download(userId, id);
        ContentDisposition disposition = ContentDisposition.attachment().filename(download.filename()).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.size())
                .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/api/attachments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        attachments.delete(userId, id);
    }
}
