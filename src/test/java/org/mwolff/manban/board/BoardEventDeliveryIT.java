package org.mwolff.manban.board;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.board.application.BoardChangedEvent;
import org.mwolff.manban.board.application.BoardChangedEvent.ChangeType;
import org.mwolff.manban.board.web.BoardEventRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IT der vollständigen SSE-Event-Kette (#464): Karten-Mutation über HTTP → {@code
 * CardBoardActivityEvent} (card-Modul) → {@code CardBoardActivityBridge} (Composition-Root) →
 * {@link BoardChangedEvent} → {@code BoardEventListener} → {@link BoardEventRegistry}.
 *
 * <p>Seit #459 hängt diese Kette an einer Annotationswahl plus Component-Scan des {@code
 * config}-Pakets. Die vorhandenen Tests prüfen jedes Glied für sich (Bridge mit Mock-Publisher,
 * Registry mit Mock-Emittern, {@code BoardEventsIT} nur die Endpoint-Autorisierung) — bricht die
 * <em>Verdrahtung</em>, bleiben sie alle grün und die Live-Updates sterben stumm. Dieser IT nagelt
 * die Verdrahtung fest.
 *
 * <p>Beobachtet wird {@link BoardEventRegistry#publish} statt der Auslieferung an einen Emitter:
 * der Heartbeat der Registry ({@code @Scheduled}) schickt eigenständig Kommentar-Pings an jeden
 * offenen Emitter, eine Zählung auf Emitter-Ebene wäre dadurch zeitabhängig. Von {@code publish}
 * bis zum Emitter deckt {@code BoardEventRegistryTest} den Rest ab.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class BoardEventDeliveryIT extends AbstractIntegrationTest {

  private static final String PASSWORD = "sup3r-secret";
  private static final long UNKNOWN_CARD_ID = 999_999L;

  @Autowired private MockMvc mvc;
  @Autowired private AppUserRepository users;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper json;
  @Autowired private TranslatedEventRecorder recorder;

  @MockitoSpyBean private BoardEventRegistry registry;

  private Cookie owner;
  private long projectId;

  @Test
  void cardMove_deliversExactlyOneEventToRegistry() throws Exception {
    setup("sse-move@example.com");
    JsonNode board = createBoard();
    long boardId = board.get("id").asLong();
    long backlog = columnId(board, 0);
    long inProgress = columnId(board, 2);
    long cardId = createCard(boardId, backlog);
    resetObservers();

    mvc.perform(
            post("/api/cards/" + cardId + "/move")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"columnId\":%d,\"position\":0}".formatted(inProgress)))
        .andExpect(status().isOk());

    ArgumentCaptor<BoardChangedEvent> captor = ArgumentCaptor.forClass(BoardChangedEvent.class);
    verify(registry, times(1)).publish(anyLong(), captor.capture());
    assertThat(captor.getValue())
        .isEqualTo(new BoardChangedEvent(boardId, ChangeType.MOVED, cardId));
  }

  @Test
  void rolledBackMutation_deliversNoEvent() throws Exception {
    setup("sse-rollback@example.com");
    JsonNode board = createBoard();
    long boardId = board.get("id").asLong();
    long cardId = createCard(boardId, columnId(board, 0));
    resetObservers();

    // Alles-oder-nichts-Batch: die erste Karte wird archiviert (und publiziert dabei bereits ihr
    // Event), die zweite existiert nicht -> die gesamte Transaktion rollt zurück.
    mvc.perform(
            post("/api/cards/bulk-archive")
                .cookie(owner)
                .contentType("application/json")
                .content("{\"cardIds\":[%d,%d]}".formatted(cardId, UNKNOWN_CARD_ID)))
        .andExpect(status().isNotFound());

    // Prämisse: die Übersetzung lief innerhalb der gescheiterten Transaktion tatsächlich — sonst
    // wäre die folgende Zusicherung leer.
    assertThat(recorder.events())
        .containsExactly(new BoardChangedEvent(boardId, ChangeType.ARCHIVED, cardId));
    // AFTER_COMMIT: bei Rollback erreicht nichts die Abonnenten.
    verify(registry, never()).publish(anyLong(), any());
  }

  @Test
  void crossBoardTransfer_deliversOneEventPerBoard() throws Exception {
    setup("sse-transfer@example.com");
    JsonNode source = createBoard();
    JsonNode target = createBoard();
    long sourceBoardId = source.get("id").asLong();
    long targetBoardId = target.get("id").asLong();
    long cardId = createCard(sourceBoardId, columnId(source, 0));
    resetObservers();

    mvc.perform(
            post("/api/cards/" + cardId + "/transfer")
                .cookie(owner)
                .contentType("application/json")
                .content(
                    "{\"targetBoardId\":%d,\"targetColumnId\":%d}"
                        .formatted(targetBoardId, columnId(target, 0))))
        .andExpect(status().isOk());

    verify(registry, times(2)).publish(anyLong(), any());
    verify(registry)
        .publish(
            eq(sourceBoardId), eq(new BoardChangedEvent(sourceBoardId, ChangeType.MOVED, cardId)));
    verify(registry)
        .publish(
            eq(targetBoardId), eq(new BoardChangedEvent(targetBoardId, ChangeType.MOVED, cardId)));
  }

  /** Setzt die Beobachter nach dem Fixture-Aufbau zurück (das Anlegen publiziert selbst Events). */
  private void resetObservers() {
    clearInvocations(registry);
    recorder.clear();
  }

  private void setup(String email) throws Exception {
    owner = session(email, PlatformRole.USER);
    Cookie admin = session("sse-admin@example.com", PlatformRole.ADMIN);
    projectId =
        json.readTree(
                mvc.perform(
                        post("/api/projects")
                            .cookie(admin)
                            .contentType("application/json")
                            .content("{\"name\":\"P\",\"ownerEmail\":\"%s\"}".formatted(email)))
                    .andExpect(status().isCreated())
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("id")
            .asLong();
  }

  private JsonNode createBoard() throws Exception {
    return json.readTree(
        mvc.perform(
                post("/api/projects/" + projectId + "/boards")
                    .cookie(owner)
                    .contentType("application/json")
                    .content("{\"name\":\"B\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString());
  }

  /** Default-Spalten: [0]=Backlog, [1]=Ready, [2]=In Progress, [3]=In Review, [4]=Done. */
  private static long columnId(JsonNode board, int index) {
    return board.get("columns").get(index).get("id").asLong();
  }

  private long createCard(long boardId, long columnId) throws Exception {
    return json.readTree(
            mvc.perform(
                    post("/api/boards/" + boardId + "/cards")
                        .cookie(owner)
                        .contentType("application/json")
                        .content("{\"columnId\":%d,\"title\":\"A\"}".formatted(columnId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString())
        .get("id")
        .asLong();
  }

  private Cookie session(String email, PlatformRole role) throws Exception {
    if (users.findByEmail(email).isEmpty()) {
      users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "P", true, role));
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

  /**
   * Registriert den Rekorder (verschachtelte {@code @TestConfiguration} wird automatisch gezogen).
   */
  @TestConfiguration
  static class RecorderConfiguration {

    @Bean
    TranslatedEventRecorder translatedEventRecorder() {
      return new TranslatedEventRecorder();
    }
  }

  /**
   * Zeichnet übersetzte {@link BoardChangedEvent}s sofort auf — bewusst ein gewöhnlicher {@code
   * EventListener} (nicht transaktionsgebunden), damit auch Events aus zurückgerollten
   * Transaktionen sichtbar bleiben. Nur so ist der Rollback-Fall nicht trivial grün.
   */
  static class TranslatedEventRecorder {

    private final List<BoardChangedEvent> received = new CopyOnWriteArrayList<>();

    @EventListener
    void onBoardChanged(BoardChangedEvent event) {
      received.add(event);
    }

    List<BoardChangedEvent> events() {
      return List.copyOf(received);
    }

    void clear() {
      received.clear();
    }
  }
}
