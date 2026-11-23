package org.mwolff.manban.deployment.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.AbstractIntegrationTest;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.deployment.application.Auslieferungsdaten;
import org.mwolff.manban.deployment.application.DeploymentStore;
import org.mwolff.manban.deployment.application.GemeldeterStand;
import org.mwolff.manban.deployment.application.NeueMeldung;
import org.mwolff.manban.deployment.application.NeueMeldung.Commit;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.AuslieferungsErmittlung;
import org.mwolff.manban.deployment.domain.Auslieferungsart;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;
import org.mwolff.manban.deployment.domain.GemeldeterCommit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Die Zusagen des Ports {@link DeploymentStore} gegen echtes Postgres (Issue #1833, Plan #1831 E3,
 * E6, E12; Vorschau und Veröffentlichung seit Issue #2215, Plan #2206 E10).
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

  private static NeueMeldung meldung(
      String attemptId,
      Ausgang ausgang,
      Instant fertig,
      List<Commit> commits,
      @Nullable Auslieferungsart art,
      @Nullable String url) {
    return new NeueMeldung(attemptId, ausgang, fertig, "rev-" + attemptId, commits, art, url);
  }

  private static final String VORSCHAU_URL = "https://vorschau.example.org/7";
  private static final String SEITE_URL = "https://example.org";

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

  @Test
  void speichertArtUndAdresseUndLiestSieWieder() {
    NeueMeldung vorschau =
        meldung("v-1", Ausgang.SUCCESS, T0, List.of(EINS), Auslieferungsart.VORSCHAU, VORSCHAU_URL);
    NeueMeldung veroeffentlicht =
        meldung(
            "p-1", Ausgang.SUCCESS, T0, List.of(), Auslieferungsart.VEROEFFENTLICHUNG, SEITE_URL);

    store.fuegeEin(eigenesProjekt, TOKEN, vorschau, EINGANG);
    store.fuegeEin(eigenesProjekt, TOKEN, veroeffentlicht, EINGANG);

    assertThat(store.findeVersuch(eigenesProjekt, "v-1")).contains(vorschau);
    assertThat(store.findeVersuch(eigenesProjekt, "p-1")).contains(veroeffentlicht);
    assertThat(
            jdbc.queryForObject(
                "SELECT art FROM deployment_attempt WHERE attempt_id = 'v-1'", String.class))
        .isEqualTo("vorschau");
  }

  @Test
  void eineMeldungOhneArtBleibtOhneArtUndAdresse() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("run-1", Ausgang.SUCCESS, T0, List.of()), EINGANG);

    assertThat(store.findeVersuch(eigenesProjekt, "run-1"))
        .hasValueSatisfying(
            gelesen -> {
              assertThat(gelesen.art()).isNull();
              assertThat(gelesen.url()).isNull();
            });
  }

  @Test
  void dieDatenbankNimmtKeineUnbekannteArt() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO deployment_attempt (project_id, attempt_id, outcome, finished_at,"
                        + " revision, token_name, created_at, art) VALUES (?, 'x', 'SUCCESS',"
                        + " now(), 'r', 't', now(), 'probe')",
                    eigenesProjekt))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void nachEinerVorschauBleibtDieLetzteVeroeffentlichungDerLetzteStand() {
    store.fuegeEin(eigenesProjekt, TOKEN, meldung("alt", Ausgang.SUCCESS, T0, List.of()), EINGANG);
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung(
            "veroeffentlicht",
            Ausgang.FAILURE,
            T0.plusSeconds(60),
            List.of(),
            Auslieferungsart.VEROEFFENTLICHUNG,
            null),
        EINGANG);
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung(
            "vorschau",
            Ausgang.SUCCESS,
            T0.plusSeconds(120),
            List.of(),
            Auslieferungsart.VORSCHAU,
            VORSCHAU_URL),
        EINGANG);

    assertThat(store.letzterVersuch(eigenesProjekt))
        .map(Auslieferungsversuch::attemptId)
        .contains("veroeffentlicht");
  }

  @Test
  void nurEineVorschauErgibtKeinenLetztenStand() {
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("vorschau", Ausgang.SUCCESS, T0, List.of(), Auslieferungsart.VORSCHAU, null),
        EINGANG);

    assertThat(store.letzterVersuch(eigenesProjekt)).isEmpty();
  }

  @Test
  void einCommitAusEinerVorschauZaehltBeiDerVeroeffentlichungFuerDieDurchlaufzeit() {
    Instant beginn = T0.plusSeconds(3600);
    Instant veroeffentlicht = T0.plus(Duration.ofDays(1));
    // Die Vorschau meldet EINS vor dem Fenster, erfolgreich — sie liefert ihn nicht aus.
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("vorschau", Ausgang.SUCCESS, T0, List.of(EINS), Auslieferungsart.VORSCHAU, null),
        EINGANG);
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("veroeffentlicht", Ausgang.SUCCESS, veroeffentlicht, List.of(EINS)),
        EINGANG);

    Auslieferungsdaten daten = store.ladeAuswertung(eigenesProjekt, beginn);

    assertThat(daten.commits())
        .extracting(GemeldeterCommit::versuchId, GemeldeterCommit::sha)
        .containsExactly(tuple(id("veroeffentlicht"), EINS.sha()));
    assertThat(
            AuslieferungsErmittlung.ermittle(daten.versuche(), daten.commits(), EINGANG)
                .leadTimeMedianSeconds())
        .isEqualTo(Duration.between(EINS.committedAt(), veroeffentlicht).toSeconds());
  }

  @Test
  void juengsterNenntJeArtDieSpaetesteMeldungMitAdresse() {
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("v-alt", Ausgang.SUCCESS, T0, List.of(), Auslieferungsart.VORSCHAU, VORSCHAU_URL),
        EINGANG);
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung(
            "v-neu",
            Ausgang.FAILURE,
            T0.plusSeconds(60),
            List.of(),
            Auslieferungsart.VORSCHAU,
            VORSCHAU_URL + "1"),
        EINGANG.plusSeconds(1));
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung(
            "p-explizit",
            Ausgang.SUCCESS,
            T0.minusSeconds(60),
            List.of(),
            Auslieferungsart.VEROEFFENTLICHUNG,
            SEITE_URL),
        EINGANG);
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("p-ohne-art", Ausgang.SUCCESS, T0, List.of()), EINGANG);
    store.fuegeEin(
        anderesProjekt,
        TOKEN,
        meldung(
            "v-fremd",
            Ausgang.SUCCESS,
            T0.plusSeconds(600),
            List.of(),
            Auslieferungsart.VORSCHAU,
            VORSCHAU_URL),
        EINGANG);

    assertThat(store.juengster(eigenesProjekt, Auslieferungsart.VORSCHAU))
        .contains(
            new GemeldeterStand(
                "rev-v-neu", VORSCHAU_URL + "1", T0.plusSeconds(60), EINGANG.plusSeconds(1)));
    assertThat(store.juengster(eigenesProjekt, Auslieferungsart.VEROEFFENTLICHUNG))
        .contains(new GemeldeterStand("rev-p-ohne-art", null, T0, EINGANG));
  }

  @Test
  void juengsterBeiGleichstandIstDieZuletztEingegangene() {
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("erste", Ausgang.SUCCESS, T0, List.of(), Auslieferungsart.VORSCHAU, null),
        EINGANG);
    store.fuegeEin(
        eigenesProjekt,
        TOKEN,
        meldung("zweite", Ausgang.SUCCESS, T0, List.of(), Auslieferungsart.VORSCHAU, null),
        EINGANG);

    assertThat(store.juengster(eigenesProjekt, Auslieferungsart.VORSCHAU))
        .map(GemeldeterStand::revision)
        .contains("rev-zweite");
  }

  @Test
  void juengsterOhneMeldungIstLeer() {
    store.fuegeEin(
        eigenesProjekt, TOKEN, meldung("run-1", Ausgang.SUCCESS, T0, List.of()), EINGANG);

    assertThat(store.juengster(eigenesProjekt, Auslieferungsart.VORSCHAU)).isEmpty();
    assertThat(store.juengster(anderesProjekt, Auslieferungsart.VEROEFFENTLICHUNG)).isEmpty();
  }
}
