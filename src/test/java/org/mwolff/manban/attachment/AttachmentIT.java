package org.mwolff.manban.attachment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.attachment.application.ObjectStorage;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.CardService;
import org.mwolff.manban.outbox.application.OutboxDispatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** End-to-End-Test für Anhänge gegen echtes Postgres + MinIO (Testcontainers). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class AttachmentIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";

  /** Kleines Limit, damit der Limit-Test (perCardLimitIsEnforced) günstig bleibt. */
  @DynamicPropertySource
  static void attachmentLimit(DynamicPropertyRegistry registry) {
    registry.add("manban.storage.max-per-card", () -> "2");
  }

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private ObjectMapper json;
  @Autowired private ObjectStorage objectStorage;
  @Autowired private OutboxDispatchService outboxDispatch;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CardService cardService;
  @Autowired private BoardService boardService;

  @Autowired private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

  private Cookie login;
  private long cardId;
  private long boardId;
  private long columnId;

  private void setup(String email) throws Exception {
    String hash = passwordEncoder.encode(PASSWORD);
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, hash, "P", true, PlatformRole.USER));
    }
    login =
        mvc.perform(
                post("/api/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie("manban_session");

    Cookie admin = platformAdminSession();
    long projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(admin)
                            .contentType("application/json")
                            .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(email)))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    JsonNode board =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(login)
                        .contentType("application/json")
                        .content("{\"name\":\"B\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    boardId = board.get("id").asLong();
    columnId = board.get("columns").get(0).get("id").asLong();
    cardId =
        json.readTree(
                mvc.perform(
                        post("/api/boards/" + boardId + "/cards")
                            .cookie(login)
                            .contentType("application/json")
                            .content("{\"columnId\":%d,\"title\":\"K\"}".formatted(columnId)))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    // Die Projektanlage merkt seit #502 eine Zuordnungs-Mail in der Outbox vor — einmal zustellen
    // (Log-Modus), damit die #503-Tests ausschließlich ihre eigenen Einträge zählen.
    outboxDispatch.dispatchDue();
  }

  private long upload(String filename, String declaredType, byte[] content) throws Exception {
    String body =
        mvc.perform(
                multipart("/api/cards/" + cardId + "/attachments")
                    .file(new MockMultipartFile("file", filename, declaredType, content))
                    .cookie(login))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return json.readTree(body).get("id").asLong();
  }

  private Cookie platformAdminSession() throws Exception {
    String email = "project-admin@example.com";
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(
              null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.ADMIN));
    }
    return mvc.perform(
            post("/api/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("manban_session");
  }

  private String objectKeyOf(long attachmentId) {
    return jdbc.queryForObject(
        "SELECT object_key FROM attachment_meta WHERE id = ?", String.class, attachmentId);
  }

  private long userIdOf(String email) {
    return users.findByEmail(email).orElseThrow().requireId();
  }

  private long createCard(String title) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards")
                        .cookie(login)
                        .contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  @Test
  void uploadDownloadDelete() throws Exception {
    setup("att-crud@example.com");
    byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 1, 2, 3, 4};

    long id = upload("bild.png", "image/png", png);

    var response =
        mvc.perform(get("/api/attachments/" + id).cookie(login))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    Assertions.assertThat(response.getHeader("Content-Disposition")).contains("attachment");
    Assertions.assertThat(response.getContentAsByteArray()).isEqualTo(png);
    Assertions.assertThat(response.getContentType()).isEqualTo("image/png");

    mvc.perform(delete("/api/attachments/" + id).cookie(login)).andExpect(status().isNoContent());
    mvc.perform(get("/api/attachments/" + id).cookie(login)).andExpect(status().isNotFound());
    mvc.perform(get("/api/cards/" + cardId + "/attachments").cookie(login))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void disguisedHtmlIsServedAsAttachmentWithDetectedType() throws Exception {
    setup("att-xss@example.com");
    byte[] html =
        "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);

    // Als PNG getarnt hochgeladen — der Content-Type wird per Magic-Bytes korrigiert.
    long id = upload("harmlos.png", "image/png", html);

    var response =
        mvc.perform(get("/api/attachments/" + id).cookie(login))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    Assertions.assertThat(response.getHeader("Content-Disposition")).contains("attachment");
    Assertions.assertThat(response.getHeader("Content-Type")).contains("text/html");
  }

  @Test
  void perCardLimitIsEnforced() throws Exception {
    setup("att-limit@example.com");
    byte[] data = {1, 2, 3, 4, 5};
    upload("a.bin", "application/octet-stream", data);
    upload("b.bin", "application/octet-stream", data);

    // Drittes Upload überschreitet das Limit (2) -> 409.
    mvc.perform(
            multipart("/api/cards/" + cardId + "/attachments")
                .file(new MockMultipartFile("file", "c.bin", "application/octet-stream", data))
                .cookie(login))
        .andExpect(status().isConflict());
  }

  /**
   * Nagelt die Entscheidung aus #462 fest: Das Archivieren eines Boards entzieht nur das Board
   * selbst, nicht die Anhänge seiner Karten.
   */
  @Test
  void attachmentsStayUsableWhenBoardIsArchived() throws Exception {
    setup("att-archived-board@example.com");
    byte[] data = {1, 2, 3, 4, 5};
    long before = upload("vor-archiv.bin", "application/octet-stream", data);

    mvc.perform(delete("/api/boards/" + boardId).cookie(login)).andExpect(status().isNoContent());
    // Das Board selbst ist nach dem Archivieren nicht mehr auffindbar ...
    mvc.perform(get("/api/boards/" + boardId).cookie(login)).andExpect(status().isNotFound());

    // ... seine Karten und deren Anhänge bleiben es: die Rechte laufen über die Projekt-ID
    // der Karte, nicht über das Board.
    mvc.perform(get("/api/cards/" + cardId + "/attachments").cookie(login))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    var response =
        mvc.perform(get("/api/attachments/" + before).cookie(login))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    Assertions.assertThat(response.getContentAsByteArray()).isEqualTo(data);

    long after = upload("nach-archiv.bin", "application/octet-stream", data);
    mvc.perform(delete("/api/attachments/" + after).cookie(login))
        .andExpect(status().isNoContent());
  }

  /**
   * Board-lose Pool-Ideen (#405) haben kein Board, über das sich eine Projekt-ID auflösen ließe —
   * ihre Anhänge müssen trotzdem funktionieren (#462).
   */
  @Test
  void attachmentsWorkForBoardlessPoolIdea() throws Exception {
    setup("att-pool-idea@example.com");
    byte[] data = {9, 8, 7};
    mvc.perform(put("/api/cards/" + cardId + "/to-pool").cookie(login)).andExpect(status().isOk());

    long id = upload("idee.bin", "application/octet-stream", data);

    mvc.perform(get("/api/cards/" + cardId + "/attachments").cookie(login))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    var response =
        mvc.perform(get("/api/attachments/" + id).cookie(login))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    Assertions.assertThat(response.getContentAsByteArray()).isEqualTo(data);

    mvc.perform(delete("/api/attachments/" + id).cookie(login)).andExpect(status().isNoContent());
  }

  // --- Konsistenz Metadaten ↔ Objektspeicher (Issue #503) --------------------------------------

  @Test
  void deleteRemovesTheBlobOnlyAfterOutboxDispatch() throws Exception {
    setup("att-outbox-delete@example.com");
    long id = upload("weg.bin", "application/octet-stream", new byte[] {1, 2, 3});
    String key = objectKeyOf(id);

    mvc.perform(delete("/api/attachments/" + id).cookie(login)).andExpect(status().isNoContent());

    // Metadaten sofort weg — der Blob liegt noch (Worker ist in ITs aus), kein kaputter Verweis.
    Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM attachment_meta WHERE id = ?", Integer.class, id))
        .isZero();
    try (var stream = objectStorage.get(key)) {
      Assertions.assertThat(stream.readAllBytes()).isNotEmpty();
    }

    // Der Outbox-Durchlauf stellt den Löschauftrag zu — danach ist der Blob wirklich fort.
    Assertions.assertThat(outboxDispatch.dispatchDue()).isEqualTo(1);
    Assertions.assertThatThrownBy(() -> objectStorage.get(key))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void deletingBlobThatIsAlreadyGoneIsNoError() throws Exception {
    setup("att-idempotent@example.com");
    long id = upload("schon-weg.bin", "application/octet-stream", new byte[] {1});
    String key = objectKeyOf(id);
    mvc.perform(delete("/api/attachments/" + id).cookie(login)).andExpect(status().isNoContent());

    // Das Objekt verschwindet anderweitig, bevor der Auftrag läuft.
    objectStorage.delete(key);

    // Idempotenz (S3-Semantik): Der Auftrag endet als Erfolg, nicht als ewiger Fehlversuch.
    Assertions.assertThat(outboxDispatch.dispatchDue()).isEqualTo(1);
    Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT status FROM outbox_entry WHERE event_type = 'attachment.blob-delete'",
                String.class))
        .isEqualTo("DONE");
  }

  @Test
  void purgingCardRemovesItsBlobs() throws Exception {
    setup("att-purge-card@example.com");
    long first = upload("a.bin", "application/octet-stream", new byte[] {1});
    long second = upload("b.bin", "application/octet-stream", new byte[] {2});
    String firstKey = objectKeyOf(first);
    String secondKey = objectKeyOf(second);

    cardService.purge(userIdOf("att-purge-card@example.com"), cardId);

    // Cascade hat die Metadaten entfernt; die Blob-Löschung war zuvor eingeplant.
    Assertions.assertThat(
            jdbc.queryForObject("SELECT count(*) FROM attachment_meta", Integer.class))
        .isZero();
    Assertions.assertThat(outboxDispatch.dispatchDue()).isEqualTo(2);
    Assertions.assertThatThrownBy(() -> objectStorage.get(firstKey))
        .isInstanceOf(RuntimeException.class);
    Assertions.assertThatThrownBy(() -> objectStorage.get(secondKey))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void purgingBoardRemovesAllBlobsIncludingTrashedCards() throws Exception {
    setup("att-purge-board@example.com");
    long activeAttachment = upload("aktiv.bin", "application/octet-stream", new byte[] {1});
    String activeKey = objectKeyOf(activeAttachment);

    // Zweite Karte mit Anhang, danach in den Papierkorb — die Cascade träfe auch sie.
    long trashedCard = createCard("Papierkorb");
    String body =
        mvc.perform(
                multipart("/api/cards/" + trashedCard + "/attachments")
                    .file(
                        new MockMultipartFile(
                            "file", "trash.bin", "application/octet-stream", new byte[] {2}))
                    .cookie(login))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    String trashedKey = objectKeyOf(json.readTree(body).get("id").asLong());
    mvc.perform(delete("/api/cards/" + trashedCard).cookie(login))
        .andExpect(status().isNoContent());

    // Board archivieren (HTTP-Delete) und endgültig löschen.
    mvc.perform(delete("/api/boards/" + boardId).cookie(login)).andExpect(status().isNoContent());
    boardService.purgeBoard(userIdOf("att-purge-board@example.com"), boardId);

    Assertions.assertThat(
            jdbc.queryForObject("SELECT count(*) FROM attachment_meta", Integer.class))
        .isZero();
    Assertions.assertThat(outboxDispatch.dispatchDue()).isEqualTo(2);
    Assertions.assertThatThrownBy(() -> objectStorage.get(activeKey))
        .isInstanceOf(RuntimeException.class);
    Assertions.assertThatThrownBy(() -> objectStorage.get(trashedKey))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void reconciliationReportsOrphanedAndMissingObjects() throws Exception {
    setup("att-reconcile@example.com");
    long id = upload("verliert-blob.bin", "application/octet-stream", new byte[] {1});
    String missingKey = objectKeyOf(id);

    // Waise: Objekt ohne Metadaten (simuliert Altbestand bzw. Commit-Abbruch nach dem Put).
    objectStorage.put("reconcile-test/waise", new byte[] {9}, "application/octet-stream");
    // Fehlendes Objekt: Metadaten bleiben, der Blob verschwindet.
    objectStorage.delete(missingKey);

    String report =
        mvc.perform(get("/api/admin/storage/reconciliation").cookie(platformAdminSession()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    // "contains" statt Gleichheit: Der geteilte Bucket enthält Blobs anderer Suite-Tests, deren
    // Metadaten der DB-Reset entfernt hat — auch das sind (erwartete) Waisen.
    JsonNode parsed = json.readTree(report);
    Assertions.assertThat(parsed.get("orphanedObjects").toString())
        .contains("reconcile-test/waise");
    Assertions.assertThat(parsed.get("missingObjects").toString()).contains(missingKey);

    // Kein Plattform-Admin → 403.
    mvc.perform(get("/api/admin/storage/reconciliation").cookie(login))
        .andExpect(status().isForbidden());
  }
}
