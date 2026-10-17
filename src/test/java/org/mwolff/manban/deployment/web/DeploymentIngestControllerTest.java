package org.mwolff.manban.deployment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.deployment.application.DeploymentService;
import org.mwolff.manban.deployment.application.Meldeergebnis;
import org.mwolff.manban.deployment.application.NeueMeldung;
import org.mwolff.manban.deployment.application.TokenNotBoundForDeploymentException;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.Auslieferungsversuch;
import org.mwolff.manban.deployment.web.DeploymentIngestController.DeploymentCommitRequest;
import org.mwolff.manban.deployment.web.DeploymentIngestController.DeploymentLatestResponse;
import org.mwolff.manban.deployment.web.DeploymentIngestController.DeploymentRequest;
import org.mwolff.manban.deployment.web.DeploymentIngestController.DeploymentResponse;
import org.mwolff.manban.deployment.web.DeploymentIngestController.DeploymentStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

/**
 * Abbildung von Anfrage und Antwort der Melde-Schnittstelle (Issue #1834, Plan #1831 E2, E4) und
 * die Pfade, die der Integrationstest nicht erreicht: Ein Aufruf ohne Kanban-Anmeldung kommt dort
 * nie an, der Controller fängt ihn trotzdem ab.
 */
class DeploymentIngestControllerTest {

  private static final Instant ENDE = Instant.parse("2026-10-17T12:00:00Z");
  private static final Instant COMMIT = Instant.parse("2026-10-17T09:30:00Z");

  private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
  private static final Validator VALIDATOR = FACTORY.getValidator();

  private final DeploymentService service = mock(DeploymentService.class);
  private final DeploymentIngestController controller = new DeploymentIngestController(service);

  @AfterAll
  static void schliessen() {
    FACTORY.close();
  }

  private static Authentication mitPrincipal(KanbanPrincipal principal) {
    TestingAuthenticationToken auth = new TestingAuthenticationToken("token", null, List.of());
    auth.setDetails(principal);
    return auth;
  }

  private static Authentication gebunden() {
    return mitPrincipal(new KanbanPrincipal(1L, 2L, 42L, 7L, "deploy"));
  }

  private static DeploymentRequest anfrage(List<DeploymentCommitRequest> commits) {
    return new DeploymentRequest("lauf-1", Ausgang.SUCCESS, ENDE, "abc123", commits);
  }

  private static DeploymentRequest anfrage() {
    return anfrage(List.of(new DeploymentCommitRequest("abc123", COMMIT)));
  }

  @Test
  void ohneAnmeldungWirdAbgewiesen_undDerDienstBleibtUnberuehrt() {
    assertThatThrownBy(() -> controller.melden(null, anfrage()))
        .isInstanceOf(TokenNotBoundForDeploymentException.class);
    assertThatThrownBy(() -> controller.latest(null))
        .isInstanceOf(TokenNotBoundForDeploymentException.class);

    verifyNoInteractions(service);
  }

  @Test
  void eineAnmeldungOhneKanbanTokenWirdAbgewiesen() {
    Authentication fremd = new TestingAuthenticationToken("wer", "auch", List.of());

    assertThatThrownBy(() -> controller.melden(fremd, anfrage()))
        .isInstanceOf(TokenNotBoundForDeploymentException.class);
    assertThatThrownBy(() -> controller.latest(fremd))
        .isInstanceOf(TokenNotBoundForDeploymentException.class);

    verifyNoInteractions(service);
  }

  @Test
  void einTokenOhneBoardBindungWirdAbgewiesen() {
    Authentication ungebunden = mitPrincipal(new KanbanPrincipal(1L, 2L, null, null, "frei"));

    assertThatThrownBy(() -> controller.melden(ungebunden, anfrage()))
        .isInstanceOf(TokenNotBoundForDeploymentException.class);
    assertThatThrownBy(() -> controller.latest(ungebunden))
        .isInstanceOf(TokenNotBoundForDeploymentException.class);

    verifyNoInteractions(service);
  }

  @Test
  void eineNeueMeldungAntwortet201MitCreated_undGehtMitInhaberProjektUndTokenNameAnDenDienst() {
    when(service.melden(eq(1L), eq(42L), eq("deploy"), any())).thenReturn(Meldeergebnis.CREATED);

    ResponseEntity<DeploymentResponse> antwort = controller.melden(gebunden(), anfrage());

    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(antwort.getBody())
        .isEqualTo(new DeploymentResponse("lauf-1", DeploymentStatus.CREATED));
    ArgumentCaptor<NeueMeldung> meldung = ArgumentCaptor.forClass(NeueMeldung.class);
    verify(service).melden(eq(1L), eq(42L), eq("deploy"), meldung.capture());
    assertThat(meldung.getValue())
        .isEqualTo(
            new NeueMeldung(
                "lauf-1",
                Ausgang.SUCCESS,
                ENDE,
                "abc123",
                List.of(new NeueMeldung.Commit("abc123", COMMIT))));
  }

