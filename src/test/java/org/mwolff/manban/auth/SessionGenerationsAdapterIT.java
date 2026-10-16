package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.SessionGenerations;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Zusagen des {@link SessionGenerations}-Ports gegen echtes Postgres (Issue #884).
 *
 * <p>Bewusst eine eigene Datei neben {@code AppUserRepositoryIT}: Der Port ist ein zweiter,
 * eigenständiger Persistenz-Zugang auf dieselbe Tabelle, und genau das Nebeneinander beider Zugänge
 * ist hier der Testgegenstand — {@link #haeltDieGenerationGegenEinSpaeteresSpeichern()} stellt sie
 * gegeneinander.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SessionGenerationsAdapterIT extends AbstractIntegrationTest {

  private static final long UNBEKANNTE_USER_ID = 999_999L;

  @Autowired private SessionGenerations generations;

  @Autowired private AppUserRepository users;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void beginntBeiNullFuerEinFrischAngelegtesKonto() {
    long userId = neuesKonto("start@example.com").id();

    assertThat(generations.current(userId)).hasValue(0L);
  }

  /**
   * Die Generation hängt nicht am Aggregat {@code AppUser} (Plan #883, E5). Würde die Spalte an
   * {@code AppUserEntity} gemappt, schriebe dieses {@code save} den beim Laden gesehenen alten Wert
   * zurück — die beendeten Sitzungen lebten wieder auf. Hochgezählt wird per SQL, so wie es eine
   * Migration tut, die alle Sitzungen beendet; den Port-Aufruf dafür gibt es seit dem Wegfall des
   * Passwort-Resets nicht mehr (Issue #1816).
   */
  @Test
  void haeltDieGenerationGegenEinSpaeteresSpeichern() {
    AppUser geladen = neuesKonto("mapping@example.com");

    jdbc.update(
        "update app_user set session_generation = session_generation + 1 where id = ?",
        geladen.id());
    users.save(geladen);

    assertThat(generations.current(geladen.id())).hasValue(1L);
  }

  @Test
  void liefertNichtsFuerEinUnbekanntesKonto() {
    assertThat(generations.current(UNBEKANNTE_USER_ID)).isEqualTo(OptionalLong.empty());
  }

  private AppUser neuesKonto(String email) {
    return users.save(
        new AppUser(null, email, "argon2-hash", "Testkonto", true, PlatformRole.USER));
  }
}
