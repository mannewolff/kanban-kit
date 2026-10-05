package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.web.api.ApiSchemas;
import org.mwolff.manban.common.web.api.ApiVertrag;
import org.mwolff.manban.common.web.api.Stabilitaet;
import org.springframework.web.method.HandlerMethod;

/**
 * Anreicherung der OpenAPI-Beschreibung (Issue #1402, Plan #1400): Stabilität, Recht und
 * Abkündigung je Aufruf aus {@link ApiVertrag}, Zugang je Pfad aus {@link ApiZugang}, die
 * automatischen 401/403 und das gemeinsame Fehlerschema.
 */
class ApiBeschreibungsAnreicherungTest {

  private final ApiBeschreibungsAnreicherung anreicherung = new ApiBeschreibungsAnreicherung();

  /** Träger der Annotationen, die die Tests auswerten — kein echter Controller. */
  static final class Beispiele {
    void ohneVertrag() {}

    @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH)
    void verlaesslich() {}

    @ApiVertrag
    void nurVorgaben() {}

    @ApiVertrag(stabilitaet = Stabilitaet.VERLAESSLICH, abgekuendigtSeit = "2.20")
    void abgekuendigt() {}

    @ApiVertrag(abgekuendigtSeit = "2")
    void abgekuendigtOhneMinor() {}

    @ApiVertrag(abgekuendigtSeit = "2.x")
    void abgekuendigtMitBuchstabe() {}

