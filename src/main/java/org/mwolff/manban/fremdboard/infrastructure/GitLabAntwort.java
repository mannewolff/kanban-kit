package org.mwolff.manban.fremdboard.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.domain.FremdEreignis;
import org.mwolff.manban.fremdboard.domain.FremdKarte;
import org.mwolff.manban.fremdboard.domain.FremdNotiz;

/**
 * Liest die JSON-Einträge der GitLab-API in die Werte des Ports (Issue #2160). Fehlt ein
 * Pflichtfeld oder hat es die falsche Form, wirft jede Methode {@link GitLabAbrufException} mit dem
 * Grund {@link GitLabAbruf#NICHT_LESBAR}.
 */
final class GitLabAntwort {

  private static final String ANGELEGT = "created_at";

  private GitLabAntwort() {}

  /** Eine Karte aus einem Eintrag von {@code issues}; ohne Beschreibung ist der Text leer. */
  static FremdKarte karte(JsonNode eintrag) {
    List<String> labels = new ArrayList<>();
    eintrag.path("labels").forEach(label -> labels.add(label.asText()));
    JsonNode beschreibung = eintrag.path("description");
    return new FremdKarte(
        zahl(eintrag, "iid"),
        text(eintrag, "title"),
        beschreibung.isTextual() ? beschreibung.asText() : "",
        labels,
        "closed".equals(eintrag.path("state").asText()),
        zeit(eintrag, ANGELEGT),
        zeit(eintrag, "updated_at"));
  }

  /** Ein Label-Ereignis; leer, wenn GitLab das Label gelöscht hat oder die Aktion unbekannt ist. */
  static Optional<FremdEreignis> labelEreignis(JsonNode eintrag, long nummer) {
    JsonNode label = eintrag.path("label").path("name");
    if (!label.isTextual()) {
      return Optional.empty();
    }
    return labelArt(eintrag.path("action").asText())
        .map(
            art ->
                new FremdEreignis(
                    "label-" + zahl(eintrag, "id"),
                    nummer,
                    art,
                    label.asText(),
                    zeit(eintrag, ANGELEGT),
                    akteur(eintrag)));
  }

  /** Ein Statuswechsel; leer bei einem Zustand, den Issues nicht kennen (etwa {@code merged}). */
  static Optional<FremdEreignis> statusEreignis(JsonNode eintrag, long nummer) {
    return statusArt(eintrag.path("state").asText())
        .map(
            art ->
                new FremdEreignis(
                    "state-" + zahl(eintrag, "id"),
                    nummer,
                    art,
                    null,
                    zeit(eintrag, ANGELEGT),
                    akteur(eintrag)));
  }

  /** Ein Kommentar; leer bei einer Systemnotiz, die GitLab selbst zu einem Wechsel schreibt. */
  static Optional<FremdNotiz> notiz(JsonNode eintrag, long nummer) {
    if (eintrag.path("system").asBoolean(false)) {
      return Optional.empty();
    }
    return Optional.of(
        new FremdNotiz(
            String.valueOf(zahl(eintrag, "id")),
            nummer,
            text(eintrag.path("author"), "username"),
            text(eintrag, "body"),
            zeit(eintrag, ANGELEGT),
            zeit(eintrag, "updated_at")));
  }

  /** Eine ganze Zahl aus {@code feld}. */
  static long zahl(JsonNode eintrag, String feld) {
    JsonNode wert = eintrag.path(feld);
    if (!wert.isIntegralNumber()) {
      throw GitLabAbruf.fehler(GitLabAbruf.NICHT_LESBAR, null);
    }
    return wert.asLong();
  }

  private static Optional<FremdEreignis.Art> labelArt(String aktion) {
    return switch (aktion) {
      case "add" -> Optional.of(FremdEreignis.Art.LABEL_GESETZT);
      case "remove" -> Optional.of(FremdEreignis.Art.LABEL_ENTFERNT);
      default -> Optional.empty();
    };
  }

  private static Optional<FremdEreignis.Art> statusArt(String zustand) {
    return switch (zustand) {
      case "closed" -> Optional.of(FremdEreignis.Art.GESCHLOSSEN);
      case "reopened" -> Optional.of(FremdEreignis.Art.WIEDER_GEOEFFNET);
      default -> Optional.empty();
    };
  }

  private static @Nullable String akteur(JsonNode eintrag) {
    JsonNode name = eintrag.path("user").path("username");
    return name.isTextual() ? name.asText() : null;
  }

  private static String text(JsonNode eintrag, String feld) {
    JsonNode wert = eintrag.path(feld);
    if (!wert.isTextual()) {
      throw GitLabAbruf.fehler(GitLabAbruf.NICHT_LESBAR, null);
    }
    return wert.asText();
  }

  private static Instant zeit(JsonNode eintrag, String feld) {
    try {
      return OffsetDateTime.parse(text(eintrag, feld)).toInstant();
    } catch (DateTimeParseException e) {
      throw GitLabAbruf.fehler(GitLabAbruf.NICHT_LESBAR, e);
    }
  }
}
