package org.mwolff.manban.fremdboard.infrastructure;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.mwolff.manban.fremdboard.application.FremdboardProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Schutz gegen Abrufe an beliebige Adressen (SSRF) beim fremden Board (Issue #2157, Plan #2152
 * A14): Abgerufen werden nur Hosts aus {@code MANBAN_FREMDBOARD_HOSTS} (Vorgabe {@code
 * gitlab.com}), nur über {@code https}, ohne Weiterleitungen und mit einem Zeitlimit von 10
 * Sekunden wie beim Web-Push-Versand.
 *
 * <p>Private Adressen sind nicht pauschal verboten: Ein selbst betriebenes GitLab liegt oft im
 * eigenen Netz, der Betreiber trägt es in die Liste ein. Verglichen wird der Hostname ohne
 * Rücksicht auf Groß- und Kleinschreibung und einen Schlusspunkt; der Port ist frei.
 */
@Component
public final class HostErlaubnis {

  /** Höchste Wartezeit auf Verbindung und Antwort des fremden Systems. */
  public static final Duration ZEITGRENZE = Duration.ofSeconds(10);

  private final List<String> erlaubteHosts;
  private final HttpClient client;
  private final Duration zeitgrenze;

  @Autowired
  HostErlaubnis(FremdboardProperties properties) {
    this(properties.hosts());
  }

  HostErlaubnis(List<String> hosts) {
    this(
        hosts,
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(ZEITGRENZE)
            .build(),
        ZEITGRENZE);
  }

  /**
   * Für Tests gegen einen lokalen {@code https}-Server: dessen Zertifikat kennt nur ein eigener
   * Client, und eine Zeitüberschreitung soll nicht zehn Sekunden dauern.
   */
  HostErlaubnis(List<String> hosts, HttpClient client, Duration zeitgrenze) {
    this.erlaubteHosts = FremdboardProperties.normalisierteHosts(hosts);
    this.client = client;
    this.zeitgrenze = zeitgrenze;
  }

  /** Die erlaubten Hosts, klein geschrieben. */
  public List<String> hosts() {
    return erlaubteHosts;
  }

  /**
   * Prüft eine als Text gegebene Adresse, etwa die Basisadresse aus den Projekteinstellungen.
   *
   * @throws HostNichtErlaubtException wenn sie keine gültige Adresse ist oder {@link #pruefe(URI)}
   *     sie ablehnt
   */
  public URI pruefe(String adresse) {
    try {
      return pruefe(new URI(adresse));
    } catch (URISyntaxException e) {
      throw new HostNichtErlaubtException("Keine gültige Adresse", e);
    }
  }

  /**
   * Lässt {@code ziel} durch, wenn es {@code https} spricht, keine Zugangsdaten im Hostteil trägt
   * und sein Host in der Erlaubnisliste steht.
   *
   * @throws HostNichtErlaubtException sonst
   */
  public URI pruefe(URI ziel) {
    if (!"https".equalsIgnoreCase(ziel.getScheme())) {
      throw new HostNichtErlaubtException("Nur https ist für ein fremdes Board erlaubt");
    }
    if (ziel.getRawUserInfo() != null) {
      throw new HostNichtErlaubtException("Zugangsdaten in der Adresse sind nicht erlaubt");
    }
    String host = ziel.getHost();
    if (host == null) {
      throw new HostNichtErlaubtException("Adresse ohne Host");
    }
    String vergleich = host.toLowerCase(Locale.ROOT);
    if (vergleich.endsWith(".")) {
      vergleich = vergleich.substring(0, vergleich.length() - 1);
    }
    if (!erlaubteHosts.contains(vergleich)) {
      throw new HostNichtErlaubtException(
          "Host " + vergleich + " steht nicht in der Erlaubnisliste MANBAN_FREMDBOARD_HOSTS");
    }
    return ziel;
  }

  /**
   * Eine Anfrage an {@code ziel} mit dem Zeitlimit, nach {@link #pruefe(URI)}.
   *
   * @throws HostNichtErlaubtException wenn {@link #pruefe(URI)} das Ziel ablehnt
   */
  public HttpRequest.Builder anfrage(URI ziel) {
    return HttpRequest.newBuilder(pruefe(ziel)).timeout(zeitgrenze);
  }

  /** Der HTTP-Client für das fremde Board: folgt keiner Weiterleitung, Verbindungslimit 10 s. */
  public HttpClient httpClient() {
    return client;
  }
}
