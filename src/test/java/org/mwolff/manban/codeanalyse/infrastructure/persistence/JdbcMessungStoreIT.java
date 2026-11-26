package org.mwolff.manban.codeanalyse.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.codeanalyse.application.MessungStore;
import org.mwolff.manban.codeanalyse.domain.Messung;
import org.mwolff.manban.codeanalyse.domain.Zustand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Zusagen des Ports {@link MessungStore} gegen echtes Postgres (Issue #2274, Plan #2272 E2, E3,
 * E8, E12): die erste Messung je Projekt und veröffentlichtem Stand gilt, gelöscht wird nur mit dem
 * Projekt, und die Datenbank selbst weist unvollständige zählende Zeilen und negative Zahlen ab.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcMessungStoreIT extends AbstractIntegrationTest {

  private static final String TOKEN = "kit-token";
  private static final Instant EINGANG = Instant.parse("2026-11-20T12:00:00Z");
  private static final Instant T0 = Instant.parse("2026-11-16T10:00:00Z");

  private static final Zustand VORHER = new Zustand(3, 1, 12, 480);
  private static final Zustand ENDE = new Zustand(1, 0, 7, 495);

  @Autowired private MessungStore store;
  @Autowired private AppUserRepository users;
  @Autowired private JdbcTemplate jdbc;

  private long eigenesProjekt;
  private long anderesProjekt;

  @BeforeEach
  void projekte() {
    Long userId =
        users.save(new AppUser(null, "person@example.com", "Person", true, PlatformRole.USER)).id();
    eigenesProjekt = projekt(userId, "P");
    anderesProjekt = projekt(userId, "Q");
  }

  private long projekt(Long userId, String name) {
    Long id =
        jdbc.queryForObject(
            "INSERT INTO project (name, owner_user_id) VALUES (?, ?) RETURNING id",
            Long.class,
            name,
            userId);
    return id == null ? -1 : id;
  }

  private static Messung zaehlend(String commit, Instant veroeffentlicht) {
    return new Messung(
        commit, veroeffentlicht, "base-" + commit, "3.21.1", true, null, 4, VORHER, ENDE);
  }

  private static Messung nichtZaehlend(String commit, Instant veroeffentlicht) {
    return new Messung(
        commit,
        veroeffentlicht,
        "base-" + commit,
        "3.21.1",
        false,
        "missing-final",
        2,
        VORHER,
        null);
  }

  private int zeilen() {
    Integer anzahl =
        jdbc.queryForObject("SELECT count(*) FROM code_analysis_measurement", Integer.class);
    return anzahl == null ? -1 : anzahl;
  }

  @Test
  void speichertZaehlendeUndNichtZaehlendeMessungUnveraendert() {
    Messung zaehlt = zaehlend("abc123", T0);
    Messung zaehltNicht = nichtZaehlend("def456", T0.plusSeconds(60));
    Messung ohneZustaende =
        new Messung(
            "ghi789",
            T0.plusSeconds(120),
            "base",
            "3.21.1",
            false,
            "no-measurement",
            0,
            null,
            null);

    assertThat(store.speichereErste(eigenesProjekt, TOKEN, zaehlt, EINGANG)).isEqualTo(zaehlt);
    assertThat(store.speichereErste(eigenesProjekt, TOKEN, zaehltNicht, EINGANG))
        .isEqualTo(zaehltNicht);
    assertThat(store.speichereErste(eigenesProjekt, TOKEN, ohneZustaende, EINGANG))
        .isEqualTo(ohneZustaende);

    assertThat(store.ladeAb(eigenesProjekt, T0))
        .containsExactly(zaehlt, zaehltNicht, ohneZustaende);
    assertThat(
            jdbc.queryForObject(
                "SELECT token_name FROM code_analysis_measurement"
                    + " WHERE published_commit = 'abc123'",
                String.class))
        .isEqualTo(TOKEN);
  }

  @Test
  void eineZweiteMeldungDesselbenStandsAendertNichtsUndLiefertDieErste() {
    Messung erste = zaehlend("abc123", T0);
    Messung zweite = nichtZaehlend("abc123", T0.plusSeconds(3600));

    store.speichereErste(eigenesProjekt, TOKEN, erste, EINGANG);

    assertThat(
            store.speichereErste(eigenesProjekt, "anderes-token", zweite, EINGANG.plusSeconds(5)))
        .isEqualTo(erste);
    assertThat(store.speichereErste(eigenesProjekt, TOKEN, erste, EINGANG)).isEqualTo(erste);
    assertThat(store.ladeAb(eigenesProjekt, T0.minusSeconds(1))).containsExactly(erste);
    assertThat(zeilen()).isEqualTo(1);
  }

  @Test
  void derselbeStandInEinemAnderenProjektIstEineEigeneMessung() {
    Messung eigene = zaehlend("abc123", T0);
    Messung fremde = nichtZaehlend("abc123", T0);

    store.speichereErste(eigenesProjekt, TOKEN, eigene, EINGANG);

    assertThat(store.speichereErste(anderesProjekt, TOKEN, fremde, EINGANG)).isEqualTo(fremde);
    assertThat(store.ladeAb(eigenesProjekt, T0)).containsExactly(eigene);
    assertThat(store.ladeAb(anderesProjekt, T0)).containsExactly(fremde);
  }

  @Test
  void ladenAbEinemZeitpunktLaesstAeltereAusUndOrdnetNachVeroeffentlichung() {
    Messung alt = zaehlend("alt", T0.minusSeconds(1));
    Messung spaet = zaehlend("spaet", T0.plusSeconds(600));
    Messung genau = nichtZaehlend("genau", T0);
    store.speichereErste(eigenesProjekt, TOKEN, alt, EINGANG);
    store.speichereErste(eigenesProjekt, TOKEN, spaet, EINGANG);
    store.speichereErste(eigenesProjekt, TOKEN, genau, EINGANG);

    assertThat(store.ladeAb(eigenesProjekt, T0)).containsExactly(genau, spaet);
  }

  @Test
  void erstesPublishedAtUndHatMessungenStimmen() {
    store.speichereErste(eigenesProjekt, TOKEN, zaehlend("b", T0.plusSeconds(60)), EINGANG);
    store.speichereErste(eigenesProjekt, TOKEN, nichtZaehlend("a", T0), EINGANG);

    assertThat(store.erstesPublishedAt(eigenesProjekt)).contains(T0);
    assertThat(store.hatMessungen(eigenesProjekt)).isTrue();
  }

  @Test
  void einLeeresProjektHatKeineMessungen() {
    store.speichereErste(eigenesProjekt, TOKEN, zaehlend("a", T0), EINGANG);

    assertThat(store.erstesPublishedAt(anderesProjekt)).isEmpty();
    assertThat(store.hatMessungen(anderesProjekt)).isFalse();
    assertThat(store.ladeAb(anderesProjekt, T0.minusSeconds(3600))).isEmpty();
  }

  @Test
  void mitDemProjektVerschwindenSeineMessungen() {
    store.speichereErste(eigenesProjekt, TOKEN, zaehlend("a", T0), EINGANG);
    store.speichereErste(anderesProjekt, TOKEN, zaehlend("a", T0), EINGANG);

    jdbc.update("DELETE FROM project WHERE id = ?", eigenesProjekt);

    assertThat(zeilen()).isEqualTo(1);
    assertThat(store.hatMessungen(eigenesProjekt)).isFalse();
  }

  @Test
  void dieDatenbankWeistEineZaehlendeZeileOhneVollstaendigeZustaendeAb() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO code_analysis_measurement (project_id, published_commit,"
                        + " published_at, base_commit, kit_version, counts, cards, before_bugs,"
                        + " before_vulnerabilities, before_code_smells, before_changed_lines,"
                        + " final_bugs, final_vulnerabilities, final_code_smells, token_name,"
                        + " created_at) VALUES (?, 'x', now(), 'b', 'k', true, 1, 0, 0, 0, 1, 0, 0,"
                        + " 0, 't', now())",
                    eigenesProjekt))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void dieDatenbankWeistEineNegativeZahlAb() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO code_analysis_measurement (project_id, published_commit,"
                        + " published_at, base_commit, kit_version, counts, reason, cards,"
                        + " before_bugs, token_name, created_at) VALUES (?, 'x', now(), 'b', 'k',"
                        + " false, 'incomplete', 0, -1, 't', now())",
                    eigenesProjekt))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO code_analysis_measurement (project_id, published_commit,"
                        + " published_at, base_commit, kit_version, counts, reason, cards,"
                        + " token_name, created_at) VALUES (?, 'y', now(), 'b', 'k', false,"
                        + " 'incomplete', -1, 't', now())",
                    eigenesProjekt))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(zeilen()).isZero();
  }
}
