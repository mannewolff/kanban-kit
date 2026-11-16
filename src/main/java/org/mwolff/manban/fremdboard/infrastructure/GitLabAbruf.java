package org.mwolff.manban.fremdboard.infrastructure;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_MULT_CHOICE;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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
 *
 * <p>Die Gegenstelle bestimmt nicht, wie viel Speicher ein Abruf belegt (Issue #2196): Jede Antwort
 * wird schon beim Empfang nach {@link Grenzen#antwortBytes} Bytes abgebrochen, eine Liste über alle
 * Seiten nach {@link Grenzen#listeBytes} Bytes oder {@link Grenzen#eintraege} Einträgen — auch wenn
 * der Server {@code per_page} missachtet. Den Rumpf einer Fehlerantwort liest der Abruf gar nicht.
 */
final class GitLabAbruf {

  /** Höchstzahl der Seiten je Liste; schützt vor einer Gegenstelle, die nie zu Ende kommt. */
  static final int HOECHSTE_SEITEN = 1000;

  /** Grund, wenn eine Antwort nicht die erwartete Form hat. */
  static final String NICHT_LESBAR = "Antwort von GitLab ist nicht lesbar";

  private static final String UNGUELTIGE_SEITE = "Ungültige Seitenangabe von GitLab";
  private static final Logger log = LoggerFactory.getLogger(GitLabAbruf.class);
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String ABGEBROCHEN = " — Abruf abgebrochen";

  /**
   * Mengengrenzen eines Abrufs.
   *
   * @param antwortBytes höchste Bytes einer einzelnen Antwort
   * @param listeBytes höchste Bytes einer Liste über alle Seiten
   * @param eintraege höchste Zahl der Einträge einer Liste über alle Seiten
   */
  record Grenzen(int antwortBytes, int listeBytes, int eintraege) {

    private static final int MIB = 1024 * 1024;

    /**
     * 8 MiB je Antwort, 64 MiB und {@value #HOECHSTE_SEITEN} volle Seiten zu je 100 Einträgen je
     * Liste: weit über einer gewöhnlichen Seite der API, weit unter dem Heap der Anwendung.
     */
    static final Grenzen VORGABE = new Grenzen(8 * MIB, 64 * MIB, HOECHSTE_SEITEN * 100);
  }

  /** Eine erfolgreiche Antwort: der gelesene Inhalt, seine Größe und die Angabe der Folgeseite. */
  private record Antwort(JsonNode inhalt, int bytes, Optional<String> naechsteSeite) {}

  private final HostErlaubnis erlaubnis;
  private final ZugangsTresor tresor;
  private final Grenzen grenzen;

  GitLabAbruf(HostErlaubnis erlaubnis, ZugangsTresor tresor) {
    this(erlaubnis, tresor, Grenzen.VORGABE);
  }

  GitLabAbruf(HostErlaubnis erlaubnis, ZugangsTresor tresor, Grenzen grenzen) {
    this.erlaubnis = erlaubnis;
    this.tresor = tresor;
    this.grenzen = grenzen;
  }

  /** Ein einzelner Eintrag; leer, wenn GitLab ihn nicht kennt (404). */
  Optional<JsonNode> eintrag(Verbindung verbindung, String pfad) {
    return abrufen(verbindung, pfad, grenzen.antwortBytes(), antwortZuGross()).map(Antwort::inhalt);
  }

  /**
   * Alle Einträge einer Liste über alle Seiten; {@code pfadUndAnfrage} trägt schon eine Anfrage.
   */
  List<JsonNode> seiten(Verbindung verbindung, String pfadUndAnfrage) {
    List<JsonNode> eintraege = new ArrayList<>();
    int restBytes = grenzen.listeBytes();
    int seite = 1;
    boolean weiter = true;
    while (weiter) {
      boolean listeBegrenzt = restBytes < grenzen.antwortBytes();
      Antwort antwort =
          abrufen(
                  verbindung,
                  pfadUndAnfrage + "&page=" + seite,
                  listeBegrenzt ? restBytes : grenzen.antwortBytes(),
                  listeBegrenzt
                      ? "GitLab liefert für eine Liste mehr als "
                          + grenzen.listeBytes()
                          + " Bytes"
                          + ABGEBROCHEN
                      : antwortZuGross())
              .orElseThrow(() -> fehler(grundFuer(HTTP_NOT_FOUND), null));
      restBytes -= antwort.bytes();
      JsonNode liste = antwort.inhalt();
      if (!liste.isArray()) {
        throw fehler(NICHT_LESBAR, null);
      }
      if (liste.size() > grenzen.eintraege() - eintraege.size()) {
        throw fehler(
            "GitLab liefert mehr als " + grenzen.eintraege() + " Einträge" + ABGEBROCHEN, null);
      }
      liste.forEach(eintraege::add);
      Optional<String> naechste = antwort.naechsteSeite();
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

  /**
   * Ruft ab und liest den Rumpf einer erfolgreichen Antwort, höchstens {@code hoechstens} Bytes;
   * leer bei 404. Eine längere Antwort bricht mit {@code zuGross} ab, sobald ihre Längenangabe oder
   * das erste Byte über der Grenze ankommt.
   */
  private Optional<Antwort> abrufen(
      Verbindung verbindung, String pfadUndAnfrage, int hoechstens, String zuGross) {
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
    HttpResponse<InputStream> antwort = senden(anfrage);
    try (InputStream ein = antwort.body()) {
      int status = antwort.statusCode();
      if (status == HTTP_NOT_FOUND) {
        return Optional.empty();
      }
      if (status < HTTP_OK || status >= HTTP_MULT_CHOICE) {
        throw fehler(grundFuer(status), null);
      }
      if (antwort.headers().firstValueAsLong("Content-Length").orElse(0) > hoechstens) {
        throw fehler(zuGross, null);
      }
      byte[] rumpf = ein.readNBytes(hoechstens + 1);
      if (rumpf.length > hoechstens) {
        throw fehler(zuGross, null);
      }
      return Optional.of(
          new Antwort(
              lesen(rumpf),
              rumpf.length,
              antwort.headers().firstValue("X-Next-Page").filter(wert -> !wert.isBlank())));
    } catch (IOException e) {
      throw fehler("Antwort von GitLab brach beim Empfang ab", e);
    }
  }

  private HttpResponse<InputStream> senden(HttpRequest anfrage) {
    try {
      return erlaubnis.httpClient().send(anfrage, HttpResponse.BodyHandlers.ofInputStream());
    } catch (HttpTimeoutException e) {
      throw fehler("GitLab hat nicht rechtzeitig geantwortet (Zeitüberschreitung)", e);
    } catch (IOException e) {
      throw fehler("GitLab ist nicht erreichbar", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw fehler("Abruf bei GitLab unterbrochen", e);
    }
  }

  private String antwortZuGross() {
    return "GitLab liefert eine Antwort über " + grenzen.antwortBytes() + " Bytes" + ABGEBROCHEN;
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

  private static JsonNode lesen(byte[] rumpf) {
    try {
      return JSON.readTree(rumpf);
    } catch (IOException e) {
      throw fehler(NICHT_LESBAR, e);
    }
  }
}
