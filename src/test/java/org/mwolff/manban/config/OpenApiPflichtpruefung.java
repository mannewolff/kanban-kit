package org.mwolff.manban.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.config.ApiZugang.Zugangsart;
import org.mwolff.manban.project.domain.Permission;
import org.springframework.context.ApplicationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Prüfroutinen der API-Beschreibung für {@link OpenApiIT}, Teil (b) und (c) (Issue #1409): Jeder
 * Handler unter {@code /api/**} erscheint als Operation, und jede Operation trägt die
 * Pflichtangaben. Sie arbeiten auf einem {@link OpenAPI}-Objekt, damit die Gegenproben in {@link
 * OpenApiPflichtpruefungTest} ohne laufende Anwendung auskommen.
 */
final class OpenApiPflichtpruefung {

  /** Die Spezifikation selbst ist kein Aufruf des Leitstands; springdoc liefert sie aus. */
  private static final Set<String> SPEZIFIKATION = Set.of("/api/openapi", "/api/openapi.yaml");

  /** Recht, das kein Projekt-Recht ist, sondern die Plattform-Rolle verlangt. */
  static final String PLATTFORM_ADMIN = "PLATTFORM_ADMIN";

  /** Endung des Tags, den springdoc ohne {@code @Tag} aus dem Klassennamen bildet. */
  private static final String SPRINGDOC_TAG_ENDUNG = "-controller";

  /** {@code {name:regex}} in einem Mapping-Muster; die Beschreibung nennt nur {@code {name}}. */
  private static final Pattern PFADVARIABLE_MIT_MUSTER = Pattern.compile("\\{([^}:]+):[^}]*}");

  private static final Set<String> RECHTE =
      Stream.concat(Stream.of(Permission.values()).map(Enum::name), Stream.of(PLATTFORM_ADMIN))
          .collect(Collectors.toUnmodifiableSet());

  private OpenApiPflichtpruefung() {}

  /** Die Spezifikation als Modell; OpenAPI 3.1, wie springdoc sie schreibt. */
  static OpenAPI modell(JsonNode spezifikation) throws JsonProcessingException {
    return Json31.mapper().treeToValue(spezifikation, OpenAPI.class);
  }

  /** Die Handler der laufenden Anwendung, wie sie Spring MVC für Controller-Methoden führt. */
  static Map<String, String> handler(@Nullable ApplicationContext kontext) {
    if (kontext == null) {
      throw new IllegalStateException("Kein Web-Anwendungskontext");
    }
    return handler(
        kontext
            .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class)
            .getHandlerMethods());
  }

  /**
   * Alle Handler unter {@code /api/**} außer der Spezifikation, als {@code METHODE pfad} → {@code
   * Klasse#methode}. Ein Handler ohne HTTP-Methode erscheint mit der Methode {@code ALLE}, die
   * keine Operation trägt — die Prüfung meldet ihn dann als fehlend.
   */
  static Map<String, String> handler(Map<RequestMappingInfo, HandlerMethod> handlerMethoden) {
    Map<String, String> handler = new TreeMap<>();
    handlerMethoden.forEach(
        (info, methode) -> {
          String name = methode.getBeanType().getSimpleName() + "#" + methode.getMethod().getName();
          Set<String> methoden =
              info.getMethodsCondition().getMethods().stream()
                  .map(Enum::name)
                  .collect(Collectors.toCollection(TreeSet::new));
          if (methoden.isEmpty()) {
            methoden.add("ALLE");
          }
          for (String muster : info.getPatternValues()) {
            String pfad = PFADVARIABLE_MIT_MUSTER.matcher(muster).replaceAll("{$1}");
            if (pfad.startsWith("/api/") && !SPEZIFIKATION.contains(pfad)) {
              methoden.forEach(m -> handler.put(m + " " + pfad, name));
            }
          }
        });
    return handler;
  }

  /** Die Handler ohne Operation in der Spezifikation, jeweils mit ihrem Namen. */
  static List<String> fehlendeHandler(Map<String, String> handler, OpenAPI spec) {
    Set<String> operationen = operationen(spec).keySet();
    List<String> fehlend = new ArrayList<>();
    handler.forEach(
        (schluessel, name) -> {
          if (!operationen.contains(schluessel)) {
            fehlend.add(schluessel + " (" + name + ")");
          }
        });
    return fehlend;
  }

  /**
   * Die Mängel an den Pflichtangaben je Operation: Tag, Summary, Beschreibung, {@code
   * x-stabilitaet}, mindestens eine 4xx-Antwort (außer bei öffentlichen Aufrufen ohne Eingabe) und
   * ein bekanntes {@code x-erforderliches-recht}.
   */
  static List<String> pflichtangabenMaengel(OpenAPI spec) {
    List<String> maengel = new ArrayList<>();
    operationen(spec)
        .forEach(
            (schluessel, op) ->
                maengel(op).forEach(mangel -> maengel.add(schluessel + ": " + mangel)));
    return maengel;
  }

  private static Map<String, Operation> operationen(OpenAPI spec) {
    Map<String, Operation> operationen = new TreeMap<>();
    if (spec.getPaths() != null) {
      spec.getPaths()
          .forEach(
              (pfad, item) ->
                  item.readOperationsMap()
                      .forEach((methode, op) -> operationen.put(methode.name() + " " + pfad, op)));
    }
    return operationen;
  }

  private static List<String> maengel(Operation op) {
    List<String> maengel = new ArrayList<>();
    if (!mitTag(op)) {
      maengel.add("Tag fehlt");
    }
    if (leer(op.getSummary())) {
      maengel.add("Summary fehlt");
    }
    if (leer(op.getDescription())) {
      maengel.add("Beschreibung fehlt");
    }
    if (leer(erweiterung(op, ApiBeschreibungsAnreicherung.X_STABILITAET))) {
      maengel.add("x-stabilitaet fehlt");
    }
    if (!mit4xx(op) && !oeffentlichOhneEingabe(op)) {
      maengel.add("keine 4xx-Antwort beschrieben");
    }
    @Nullable String recht = erweiterung(op, ApiBeschreibungsAnreicherung.X_ERFORDERLICHES_RECHT);
    if (recht != null && !RECHTE.contains(recht)) {
      maengel.add("unbekanntes Recht " + recht);
    }
    return maengel;
  }

  /** Ohne {@code @Tag} vergibt springdoc den Klassennamen (etwa card-archive-controller). */
  private static boolean mitTag(Operation op) {
    return op.getTags() != null
        && op.getTags().stream().anyMatch(tag -> !tag.endsWith(SPRINGDOC_TAG_ENDUNG));
  }

  private static boolean mit4xx(Operation op) {
    return op.getResponses() != null
        && op.getResponses().keySet().stream().anyMatch(code -> code.startsWith("4"));
  }

  private static boolean oeffentlichOhneEingabe(Operation op) {
    boolean ohneParameter = op.getParameters() == null || op.getParameters().isEmpty();
    return Zugangsart.OEFFENTLICH
            .kennung()
            .equals(erweiterung(op, ApiBeschreibungsAnreicherung.X_ZUGANG))
        && ohneParameter
        && op.getRequestBody() == null;
  }

  private static @Nullable String erweiterung(Operation op, String name) {
    @Nullable Object wert = op.getExtensions() == null ? null : op.getExtensions().get(name);
    return wert == null ? null : wert.toString();
  }

  private static boolean leer(@Nullable String text) {
    return text == null || text.isBlank();
  }
}
