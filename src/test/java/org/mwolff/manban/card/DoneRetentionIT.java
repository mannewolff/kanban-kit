package org.mwolff.manban.card;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.card.application.CardRepository;
import org.mwolff.manban.card.application.DoneRetentionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Test der Done-Retention mit fixer Zeitquelle. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class DoneRetentionIT {

  @Container @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

  private static final String PASSWORD = "sup3r-secret";

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private DoneRetentionService retention;
  @Autowired private CardRepository cards;

  @Test
  void archivesOnlyExpiredDoneCards() throws Exception {
    Cookie alice = login("retention@example.com");
    Cookie admin = platformAdminSession();
    long projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(admin)
                            .contentType("application/json")
                            .content("{\"name\":\"P\",\"ownerEmail\":\"retention@example.com\"}"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
    JsonNode board =
        json.readTree(
            mvc.perform(
                    post("/api/projects/" + projectId + "/boards")
                        .cookie(alice)
                        .contentType("application/json")
                        .content("{\"name\":\"B\"}"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    long boardId = board.get("id").asLong();
    long columnId = board.get("columns").get(0).get("id").asLong();

    long expired = createCard(alice, boardId, columnId, "Alt");
    long recent = createCard(alice, boardId, columnId, "Neu");
    long notDone = createCard(alice, boardId, columnId, "Offen");

    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    setMovedToDone(expired, now.minus(Duration.ofDays(40))); // vor der Retention
    setMovedToDone(recent, now.minus(Duration.ofDays(5))); // innerhalb der Retention
    // notDone bleibt moved_to_done_at = NULL

    int archivedCount = retention.archiveExpiredDoneCards(now, 30);

    Assertions.assertThat(archivedCount).isEqualTo(1);
    Assertions.assertThat(cards.findById(expired).orElseThrow().archived()).isTrue();
    Assertions.assertThat(cards.findById(recent).orElseThrow().archived()).isFalse();
    Assertions.assertThat(cards.findById(notDone).orElseThrow().archived()).isFalse();
  }

  private Cookie login(String email) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(
          new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, PlatformRole.USER));
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

  private long createCard(Cookie session, long boardId, long columnId, String title)
      throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards")
                        .cookie(session)
                        .contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"%s\"}".formatted(columnId, title)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private void setMovedToDone(long cardId, Instant when) {
    jdbc.update(
        "UPDATE card SET moved_to_done_at = ? WHERE id = ?", java.sql.Timestamp.from(when), cardId);
  }
}
