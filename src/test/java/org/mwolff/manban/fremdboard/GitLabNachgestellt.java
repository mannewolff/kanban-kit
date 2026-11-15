package org.mwolff.manban.fremdboard;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.domain.Verbindung;
import org.mwolff.manban.fremdboard.infrastructure.GitLabTestServer;
import org.mwolff.manban.fremdboard.infrastructure.HostErlaubnis;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * GitLab nachgestellt für Integrationstests des Abgleichs (Issue #2167): ein {@link
 * GitLabTestServer} für die ganze Testsuite, dessen Erlaubnis die {@code GitLabQuelle} des
 * Anwendungskontexts übernimmt. Dasselbe Mittel wie in {@code WebPushSenderTest}, ohne neue
 * Testabhängigkeit.
 *
 * <p>Wer das hier importiert, setzt auch {@code manban.fremdboard.secret-key} auf {@link
 * GitLabTestServer#SCHLUESSEL} — nur dann entschlüsselt der Tresor den Zugang. Beide Testklassen
 * tragen dieselbe Konfiguration und teilen sich so einen Kontext.
 */
@TestConfiguration
public class GitLabNachgestellt {

  /** Der Server; vor jedem Test mit {@link GitLabTestServer#zuruecksetzen()} leeren. */
  public static final GitLabTestServer GITLAB = starte();

  /** Pfad einer Karte samt Projekt, wie die Quelle ihn abruft. */
  public static String karte(int nummer) {
    return GitLabTestServer.PROJEKT + "/issues/" + nummer;
  }

  /** Erste und einzige Seite der Label-Ereignisse einer Karte. */
  public static String labelEreignisse(int nummer) {
    return karte(nummer) + "/resource_label_events?per_page=100&page=1";
  }

  /** Erste und einzige Seite der Statusereignisse einer Karte. */
  public static String statusEreignisse(int nummer) {
    return karte(nummer) + "/resource_state_events?per_page=100&page=1";
  }

  /** Erste und einzige Seite der Notizen einer Karte. */
  public static String notizen(int nummer) {
    return karte(nummer) + "/notes?sort=asc&order_by=created_at&per_page=100&page=1";
  }

  /** Antwortet auf eine Liste ohne Folgeseite. */
  public static void liste(String pfad, String json) {
    GITLAB.antworte(pfad, 200, json, "");
  }

  /** Eine Karte, wie {@code GET /issues/:iid} sie liefert. */
  public static String issue(
      int nummer,
      String titel,
      String beschreibung,
      List<String> labels,
      boolean geschlossen,
      Instant angelegt,
      Instant geaendert) {
    return """
        {"id":%d,"iid":%d,"title":"%s","description":"%s","state":"%s","created_at":"%s",
         "updated_at":"%s","labels":[%s],"web_url":"%s/gruppe/projekt/-/issues/%d"}"""
        .formatted(
            1000 + nummer,
            nummer,
            titel,
            beschreibung.replace("\n", "\\n"),
            geschlossen ? "closed" : "opened",
            angelegt,
            geaendert,
            labels.stream().map(l -> "\"" + l + "\"").collect(Collectors.joining(",")),
            GITLAB.basis(),
            nummer);
  }

  /** Ein Label-Ereignis; {@code aktion} ist {@code add} oder {@code remove}. */
  public static String labelEreignis(
      int id, String label, String aktion, Instant zeit, String benutzer) {
    return """
        {"id":%d,"user":{"username":"%s"},"created_at":"%s","resource_type":"Issue",
         "label":{"name":"%s"},"action":"%s"}"""
        .formatted(id, benutzer, zeit, label, aktion);
  }

  /** Ein Statusereignis; {@code zustand} ist {@code closed} oder {@code reopened}. */
  public static String statusEreignis(int id, String zustand, Instant zeit, String benutzer) {
    return """
        {"id":%d,"user":{"username":"%s"},"created_at":"%s","resource_type":"Issue",
         "state":"%s"}"""
        .formatted(id, benutzer, zeit, zustand);
  }

  /** Eine Notiz, die kein Systemeintrag ist. */
  public static String notiz(
      int id, String text, String autor, Instant angelegt, Instant geaendert) {
    return """
        {"id":%d,"body":"%s","author":{"username":"%s"},"created_at":"%s","updated_at":"%s",
         "system":false}"""
        .formatted(id, text, autor, angelegt, geaendert);
  }

  /** Stellt eine Karte samt Wechseln und Notizen nach. */
  public static void stelleNach(
      int nummer, String karte, List<String> label, List<String> status, List<String> notizen) {
    GITLAB.antworte(karte(nummer), 200, karte, null);
    liste(labelEreignisse(nummer), "[" + String.join(",", label) + "]");
    liste(statusEreignisse(nummer), "[" + String.join(",", status) + "]");
    liste(notizen(nummer), "[" + String.join(",", notizen) + "]");
  }

  /** Die Verbindung eines Projekts zum nachgestellten GitLab mit den Standard-Statuslabels. */
  public static Verbindung verbindung(long projectId, long boardId, Instant angebundenAm) {
    return new Verbindung(
        projectId,
        FremdBoardArt.GITLAB,
        GITLAB.basis(),
        "gruppe/projekt",
        GITLAB.zugangVerschluesselt(),
        "kit-bot",
        StatusLabels.STANDARD,
        boardId,
        angebundenAm,
        null,
        null,
        null);
  }

  @Bean
  @Primary
  HostErlaubnis nachgestellteErlaubnis() {
    return GITLAB.erlaubnis();
  }

  private static GitLabTestServer starte() {
    try {
      return new GitLabTestServer();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
