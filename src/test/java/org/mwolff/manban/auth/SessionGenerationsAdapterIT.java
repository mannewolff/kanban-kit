package org.mwolff.manban.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.SessionGenerations;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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

  @Test
  void beginntBeiNullFuerEinFrischAngelegtesKonto() {
    long userId = neuesKonto("start@example.com").id();

    assertThat(generations.current(userId)).hasValue(0L);
  }

  @Test
  void zaehltJeAufrufUmGenauEinsHoch() {
    long userId = neuesKonto("bump@example.com").id();

    generations.invalidateSessions(userId);
    assertThat(generations.current(userId)).hasValue(1L);

    generations.invalidateSessions(userId);
    assertThat(generations.current(userId)).hasValue(2L);
  }

  /**
   * Die Generation hängt nicht am Aggregat {@code AppUser} (Plan #883, E5). Würde die Spalte an
   * {@code AppUserEntity} gemappt, schriebe dieses {@code save} den beim Laden gesehenen alten Wert
   * zurück — die beendeten Sitzungen lebten wieder auf.
   */
  @Test
  void haeltDieGenerationGegenEinSpaeteresSpeichern() {
    AppUser geladen = neuesKonto("mapping@example.com");

    generations.invalidateSessions(geladen.id());
    users.save(geladen);

    assertThat(generations.current(geladen.id())).hasValue(1L);
  }

  @Test
  void liefertNichtsFuerEinUnbekanntesKonto() {
    assertThat(generations.current(UNBEKANNTE_USER_ID)).isEqualTo(OptionalLong.empty());
  }

  /**
   * Ein Konto, das es nicht (mehr) gibt, hat keine Sitzungen, die weiterlaufen könnten — das
   * Hochzählen trifft null Zeilen und ist damit erfüllt, kein Fehlerfall.
   */
  @Test
  void bleibtStillFuerEinUnbekanntesKonto() {
    assertThatCode(() -> generations.invalidateSessions(UNBEKANNTE_USER_ID))
        .doesNotThrowAnyException();
  }

  private AppUser neuesKonto(String email) {
    return users.save(
        new AppUser(null, email, "argon2-hash", "Testkonto", true, PlatformRole.USER));
  }
}
