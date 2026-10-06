package org.mwolff.manban.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.config.ApiZugang.Zugangsart;

/**
 * Gegenproben zu den Prüfroutinen in {@link OpenApiPflichtpruefung} (Issue #1409): Eine
 * Spezifikation mit einer Lücke wird abgewiesen, eine vollständige nicht.
 */
class OpenApiPflichtpruefungTest {

  private static final String PFAD = "/api/beispiel";

  /** Eine Spezifikation mit genau einer Operation, die alle Pflichtangaben trägt. */
  private static OpenAPI vollstaendig() {
    Operation op =
        new Operation()
            .addTagsItem("Beispiel")
            .summary("Beispiel lesen")
            .description("Liest das Beispiel.")
            .responses(
                new ApiResponses()
                    .addApiResponse("200", new ApiResponse().description("ok"))
                    .addApiResponse("404", new ApiResponse().description("fehlt")));
    op.addExtension(ApiBeschreibungsAnreicherung.X_STABILITAET, "aenderbar");
    op.addExtension(ApiBeschreibungsAnreicherung.X_ZUGANG, Zugangsart.NUR_SESSION.kennung());
    OpenAPI spec = new OpenAPI().paths(new Paths());
    spec.getPaths().addPathItem(PFAD, new PathItem().get(op));
    return spec;
  }

  private static Operation operation(OpenAPI spec) {
    return spec.getPaths().get(PFAD).getGet();
  }

  @Test
  void vollstaendigeOperationBesteht() {
    OpenAPI spec = vollstaendig();
    operation(spec)
        .addExtension(
            ApiBeschreibungsAnreicherung.X_ERFORDERLICHES_RECHT,
            OpenApiPflichtpruefung.PLATTFORM_ADMIN);

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec)).isEmpty();
  }

  /** Gegenprobe: Eine Operation ohne Summary wird abgewiesen. */
  @Test
  void operationOhneSummaryWirdAbgewiesen() {
    OpenAPI spec = vollstaendig();
    operation(spec).setSummary(null);

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec))
        .containsExactly("GET /api/beispiel: Summary fehlt");
  }

  /** Gegenprobe: Ein Recht, das es nicht gibt, wird abgewiesen. */
  @Test
  void unbekanntesRechtWirdAbgewiesen() {
    OpenAPI spec = vollstaendig();
    operation(spec).addExtension(ApiBeschreibungsAnreicherung.X_ERFORDERLICHES_RECHT, "GIBTSNICHT");

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec))
        .containsExactly("GET /api/beispiel: unbekanntes Recht GIBTSNICHT");
  }

  @Test
  void projektRechtWirdAngenommen() {
    OpenAPI spec = vollstaendig();
    operation(spec)
        .addExtension(ApiBeschreibungsAnreicherung.X_ERFORDERLICHES_RECHT, "TICKET_UPDATE");

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec)).isEmpty();
  }

  /** Gegenprobe: Der Tag, den springdoc ohne {@code @Tag} vergibt, zählt nicht. */
  @Test
  void klassennameAlsTagWirdAbgewiesen() {
    OpenAPI spec = vollstaendig();
    operation(spec).setTags(List.of("beispiel-controller"));

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec))
        .containsExactly("GET /api/beispiel: Tag fehlt");
  }

  @Test
  void fehlendeAngabenWerdenEinzelnGenannt() {
    OpenAPI spec = new OpenAPI().paths(new Paths());
    spec.getPaths().addPathItem(PFAD, new PathItem().post(new Operation()));

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec))
        .containsExactly(
            "POST /api/beispiel: Tag fehlt",
            "POST /api/beispiel: Summary fehlt",
            "POST /api/beispiel: Beschreibung fehlt",
            "POST /api/beispiel: x-stabilitaet fehlt",
            "POST /api/beispiel: keine 4xx-Antwort beschrieben");
  }

  @Test
  void oeffentlicherAufrufOhneEingabeBrauchtKeine4xxAntwort() {
    OpenAPI spec = vollstaendig();
    Operation op = operation(spec);
    op.setResponses(new ApiResponses().addApiResponse("204", new ApiResponse().description("ok")));
    op.addExtension(ApiBeschreibungsAnreicherung.X_ZUGANG, Zugangsart.OEFFENTLICH.kennung());

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec)).isEmpty();
  }

  @Test
  void oeffentlicherAufrufMitEingabeBrauchtEine4xxAntwort() {
    OpenAPI spec = vollstaendig();
    Operation op = operation(spec);
    op.setResponses(new ApiResponses().addApiResponse("204", new ApiResponse().description("ok")));
    op.addExtension(ApiBeschreibungsAnreicherung.X_ZUGANG, Zugangsart.OEFFENTLICH.kennung());
    op.addParametersItem(new PathParameter().name("id"));

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec))
        .containsExactly("GET /api/beispiel: keine 4xx-Antwort beschrieben");
  }

  @Test
  void angemeldeterAufrufOhne4xxAntwortWirdAbgewiesen() {
    OpenAPI spec = vollstaendig();
    operation(spec)
        .setResponses(
            new ApiResponses().addApiResponse("204", new ApiResponse().description("ok")));

    assertThat(OpenApiPflichtpruefung.pflichtangabenMaengel(spec))
        .containsExactly("GET /api/beispiel: keine 4xx-Antwort beschrieben");
  }

  /** Gegenprobe: Ein Handler ohne Operation wird mit seinem Namen gemeldet. */
  @Test
  void fehlenderHandlerWirdBeimNamenGenannt() {
    assertThat(
            OpenApiPflichtpruefung.fehlendeHandler(
                Map.of("GET /api/beispiel", "AController#a", "POST /api/b", "BController#b"),
                vollstaendig()))
        .containsExactly("POST /api/b (BController#b)");
  }
}