  @Test
  void eineUnveraenderteWiederholungAntwortet200MitUnchanged() {
    when(service.melden(eq(1L), eq(42L), eq("deploy"), any())).thenReturn(Meldeergebnis.UNCHANGED);

    ResponseEntity<DeploymentResponse> antwort = controller.melden(gebunden(), anfrage());

    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody())
        .isEqualTo(new DeploymentResponse("lauf-1", DeploymentStatus.UNCHANGED));
  }

  /** Ein doppelt gemeldeter Commit ist derselbe Commit; er verletzte sonst den Primärschlüssel. */
  @Test
  void einDoppeltGemeldeterCommitGehtNurEinmalAnDenDienst() {
    when(service.melden(eq(1L), eq(42L), eq("deploy"), any())).thenReturn(Meldeergebnis.CREATED);
    DeploymentCommitRequest commit = new DeploymentCommitRequest("abc123", COMMIT);

    controller.melden(gebunden(), anfrage(List.of(commit, commit)));

    ArgumentCaptor<NeueMeldung> meldung = ArgumentCaptor.forClass(NeueMeldung.class);
    verify(service).melden(eq(1L), eq(42L), eq("deploy"), meldung.capture());
    assertThat(meldung.getValue().commits())
        .containsExactly(new NeueMeldung.Commit("abc123", COMMIT));
  }

  @Test
  void derLetzteStandKommtMit200UndRevisionAusgangUndZeitpunkt() {
    when(service.letzterStand(1L, 42L))
        .thenReturn(
            Optional.of(new Auslieferungsversuch(9L, "lauf-1", Ausgang.FAILURE, ENDE, "abc123")));

    ResponseEntity<DeploymentLatestResponse> antwort = controller.latest(gebunden());

    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody())
        .isEqualTo(new DeploymentLatestResponse("abc123", Ausgang.FAILURE, ENDE));
  }

  @Test
  void ohneGemeldetenVersuchAntwortetDerLetzteStandMit204OhneRumpf() {
    when(service.letzterStand(1L, 42L)).thenReturn(Optional.empty());

    ResponseEntity<DeploymentLatestResponse> antwort = controller.latest(gebunden());

    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(antwort.getBody()).isNull();
  }

  @Test
  void eineVollstaendigeMeldungIstGueltig() {
    assertThat(VALIDATOR.validate(anfrage())).isEmpty();
  }

  @Test
  void eineMeldungOhneCommitsIstGueltig() {
    assertThat(VALIDATOR.validate(anfrage(List.of()))).isEmpty();
  }

  @Test
  void jedesPflichtfeldFehltBenannt() {
    DeploymentRequest leer = new DeploymentRequest(null, null, null, null, null);

    assertThat(felder(VALIDATOR.validate(leer)))
        .containsExactlyInAnyOrder("attemptId", "outcome", "finishedAt", "revision", "commits");
  }

  @Test
  void leereKennungUndLeererStandSindUngueltig() {
    DeploymentRequest leer = new DeploymentRequest(" ", Ausgang.SUCCESS, ENDE, "", List.of());

    assertThat(felder(VALIDATOR.validate(leer))).containsExactlyInAnyOrder("attemptId", "revision");
  }

  @Test
  void dieLaengengrenzenFolgenDenSpaltenDerMigration() {
    String kennung = "k".repeat(DeploymentIngestController.ATTEMPT_ID_MAX);
    String sha = "a".repeat(DeploymentIngestController.SHA_MAX);
    DeploymentRequest grenze =
        new DeploymentRequest(
            kennung, Ausgang.SUCCESS, ENDE, sha, List.of(new DeploymentCommitRequest(sha, COMMIT)));
    DeploymentRequest drueber =
        new DeploymentRequest(
            kennung + "k",
            Ausgang.SUCCESS,
            ENDE,
            sha + "a",
            List.of(new DeploymentCommitRequest(sha + "a", COMMIT)));

    assertThat(VALIDATOR.validate(grenze)).isEmpty();
    assertThat(felder(VALIDATOR.validate(drueber)))
        .containsExactlyInAnyOrder("attemptId", "revision", "commits[0].sha");
  }

  @Test
  void einCommitOhneShaOderZeitpunktIstUngueltig() {
    DeploymentRequest ohne = anfrage(Arrays.asList(new DeploymentCommitRequest(null, null), null));

    assertThat(felder(VALIDATOR.validate(ohne)))
        .containsExactlyInAnyOrder(
            "commits[0].sha", "commits[0].committedAt", "commits[1].<list element>");
  }

  @Test
  void hoechstensMaxCommitsJeMeldung() {
    List<DeploymentCommitRequest> genau =
        IntStream.range(0, DeploymentIngestController.MAX_COMMITS)
            .mapToObj(i -> new DeploymentCommitRequest("sha" + i, COMMIT))
            .toList();
    List<DeploymentCommitRequest> mehr =
        IntStream.rangeClosed(0, DeploymentIngestController.MAX_COMMITS)
            .mapToObj(i -> new DeploymentCommitRequest("sha" + i, COMMIT))
            .toList();

    assertThat(VALIDATOR.validate(anfrage(genau))).isEmpty();
    assertThat(felder(VALIDATOR.validate(anfrage(mehr)))).containsExactly("commits");
  }

  private static List<String> felder(Set<ConstraintViolation<DeploymentRequest>> verstoesse) {
    return verstoesse.stream().map(v -> v.getPropertyPath().toString()).toList();
  }
}
