package org.mwolff.manban.attachment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-End-Test für Anhänge gegen echtes Postgres + MinIO (Testcontainers). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AttachmentIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio");

    private static final String PASSWORD = "sup3r-secret";

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("manban.storage.endpoint", MINIO::getS3URL);
        registry.add("manban.storage.access-key", MINIO::getUserName);
        registry.add("manban.storage.secret-key", MINIO::getPassword);
        registry.add("manban.storage.bucket", () -> "manban-test");
        registry.add("manban.storage.max-per-card", () -> "2"); // günstiger Limit-Test
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ObjectMapper json;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    private Cookie login;
    private long cardId;

    private void setup(String email) throws Exception {
        String hash = passwordEncoder.encode(PASSWORD);
        if (users.findByEmail(email).isEmpty()) {
            users.save(new AppUser(null, email, hash, "P", true, PlatformRole.USER));
        }
        login = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("manban_session");

        long projectId = json.readTree(mvc.perform(post("/api/projects").cookie(login)
                        .contentType("application/json").content("{\"name\":\"P\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
        JsonNode board = json.readTree(mvc.perform(post("/api/projects/" + projectId + "/boards").cookie(login)
                        .contentType("application/json").content("{\"name\":\"B\"}"))
                .andReturn().getResponse().getContentAsString());
        long boardId = board.get("id").asLong();
        long columnId = board.get("columns").get(0).get("id").asLong();
        cardId = json.readTree(mvc.perform(post("/api/boards/" + boardId + "/cards").cookie(login)
                        .contentType("application/json").content("{\"columnId\":%d,\"title\":\"K\"}".formatted(columnId)))
                .andReturn().getResponse().getContentAsString()).get("id").asLong();
    }

    private long upload(String filename, String declaredType, byte[] content) throws Exception {
        String body = mvc.perform(multipart("/api/cards/" + cardId + "/attachments")
                        .file(new MockMultipartFile("file", filename, declaredType, content)).cookie(login))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void uploadDownloadDelete() throws Exception {
        setup("att-crud@example.com");
        byte[] png = {(byte) 137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 1, 2, 3, 4};

        long id = upload("bild.png", "image/png", png);

        var response = mvc.perform(get("/api/attachments/" + id).cookie(login))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse();
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
        byte[] html = "<html><body><script>alert(1)</script></body></html>".getBytes(StandardCharsets.UTF_8);

        // Als PNG getarnt hochgeladen — der Content-Type wird per Magic-Bytes korrigiert.
        long id = upload("harmlos.png", "image/png", html);

        mvc.perform(get("/api/attachments/" + id).cookie(login))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andExpect(header().string("Content-Type",
                        org.hamcrest.Matchers.containsString("text/html")));
    }

    @Test
    void perCardLimitIsEnforced() throws Exception {
        setup("att-limit@example.com");
        byte[] data = {1, 2, 3, 4, 5};
        upload("a.bin", "application/octet-stream", data);
        upload("b.bin", "application/octet-stream", data);

        // Drittes Upload überschreitet das Limit (2) -> 409.
        mvc.perform(multipart("/api/cards/" + cardId + "/attachments")
                        .file(new MockMultipartFile("file", "c.bin", "application/octet-stream", data)).cookie(login))
                .andExpect(status().isConflict());
    }
}
