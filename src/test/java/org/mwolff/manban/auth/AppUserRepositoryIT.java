package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Integrationstest gegen ein echtes Postgres (Testcontainers). Deckt die
 * Akzeptanzkriterien von F2 ab: Flyway migriert fehlerfrei (Kontext startet und
 * Hibernate validiert die Entity gegen das migrierte Schema), ein Repository
 * liest/schreibt eine Tabelle, und die Seed-Matrix in {@code role_permission} stimmt.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
class AppUserRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    private AppUserRepository users;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void savesAndReadsAppUser() {
        AppUser saved = users.save(
                new AppUser(null, "alice@example.com", "argon2-hash", "Alice", false, PlatformRole.USER));

        assertThat(saved.id()).isNotNull();
        assertThat(users.existsByEmail("alice@example.com")).isTrue();
        assertThat(users.findByEmail("alice@example.com"))
                .get()
                .satisfies(u -> {
                    assertThat(u.displayName()).isEqualTo("Alice");
                    assertThat(u.platformRole()).isEqualTo(PlatformRole.USER);
                    assertThat(u.emailVerified()).isFalse();
                });
    }

    @Test
    void seedsRolePermissionMatrix() {
        assertThat(permissionCount("OWNER")).isEqualTo(24);
        assertThat(permissionCount("ADMIN")).isEqualTo(22);
        assertThat(permissionCount("MEMBER")).isEqualTo(16);
        assertThat(permissionCount("VIEWER")).isEqualTo(5);
    }

    private int permissionCount(String role) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM role_permission WHERE role = ?", Integer.class, role);
        return count == null ? 0 : count;
    }
}
