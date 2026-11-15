package org.mwolff.manban.fremdboard.infrastructure;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Der Weg zur REST-API v4 von GitLab für {@link GitLabQuelle} (Issue #2160): Adresse aus Basis und
 * URL-kodiertem Projektpfad, Zugang im Kopf {@code PRIVATE-TOKEN}, Abruf ausschließlich über {@link
 * HostErlaubnis}, Listen seitenweise über {@code X-Next-Page}. Jeder Fehler wird zu {@link
 * GitLabAbrufException} mit lesbarem Grund und als Warnung protokolliert — beides ohne Zugang und
 * ohne Rumpf der Antwort.
 */
final class GitLabAbruf {

  /** Höchstzahl der Seiten je Liste; schützt vor einer Gegenstelle, die nie zu Ende kommt. */
  static final int HOECHSTE_SEITEN = 1000;

  /** Grund, wenn eine Antwort nicht die erwartete Form hat. */
  static final String NICHT_LESBAR = "Antwort von GitLab ist nicht lesbar";

  private static final String UNGUELTIGE_SEITE = "Ungültige Seitenangabe von GitLab";
  private static final Logger log = LoggerFactory.getLogger(GitLabAbruf.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private final HostErlaubnis erlaubnis;
  private final ZugangsTresor tresor;

  GitLabAbruf(HostErlaubnis erlaubnis, ZugangsTresor tresor) {
    this.erlaubnis = erlaubnis;
    this.tresor = tresor;
  }

  /** Ein einzelner Eintrag; leer, wenn GitLab ihn nicht kennt (404). */
  Optional<JsonNode> eintrag(Verbindung verbindung, String pfad) {
    HttpResponse<String> antwort = abrufen(verbindung, pfad);
    if (antwort.statusCode() == HTTP_NOT_FOUND) {
      return Optional.empty();
    }
    return Optional.of(lesen(rumpf(antwort)));
  }

  /**
   * Alle Einträge einer Liste über alle Seiten; {@code pfadUndAnfrage} trägt schon eine Anfrage.
   */
  List<JsonNode> seiten(Verbindung verbindung, String pfadUndAnfrage) {
    List<JsonNode> eintraege = new ArrayList<>();
    int seite = 1;
    boolean weiter = true;
    while (weiter) {
      HttpResponse<String> antwort = abrufen(verbindung, pfadUndAnfrage + "&page=" + seite);
      JsonNode liste = lesen(rumpf(antwort));
      if (!liste.isArray()) {
        throw fehler(NICHT_LESBAR, null);
      }
      liste.forEach(eintraege::add);
      Optional<String> naechste =
          antwort.headers().firstValue("X-Next-Page").filter(wert -> !wert.isBlank());
      weiter = naechste.isPresent();
      if (weiter) {
        seite = folgeseite(naechste.orElseThrow(), seite);
      }
    }
    return eintraege;
  }

  /** Die Basisadresse ohne abschließende Schrägstriche. */
  static String ohneSchlussstrich(String adresse) {
    String ergebnis = adresse;
    while (ergebnis.endsWith("/")) {
      ergebnis = ergebnis.substring(0, ergebnis.length() - 1);
    }
    return ergebnis;
  }

  /** Ein Wert für Pfad oder Anfrage, URL-kodiert. */
  static String kodiert(String wert) {
    return URLEncoder.encode(wert, StandardCharsets.UTF_8).replace("+", "%20");
  }

  /** Protokolliert den Grund und baut die Ausnahme dazu. */
  static GitLabAbrufException fehler(String grund, @Nullable Throwable ursache) {
    log.warn("Abruf bei GitLab fehlgeschlagen: {}", grund);
    return ursache == null
        ? new GitLabAbrufException(grund)
        : new GitLabAbrufException(grund, ursache);
  }

  private HttpResponse<String> abrufen(Verbindung verbindung, String pfadUndAnfrage) {
    URI ziel =
        erlaubnis.pruefe(
            ohneSchlussstrich(verbindung.basisUrl())
                + "/api/v4/projects/"
                + kodiert(verbindung.projektPfad())
                + pfadUndAnfrage);
    HttpRequest anfrage =
        erlaubnis
            .anfrage(ziel)
            .header("PRIVATE-TOKEN", tresor.entschluesseln(verbindung.zugangVerschluesselt()))
            .header("Accept", "application/json")
            .GET()
            .build();
    try {
      return erlaubnis.httpClient().send(anfrage, HttpResponse.BodyHandlers.ofString());
    } catch (HttpTimeoutException e) {
      throw fehler("GitLab hat nicht rechtzeitig geantwortet (Zeitüberschreitung)", e);
    } catch (IOException e) {
      throw fehler("GitLab ist nicht erreichbar", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw fehler("Abruf bei GitLab unterbrochen", e);
    }
  }

  private static int folgeseite(String angabe, int seite) {
    int folgende;
    try {
      folgende = Integer.parseInt(angabe.strip());
    } catch (NumberFormatException e) {
      throw fehler(UNGUELTIGE_SEITE, e);
    }
    if (folgende <= seite) {
      throw fehler(UNGUELTIGE_SEITE, null);
    }
    if (folgende > HOECHSTE_SEITEN) {
      throw fehler(
          "GitLab liefert mehr als " + HOECHSTE_SEITEN + " Seiten — Abruf abgebrochen", null);
    }
    return folgende;
  }

  private static String rumpf(HttpResponse<String> antwort) {
    int status = antwort.statusCode();
    if (status >= HTTP_OK && status < HTTP_MULT_CHOICE) {
      return antwort.body();
    }
    throw fehler(grundFuer(status), null);
  }

  private static String grundFuer(int status) {
    if (status == HTTP_UNAUTHORIZED) {
      return "GitLab lehnt den Zugang ab (401) — Zugang prüfen oder neu hinterlegen";
    }
    if (status == HTTP_NOT_FOUND) {
      return "GitLab kennt das Projekt oder die Karte nicht (404) — Adresse und Projektpfad"
          + " prüfen";
    }
    if (status >= HTTP_INTERNAL_ERROR) {
      return "GitLab meldet einen Serverfehler (" + status + ")";
    }
    return "GitLab antwortet unerwartet mit " + status;
  }

  private static JsonNode lesen(String rumpf) {
    try {
      return JSON.readTree(rumpf);
    } catch (JsonProcessingException e) {
      throw fehler(NICHT_LESBAR, e);
    }
  }
}
