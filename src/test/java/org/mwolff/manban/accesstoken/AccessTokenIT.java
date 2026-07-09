package org.mwolff.manban.accesstoken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** End-to-End-Test der PAT-Funktionalität (Erstellen, Nutzen, Widerrufen, Least Privilege). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AccessTokenIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    private static final String PASSWORD = "sup3r-secret";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper json;

    private Cookie loginAs(String email) throws Exception {
        if (users.findByEmail(email).isEmpty()) {
            users.save(new AppUser(null, email, passwordEncoder.encode(PASSWORD), "Person", true, PlatformRole.USER));
        }
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("manban_session");
    }

    @Test
    void createUseListRevokeFlow() throws Exception {
        Cookie session = loginAs("pat-user@example.com");

        // 1. Anlegen (Cookie) -> Klartext einmalig
        String body = mvc.perform(post("/api/access-tokens").cookie(session)
                        .contentType("application/json").content("{\"name\":\"CI-Runner\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plaintext").exists())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = json.readTree(body);
        String plaintext = created.get("plaintext").asText();
        long tokenId = created.get("id").asLong();
        assertThat(plaintext).startsWith("tk_");

        // 2. Mit PAT authentifizieren
        mvc.perform(get("/api/me").header("X-Kanban-Token", plaintext))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("pat-user@example.com"));

        // 3. Auflisten (Cookie) — kein Klartext/Hash
        mvc.perform(get("/api/access-tokens").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("CI-Runner"))
                .andExpect(jsonPath("$[0].revoked").value(false));

        // 4. Widerrufen (Cookie) -> PAT danach 401
        mvc.perform(delete("/api/access-tokens/" + tokenId).cookie(session))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").header("X-Kanban-Token", plaintext))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPatIsUnauthorized() throws Exception {
        mvc.perform(get("/api/me").header("X-Kanban-Token", "tk_deadbeef"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenManagementViaPatIsForbidden() throws Exception {
        Cookie session = loginAs("pat-admin@example.com");
        String body = mvc.perform(post("/api/access-tokens").cookie(session)
                        .contentType("application/json").content("{\"name\":\"self\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String plaintext = json.readTree(body).get("plaintext").asText();

        // Verwaltung per PAT (ohne Cookie) muss verweigert werden (Least Privilege).
        mvc.perform(post("/api/access-tokens").header("X-Kanban-Token", plaintext)
                        .contentType("application/json").content("{\"name\":\"escalate\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/access-tokens").header("X-Kanban-Token", plaintext))
                .andExpect(status().isForbidden());
    }
}
