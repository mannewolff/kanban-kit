package org.mwolff.manban.project;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Prüft den Rollen-Matrix-Endpunkt gegen den geseedeten Stand (#51). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class RoleMatrixIT {

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
    void matrixReflectsSeededGrants() throws Exception {
        Cookie session = loginAs("matrix-user@example.com");

        mvc.perform(get("/api/roles/matrix").cookie(session))
                .andExpect(status().isOk())
                // Rollen in Anzeige-Reihenfolge
                .andExpect(jsonPath("$.roles.length()").value(4))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
                .andExpect(jsonPath("$.roles[3]").value("OWNER"))
                // Rechte inkl. abgeleiteter Ressource/Operation
                .andExpect(jsonPath("$.permissions.length()").value(24))
                .andExpect(jsonPath("$.permissions[?(@.key=='BOARD_CREATE')].resource").value(hasItem("BOARD")))
                .andExpect(jsonPath("$.permissions[?(@.key=='BOARD_CREATE')].operation").value(hasItem("CREATE")))
                // Grants je Rolle passend zum V4-Seed
                .andExpect(jsonPath("$.grants.VIEWER.length()").value(5))
                .andExpect(jsonPath("$.grants.VIEWER").value(hasItem("BOARD_READ")))
                .andExpect(jsonPath("$.grants.MEMBER.length()").value(16))
                .andExpect(jsonPath("$.grants.MEMBER").value(hasItem("TICKET_CREATE")))
                .andExpect(jsonPath("$.grants.MEMBER").value(not(hasItem("COMMENT_DELETE"))))
                .andExpect(jsonPath("$.grants.ADMIN.length()").value(22))
                .andExpect(jsonPath("$.grants.ADMIN").value(hasItem("COMMENT_DELETE")))
                .andExpect(jsonPath("$.grants.OWNER.length()").value(24));
    }

    @Test
    void matrixRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/roles/matrix"))
                .andExpect(status().isUnauthorized());
    }
}
