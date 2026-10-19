package org.mwolff.manban.deployment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.deployment.application.Auslieferungsdaten;
import org.mwolff.manban.deployment.application.DeploymentStore;
import org.mwolff.manban.deployment.application.NeueMeldung;
import org.mwolff.manban.deployment.application.NeueMeldung.Commit;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;
import org.mwolff.manban.deployment.domain.GemeldeterCommit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Zusagen des Ports {@link DeploymentStore} gegen echtes Postgres (Issue #1833, Plan #1831 E3,
 * E6, E12).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class JdbcDeploymentStoreIT extends AbstractIntegrationTest {

  private static final String TOKEN = "deploy-token";
  private static final Instant EINGANG = Instant.parse("2026-10-17T12:00:00Z");
  private static final Instant T0 = Instant.parse("2026-10-10T10:00:00Z");

  private static final Commit EINS = new Commit("aaa111", Instant.parse("2026-10-09T08:00:00Z"));
  private static final Commit ZWEI = new Commit("bbb222", Instant.parse("2026-10-09T09:00:00Z"));

  @Autowired private DeploymentStore store;
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

  private static NeueMeldung meldung(
      String attemptId, Ausgang ausgang, Instant fertig, List<Commit> commits) {
    return new NeueMeldung(attemptId, ausgang, fertig, "rev-" + attemptId, commits);
  }

  private long id(String attemptId) {
    Long id =
        jdbc.queryForObject(
            "SELECT id FROM deployment_attempt WHERE attempt_id = ?", Long.class, attemptId);
    return id == null ? -1 : id;
  }

  private int zeilen(String tabelle) {
    Integer anzahl = jdbc.queryForObject("SELECT count(*) FROM " + tabelle, Integer.class);
    return anzahl == null ? -1 : anzahl;
  }

  @Test
  void fuegtEinenVersuchMitCommitsEinUndLiestIhnWieder() {
    NeueMeldung meldung = meldung("run-1", Ausgang.FAILURE, T0, List.of(EINS, ZWEI));

    store.fuegeEin(eigenesProjekt, TOKEN, meldung, EINGANG);

    assertThat(store.findeVersuch(eigenesProjekt, "run-1"))
        .hasValueSatisfying(
            gelesen -> {
              assertThat(gelesen)
                  .usingRecursiveComparison()
                  .ignoringFields("commits")
                  .isEqualTo(meldung);
              assertThat(gelesen.commits()).containsExactlyInAnyOrder(EINS, ZWEI);
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT token_name FROM deployment_attempt WHERE attempt_id = 'run-1'",
                String.class))
        .isEqualTo(TOKEN);
  }

  @Test
  void einVersuchOhneCommitsWirdGespeichert() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("run-1", Ausgang.SUCCESS, T0, List.of()), EINGANG);

    assertThat(store.findeVersuch(eigenesProjekt, "run-1"))
        .hasValueSatisfying(gelesen -> assertThat(gelesen.commits()).isEmpty());
  }

  @Test
  void unbekannteKennungIstLeer() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("run-1", Ausgang.SUCCESS, T0, List.of()), EINGANG);

    assertThat(store.findeVersuch(eigenesProjekt, "run-2")).isEmpty();
    assertThat(store.findeVersuch(anderesProjekt, "run-1")).isEmpty();
  }

  @Test
  void dieselbeKennungImSelbenProjektVerletztDieEindeutigkeit() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("run-1", Ausgang.SUCCESS, T0, List.of()), EINGANG);
    NeueMeldung nochmal = meldung("run-1", Ausgang.SUCCESS, T0, List.of());

    assertThatThrownBy(() -> store.fuegeEin(eigenesProjekt, TOKEN, nochmal, EINGANG))
        .isInstanceOf(DuplicateKeyException.class);
    store.fuegeEin(anderesProjekt, TOKEN, nochmal, EINGANG);
    assertThat(zeilen("deployment_attempt")).isEqualTo(2);
  }

  @Test
  void derselbeShaInZweiVersuchenWirdZweimalGespeichert() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("run-1", Ausgang.FAILURE, T0, List.of(EINS)), EINGANG);
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("run-2", Ausgang.SUCCESS, T0.plusSeconds(60), List.of(EINS, ZWEI)),
        EINGANG);

    Auslieferungsdaten daten = store.ladeAuswertung(eigenesProjekt, T0.minusSeconds(3600));

    assertThat(daten.commits())
        .containsExactlyInAnyOrder(
            new GemeldeterCommit(id("run-1"), EINS.sha(), EINS.committedAt()),
            new GemeldeterCommit(id("run-2"), EINS.sha(), EINS.committedAt()),
            new GemeldeterCommit(id("run-2"), ZWEI.sha(), ZWEI.committedAt()));
    assertThat(daten.versuche())
        .containsExactlyInAnyOrder(
            new Auslieferungsversuch(id("run-1"), "run-1", Ausgang.FAILURE, T0, "rev-run-1"),
            new Auslieferungsversuch(
                id("run-2"), "run-2", Ausgang.SUCCESS, T0.plusSeconds(60), "rev-run-2"));
  }

  @Test
  void ladenJeProjektLiefertNichtsAusEinemAnderenProjekt() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("run-1", Ausgang.SUCCESS, T0, List.of(EINS)), EINGANG);
    store.fuegeEin(
        anderesProjekt, TOKEN, meldung("run-2", Ausgang.SUCCESS, T0, List.of(ZWEI)), EINGANG);

    Auslieferungsdaten daten = store.ladeAuswertung(eigenesProjekt, T0.minusSeconds(3600));

    assertThat(daten.versuche())
        .extracting(Auslieferungsversuch::attemptId)
        .containsExactly("run-1");
    assertThat(daten.commits()).extracting(GemeldeterCommit::sha).containsExactly(EINS.sha());
    assertThat(store.letzterVersuch(anderesProjekt))
        .map(Auslieferungsversuch::attemptId)
        .contains("run-2");
    assertThat(store.ladeAuswertung(eigenesProjekt + anderesProjekt + 1, T0).versuche()).isEmpty();
  }

  @Test
  void ladenLaesstNurCommitsAusDieVorDemFensterAusgeliefertWurden() {
    Instant beginn = T0.plusSeconds(3600);
    Commit offen = new Commit("ccc333", T0);
    Commit spaet = new Commit("ddd444", T0);
    // EINS: vor dem Fenster gemeldet und ausgeliefert — faellt weg, auch mit spaeterer Meldung.
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("vorher", Ausgang.SUCCESS, T0, List.of(EINS)), EINGANG);
    // ZWEI und offen: vor dem Fenster gemeldet, aber gescheitert; ZWEI geht erst im Fenster raus.
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("rot", Ausgang.FAILURE, T0.plusSeconds(60), List.of(ZWEI, offen)),
        EINGANG);
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("im-fenster", Ausgang.SUCCESS, beginn, List.of(EINS, ZWEI, spaet)),
        EINGANG);

    Auslieferungsdaten daten = store.ladeAuswertung(eigenesProjekt, beginn);

    assertThat(daten.versuche()).hasSize(3);
    assertThat(daten.commits())
        .extracting(GemeldeterCommit::versuchId, GemeldeterCommit::sha)
        .containsExactlyInAnyOrder(
            tuple(id("rot"), ZWEI.sha()),
            tuple(id("rot"), offen.sha()),
            tuple(id("im-fenster"), ZWEI.sha()),
            tuple(id("im-fenster"), spaet.sha()));
  }

  @Test
  void letzterVersuchIstDerMitDemGroesstenFinishedAtGleichWelchenAusgangs() {
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("spaet", Ausgang.FAILURE, T0.plusSeconds(60), List.of()),
        EINGANG);
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("frueh", Ausgang.SUCCESS, T0, List.of()), EINGANG);

    assertThat(store.letzterVersuch(eigenesProjekt))
        .contains(
            new Auslieferungsversuch(
                id("spaet"), "spaet", Ausgang.FAILURE, T0.plusSeconds(60), "rev-spaet"));
  }

  @Test
  void beiGleichstandIstDerZuletztEingegangeneDerLetzte() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("erster", Ausgang.SUCCESS, T0, List.of()), EINGANG);
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("zweiter", Ausgang.FAILURE, T0, List.of()), EINGANG);

    assertThat(store.letzterVersuch(eigenesProjekt))
        .map(Auslieferungsversuch::attemptId)
        .contains("zweiter");
  }

  @Test
  void ohneVersuchIstDerLetzteLeer() {
    assertThat(store.letzterVersuch(eigenesProjekt)).isEmpty();
  }

  @Test
  void loeschenDesProjektsEntferntVersucheUndCommits() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("run-1", Ausgang.SUCCESS, T0, List.of(EINS, ZWEI)), EINGANG);
    store.fuegeEin(
        anderesProjekt, TOKEN, meldung("run-2", Ausgang.SUCCESS, T0, List.of(EINS)), EINGANG);

    jdbc.update("DELETE FROM project WHERE id = ?", eigenesProjekt);

    assertThat(zeilen("deployment_attempt")).isEqualTo(1);
    assertThat(zeilen("deployment_commit")).isEqualTo(1);
    assertThat(store.findeVersuch(eigenesProjekt, "run-1")).isEmpty();
  }
}