    @ApiVertrag(recht = "CARD_EDIT")
    void mitRecht() {}
  }

  private Operation customize(String methode, Operation operation) {
    try {
      Method method = Beispiele.class.getDeclaredMethod(methode);
      return anreicherung.customize(operation, new HandlerMethod(new Beispiele(), method));
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  // --- OperationCustomizer: @ApiVertrag ------------------------------------------------------

  @Test
  void ohneAnnotationGiltAenderbarOhneRechtUndOhneVorspann() {
    Operation operation = customize("ohneVertrag", new Operation().description("Zweck."));

    assertThat(operation.getExtensions())
        .containsEntry(ApiBeschreibungsAnreicherung.X_STABILITAET, "aenderbar")
        .doesNotContainKey(ApiBeschreibungsAnreicherung.X_ERFORDERLICHES_RECHT);
    assertThat(operation.getDeprecated()).isNull();
    assertThat(operation.getDescription()).isEqualTo("Zweck.");
  }

  @Test
  void annotationMitVorgabenGiltAenderbar() {
    Operation operation = customize("nurVorgaben", new Operation());

    assertThat(operation.getExtensions())
        .containsEntry(ApiBeschreibungsAnreicherung.X_STABILITAET, "aenderbar")
        .doesNotContainKey(ApiBeschreibungsAnreicherung.X_ERFORDERLICHES_RECHT);
    assertThat(operation.getDeprecated()).isNull();
    assertThat(operation.getDescription()).isNull();
  }

  @Test
  void verlaesslichSchreibtExtensionUndStelltDieZeileVoran() {
    Operation operation = customize("verlaesslich", new Operation().description("Zweck."));

    assertThat(operation.getExtensions())
        .containsEntry(ApiBeschreibungsAnreicherung.X_STABILITAET, "verlaesslich");
    assertThat(operation.getDeprecated()).isNull();
    assertThat(operation.getDescription()).isEqualTo("Stabilität: verlässlich.\n\nZweck.");
  }

  @Test
  void verlaesslichOhneBeschreibungTraegtNurDieZeile() {
    Operation operation = customize("verlaesslich", new Operation());

    assertThat(operation.getDescription()).isEqualTo("Stabilität: verlässlich.");
  }

  @Test
  void abgekuendigtSetztDeprecatedUndNenntDieFruehesteMinorVersion() {
    Operation operation = customize("abgekuendigt", new Operation().description("Zweck."));

    assertThat(operation.getDeprecated()).isTrue();
    assertThat(operation.getExtensions())
        .containsEntry(ApiBeschreibungsAnreicherung.X_STABILITAET, "verlaesslich");
    assertThat(operation.getDescription())
        .isEqualTo("Abgekündigt seit 2.20 — entfällt frühestens mit 2.21.\n\nZweck.");
  }

  @Test
  void abgekuendigtOhneMinorVersionIstEinProgrammfehler() {
    Operation operation = new Operation();

    assertThatThrownBy(() -> customize("abgekuendigtOhneMinor", operation))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'2'");
  }

  @Test
  void abgekuendigtMitBuchstabenIstEinProgrammfehler() {
    Operation operation = new Operation();

    assertThatThrownBy(() -> customize("abgekuendigtMitBuchstabe", operation))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("'2.x'");
  }

  @Test
  void rechtLandetInDerExtension() {
    Operation operation = customize("mitRecht", new Operation());

    assertThat(operation.getExtensions())
        .containsEntry(ApiBeschreibungsAnreicherung.X_ERFORDERLICHES_RECHT, "CARD_EDIT")
        .containsEntry(ApiBeschreibungsAnreicherung.X_STABILITAET, "aenderbar");
  }

  // --- OpenApiCustomizer: Zugang, 401/403, ProblemDetail -------------------------------------

  private static OpenAPI spezifikation(String pfad, PathItem item) {
    return new OpenAPI().paths(new Paths().addPathItem(pfad, item));
  }

  @Test
  void oeffentlicherPfadHatKeineSicherheitUndKeine401oder403() {
    Operation post = new Operation();
    OpenAPI api = spezifikation("/api/auth/login", new PathItem().post(post));

    anreicherung.customise(api);

    assertThat(post.getSecurity()).isEmpty();
    assertThat(post.getExtensions())
        .containsEntry(ApiBeschreibungsAnreicherung.X_ZUGANG, "oeffentlich");
    assertThat(post.getResponses()).isNull();
  }

  @Test
  void nurSessionVerlangtDasSessionCookieUndNennt401und403() {
    Operation get = new Operation();
    OpenAPI api = spezifikation("/api/admin/users", new PathItem().get(get));

    anreicherung.customise(api);

    assertThat(get.getSecurity())
        .containsExactly(
            new SecurityRequirement().addList(ApiBeschreibungsAnreicherung.SESSION_COOKIE));
    assertThat(get.getExtensions())
        .containsEntry(ApiBeschreibungsAnreicherung.X_ZUGANG, "nur-session");
    assertThat(get.getResponses())
        .containsKeys(
            ApiBeschreibungsAnreicherung.UNAUTHORIZED, ApiBeschreibungsAnreicherung.FORBIDDEN);
    assertThat(get.getResponses().get(ApiBeschreibungsAnreicherung.UNAUTHORIZED).getDescription())
        .isEqualTo(ApiBeschreibungsAnreicherung.BESCHREIBUNG_401);
    assertThat(get.getResponses().get(ApiBeschreibungsAnreicherung.FORBIDDEN).getDescription())
        .isEqualTo(ApiBeschreibungsAnreicherung.BESCHREIBUNG_403);
  }

  @Test
  void nurProjektTokenVerlangtDasToken() {
    Operation get = new Operation();
    OpenAPI api = spezifikation("/api/kanban/items", new PathItem().get(get));

    anreicherung.customise(api);

    assertThat(get.getSecurity())
        .containsExactly(
            new SecurityRequirement().addList(ApiBeschreibungsAnreicherung.PROJEKT_TOKEN));
    assertThat(get.getExtensions())
        .containsEntry(ApiBeschreibungsAnreicherung.X_ZUGANG, "nur-projekt-token");
    assertThat(get.getResponses())
        .containsKeys(
            ApiBeschreibungsAnreicherung.UNAUTHORIZED, ApiBeschreibungsAnreicherung.FORBIDDEN);
  }

  @Test
  void uebrigeApiNimmtSessionOderTokenAlsAlternativen() {
    Operation get = new Operation();
    Operation post = new Operation();
    OpenAPI api = spezifikation("/api/cards/{cardId}", new PathItem().get(get).post(post));

    anreicherung.customise(api);

    for (Operation operation : new Operation[] {get, post}) {
      assertThat(operation.getSecurity())
          .containsExactly(
              new SecurityRequirement().addList(ApiBeschreibungsAnreicherung.SESSION_COOKIE),
              new SecurityRequirement().addList(ApiBeschreibungsAnreicherung.PROJEKT_TOKEN));
      assertThat(operation.getExtensions())
          .containsEntry(ApiBeschreibungsAnreicherung.X_ZUGANG, "session-oder-ungebundenes-token");
      assertThat(operation.getResponses())
          .containsKeys(
              ApiBeschreibungsAnreicherung.UNAUTHORIZED, ApiBeschreibungsAnreicherung.FORBIDDEN);
    }
  }

  @Test
  void vorhandeneAntwortenBleibenUnveraendert() {
    ApiResponse eigene403 = new ApiResponse().description("Kein Mitglied des Projekts.");
    ApiResponse ok = new ApiResponse().description("OK");
    Operation get =
        new Operation()
            .responses(
                new ApiResponses()
                    .addApiResponse("200", ok)
                    .addApiResponse(ApiBeschreibungsAnreicherung.FORBIDDEN, eigene403));
    OpenAPI api = spezifikation("/api/projects/{projectId}", new PathItem().get(get));

    anreicherung.customise(api);

    assertThat(get.getResponses().get("200")).isSameAs(ok);
    assertThat(get.getResponses().get(ApiBeschreibungsAnreicherung.FORBIDDEN)).isSameAs(eigene403);
    assertThat(get.getResponses().get(ApiBeschreibungsAnreicherung.UNAUTHORIZED).getDescription())
        .isEqualTo(ApiBeschreibungsAnreicherung.BESCHREIBUNG_401);
  }

  @Test
  void spezifikationOhnePfadeBekommtNurDasFehlerschema() {
    OpenAPI api = new OpenAPI();

    anreicherung.customise(api);

    assertThat(api.getPaths()).isNull();
    assertThat(api.getComponents().getSchemas()).containsKey(ApiSchemas.PROBLEM_DETAIL);
  }

  @Test
  // Schema#getProperties liefert Map<String, Schema> mit rohem Schema (swagger-models).
  @SuppressWarnings("rawtypes")
  void registriertProblemDetailNachRfc9457MitFieldErrors() {
    Schema<?> vorhanden = new Schema<>().description("anderes Schema");
    OpenAPI api = new OpenAPI().components(new Components().addSchemas("Anderes", vorhanden));

    anreicherung.customise(api);

    assertThat(api.getComponents().getSchemas()).containsEntry("Anderes", vorhanden);
    Schema<?> problem = api.getComponents().getSchemas().get(ApiSchemas.PROBLEM_DETAIL);
    assertThat(problem.getTypes()).containsExactly("object");
    assertThat(problem.getDescription()).contains("RFC 9457");
    java.util.Map<String, Schema> felder = problem.getProperties();
    assertThat(felder)
        .containsOnlyKeys("type", "title", "status", "detail", "instance", "fieldErrors");
    Schema<?> type = felder.get("type");
    Schema<?> status = felder.get("status");
    Schema<?> title = felder.get("title");
    Schema<?> detail = felder.get("detail");
    Schema<?> instance = felder.get("instance");
    assertThat(type.getTypes()).containsExactly("string");
    assertThat(type.getFormat()).isEqualTo("uri");
    assertThat(status.getTypes()).containsExactly("integer");
    assertThat(status.getFormat()).isEqualTo("int32");
    assertThat(title.getTypes()).containsExactly("string");
    assertThat(detail.getTypes()).containsExactly("string");
    assertThat(instance.getTypes()).containsExactly("string");
    assertThat(instance.getFormat()).isEqualTo("uri");
    Schema<?> fieldErrors = felder.get("fieldErrors");
    assertThat(fieldErrors.getTypes()).containsExactly("object");
    assertThat(fieldErrors.getAdditionalProperties()).isInstanceOf(Schema.class);
    assertThat(((Schema<?>) fieldErrors.getAdditionalProperties()).getTypes())
        .containsExactly("string");
    for (Schema<?> feld : felder.values()) {
      assertThat(feld.getDescription()).isNotBlank();
    }
  }

  @Test
  void schemaVerweisZeigtAufDasRegistrierteSchema() {
    assertThat(ApiSchemas.PROBLEM_DETAIL_REF)
        .isEqualTo("#/components/schemas/" + ApiSchemas.PROBLEM_DETAIL);
    assertThat(ApiSchemas.PROBLEM_JSON).isEqualTo("application/problem+json");
  }

  @Test
  void stabilitaetTraegtIhreKennung() {
    assertThat(Stabilitaet.VERLAESSLICH.kennung()).isEqualTo("verlaesslich");
    assertThat(Stabilitaet.AENDERBAR.kennung()).isEqualTo("aenderbar");
  }
}
