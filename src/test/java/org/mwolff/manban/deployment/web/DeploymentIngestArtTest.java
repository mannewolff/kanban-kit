package org.mwolff.manban.deployment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.deployment.application.DeploymentAttemptConflictException;
import org.mwolff.manban.deployment.application.DeploymentService;
import org.mwolff.manban.deployment.application.DeploymentStore;
import org.mwolff.manban.deployment.application.Meldeergebnis;
import org.mwolff.manban.deployment.application.NeueMeldung;
import org.mwolff.manban.deployment.domain.Ausgang;
import org.mwolff.manban.deployment.domain.Auslieferungsart;
import org.mwolff.manban.deployment.web.DeploymentIngestController.DeploymentCommitRequest;
import org.mwolff.manban.deployment.web.DeploymentIngestController.DeploymentRequest;
import org.mwolff.manban.project.application.PermissionChecker;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Art und Adresse einer Meldung an der Melde-Schnittstelle (Issue #2215, Plan #2206 E10):
 * Abbildung, Grenzen und der Konflikt einer wiederholten Meldung mit anderer Art. Aus {@code
 * DeploymentIngestControllerTest} ausgelagert, damit beide unter der Import-Schwelle bleiben.
 */
class DeploymentIngestArtTest {

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

  private static Authentication gebunden() {
    TestingAuthenticationToken auth = new TestingAuthenticationToken("token", null, List.of());
    auth.setDetails(new KanbanPrincipal(1L, 2L, 42L, 7L, "deploy"));
    return auth;
  }

  private static DeploymentRequest mitArt(@Nullable String art, @Nullable String url) {
    return new DeploymentRequest(
        "lauf-1",
        Ausgang.SUCCESS,
        ENDE,
        "abc123",
        List.of(new DeploymentCommitRequest("abc123", COMMIT)),
        art,
        url);
  }

  @Test
  void artUndAdresseGehenAnDenDienst() {
    when(service.melden(eq(1L), eq(42L), eq("deploy"), any())).thenReturn(Meldeergebnis.CREATED);

    controller.melden(gebunden(), mitArt("vorschau", "https://vorschau.example.org/42"));
    controller.melden(gebunden(), mitArt("veroeffentlichung", null));

    ArgumentCaptor<NeueMeldung> meldung = ArgumentCaptor.forClass(NeueMeldung.class);
    verify(service, times(2)).melden(eq(1L), eq(42L), eq("deploy"), meldung.capture());
    assertThat(meldung.getAllValues())
        .extracting(NeueMeldung::art, NeueMeldung::url)
        .containsExactly(
            tuple(Auslieferungsart.VORSCHAU, "https://vorschau.example.org/42"),
            tuple(Auslieferungsart.VEROEFFENTLICHUNG, null));
  }

  @Test
  void dieselbeKennungMitAndererArtEndetMit409() {
    DeploymentStore store = mock(DeploymentStore.class);
    DeploymentService echt =
        new DeploymentService(
            store, mock(PermissionChecker.class), Clock.fixed(ENDE, ZoneOffset.UTC));
    when(store.findeVersuch(42L, "lauf-1"))
        .thenReturn(
            Optional.of(
                new NeueMeldung(
                    "lauf-1",
                    Ausgang.SUCCESS,
                    ENDE,
                    "abc123",
                    List.of(new NeueMeldung.Commit("abc123", COMMIT)))));
    DeploymentIngestController mitDienst = new DeploymentIngestController(echt);

    assertThat(mitDienst.melden(gebunden(), mitArt(null, null)).getStatusCode())
        .isEqualTo(HttpStatus.OK);
    assertThatThrownBy(() -> mitDienst.melden(gebunden(), mitArt("vorschau", null)))
        .isInstanceOf(DeploymentAttemptConflictException.class);
    assertThat(DeploymentAttemptConflictException.class.getAnnotation(ResponseStatus.class).value())
        .isEqualTo(HttpStatus.CONFLICT);
  }

  @Test
  void artUndAdresseSindOptional() {
    assertThat(VALIDATOR.validate(mitArt(null, null))).isEmpty();
    assertThat(VALIDATOR.validate(mitArt("vorschau", "https://vorschau.example.org/42"))).isEmpty();
    assertThat(VALIDATOR.validate(mitArt("veroeffentlichung", "https://example.org"))).isEmpty();
  }

  @Test
  void eineUnbekannteArtIstUngueltig() {
    assertThat(felder(VALIDATOR.validate(mitArt("probe", null)))).containsExactly("art");
    assertThat(felder(VALIDATOR.validate(mitArt("VORSCHAU", null)))).containsExactly("art");
    assertThat(felder(VALIDATOR.validate(mitArt("", null)))).containsExactly("art");
  }

  @Test
  void eineAdresseOhneHttpsIstUngueltig() {
    assertThat(felder(VALIDATOR.validate(mitArt(null, "http://example.org"))))
        .containsExactly("url");
    assertThat(felder(VALIDATOR.validate(mitArt(null, "javascript:alert(1)"))))
        .containsExactly("url");
    assertThat(felder(VALIDATOR.validate(mitArt(null, "https://")))).containsExactly("url");
    assertThat(felder(VALIDATOR.validate(mitArt(null, "https://a b")))).containsExactly("url");
    assertThat(felder(VALIDATOR.validate(mitArt(null, " https://example.org"))))
        .containsExactly("url");
  }

  @Test
  void dieAdresseHatDieLaengeDerSpalte() {
    String grenze = "https://e.org/" + "x".repeat(DeploymentIngestController.URL_MAX - 14);

    assertThat(VALIDATOR.validate(mitArt(null, grenze))).isEmpty();
    assertThat(felder(VALIDATOR.validate(mitArt(null, grenze + "x")))).containsExactly("url");
  }

  private static List<String> felder(Set<ConstraintViolation<DeploymentRequest>> verstoesse) {
    return verstoesse.stream().map(v -> v.getPropertyPath().toString()).toList();
  }
}
