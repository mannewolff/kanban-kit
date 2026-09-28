package org.mwolff.manban.attachment.infrastructure;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.attachment.application.ObjectStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verweigert den Start, wenn die Anhänge in einem Objektspeicher mit den mitgelieferten
 * Standardzugangsdaten landen würden (Issue #1227, Plan #1222, fachlich #1221).
 *
 * <p>Die Standardzugangsdaten stehen im öffentlichen Repository — wer sie kennt, kann jeden Anhang
 * dieser Instanz lesen, ersetzen und löschen. Eine Prüfung auf „fehlt“ allein liefe dabei ins
 * Leere: {@code docker-compose.yml} reicht die Zugangsdaten mit {@code :-}-Vorgabe durch, im
 * Container ist die Variable also nie leer. Ein Betreiber, der seine {@code .env} nach der
 * Umbenennung aus Issue #1226 nicht anfasst, bekommt einen laufenden Stack mit öffentlich bekannten
 * Zugangsdaten und merkt davon nichts. Deshalb prüft diese Bean — wie {@code
 * SessionSecretStartupCheck} für den Signaturschlüssel — auch auf <em>Gleichheit mit dem
 * mitgelieferten Standardwert</em>.
 *
 * <p>Geprüft werden die drei Werte, die den Zugang ausmachen: Endpunkt, Zugangsschlüssel und
 * Geheimnis. Der Bucketname bleibt außen vor — er ist kein Zugangsmittel. Alle Befunde stehen in
 * einer Meldung, damit ein Betreiber nicht dreimal startet, bis er jede Stelle kennt.
 *
 * <p>Die Prüfung hängt bewusst <em>nicht</em> am Rückfall im Kompaktkonstruktor von {@link
 * ObjectStorageProperties}: Sie hält einen fehlenden oder leeren Wert ebenso für unsicher, selbst
 * wenn der Record ihn nie so durchreichen würde. Fiele der Rückfall einmal weg, prüfte sie weiter
 * das Richtige.
 *
 * <p>Die Prüfung läuft im Konstruktor und damit in {@code finishBeanFactoryInitialization} — vor
 * der Freigabe des Webservers in {@code finishRefresh}. Es geht also kein Request gegen eine
 * Instanz, die Anhänge in einen offenen Speicher schreibt.
 *
 * <p><strong>Keine Ausgabe nennt einen Schlüsselwert</strong> — auch nicht gekürzt oder als
 * Prüfsumme. Log-Ziele sind selten so geschützt wie die Konfiguration; eine Meldung, die den Wert
 * trägt, verteilte das Geheimnis genau dorthin. Genannt werden nur Umgebungsvariable und
 * Property-Name.
 */
// final: Der Konstruktor wirft — bei einer ableitbaren Klasse wäre das ein Finalizer-Angriffspfad
// (SpotBugs CT_CONSTRUCTOR_THROW), weil ein teilinitialisiertes Objekt zurückbliebe. Abgeleitet
// wird hier ohnehin nichts, und ein Proxy ist nicht nötig (kein @Transactional/@Async).
@Component
final class ObjectStorageStartupCheck {

  // Die Standardwerte bleiben private: Der Test hält dieselben Werte als eigene Literale und
  // schreibt damit unabhängig fest, was als unsicher gilt — gegen dieselbe Konstante zu prüfen
  // wäre tautologisch. (Nebenbei meldet PMD UseUtilityClass eine Klasse ohne Instanzmember erst,
  // wenn sie nicht-private statische Member trägt.)

  /** Standardwert aus {@code application.yml} und dem Kompaktkonstruktor der Properties. */
  private static final String INSECURE_DEFAULT_ENDPOINT = "http://localhost:9000";

  /** Standardwert aus {@code application.yml}, dem Record und der {@code :-}-Vorgabe in Compose. */
  private static final String INSECURE_DEFAULT_ACCESS_KEY = "manban";

  /**
   * Platzhalter von {@code OBJEKTSPEICHER_ROOT_USER} in {@code .env.example} (Issue #1243) — wie
   * {@link #INSECURE_DEFAULT_SECRET_KEY_ENV_VORLAGE} für das Geheimnis. Die Vorlage trägt seit dem
   * Ausfall von v2.12.0 keinen gültig aussehenden Benutzernamen mehr, sondern genau diesen
   * Platzhalter; dass er hier steht, hält die Vorlage bewusst nicht lauffähig.
   */
  private static final String INSECURE_DEFAULT_ACCESS_KEY_ENV_VORLAGE = "change-me-benutzer";

  /** Standardwert aus {@code application.yml} und dem Kompaktkonstruktor der Properties. */
  private static final String INSECURE_DEFAULT_SECRET_KEY = "manban-minio";

  /**
   * Standardwert der {@code :-}-Vorgabe von {@code OBJEKTSPEICHER_ROOT_PASSWORD} in {@code
   * docker-compose.yml}. Genau dieser Wert erreicht den Container im Fall, den diese Bean abfängt —
   * eine Prüfung nur gegen {@link #INSECURE_DEFAULT_SECRET_KEY} ginge daran vorbei.
   */
  private static final String INSECURE_DEFAULT_SECRET_KEY_COMPOSE = "manban-objektspeicher";

  /** Standardwert aus {@code .env.example} — ebenfalls öffentlich bekannt. */
  private static final String INSECURE_DEFAULT_SECRET_KEY_ENV_VORLAGE = "change-me-objektspeicher";

  private static final Logger log = LoggerFactory.getLogger(ObjectStorageStartupCheck.class);

  private static final String BEFUND_ENDPUNKT =
      "Endpunkt (Umgebungsvariable MANBAN_STORAGE_ENDPOINT, Property manban.storage.endpoint)";

  private static final String BEFUND_ZUGANGSSCHLUESSEL =
      "Zugangsschlüssel (Umgebungsvariable MANBAN_STORAGE_ACCESS_KEY, Property"
          + " manban.storage.access-key)";

  private static final String BEFUND_GEHEIMNIS =
      "Geheimnis (Umgebungsvariable MANBAN_STORAGE_SECRET_KEY, Property"
          + " manban.storage.secret-key)";

  /**
   * Begleitet den Startabbruch als ERROR-Zeile und trägt zugleich die Ausnahmemeldung: Wer nur die
   * Logs liest, soll dieselbe Aussage vorfinden wie in der Stacktrace-Ursache.
   */
  private static final String ABBRUCH_KOPF =
      "Start abgebrochen: Der Zugang zum Objektspeicher ist nicht eingerichtet. Folgende Werte"
          + " fehlen oder tragen den mitgelieferten Standardwert aus dem öffentlichen Repository: ";

  private static final String ABBRUCH_RAT =
      ". Anhänge lägen damit in einem Speicher mit öffentlich bekannten Zugangsdaten — jeder"
          + " könnte sie lesen, ersetzen und löschen. Eigene Zugangsdaten setzen (Geheimnis etwa"
          + " mit openssl rand -hex 32) und in der .env unter OBJEKTSPEICHER_ROOT_USER und"
          + " OBJEKTSPEICHER_ROOT_PASSWORD hinterlegen. Für einen bewussten Entwicklungs- oder"
          + " Testbetrieb: manban.dev-mode=true.";

  /**
   * Der Entwicklungs-/Testbetrieb darf mit den Standardzugangsdaten laufen — aber nicht schweigend:
   * Diese Zeile steht bei jedem Start im Log, damit niemand eine solche Instanz für produktionsreif
   * hält.
   */
  private static final String ENTWICKLUNGSBETRIEB_KOPF =
      "Entwicklungs-/Testbetrieb (manban.dev-mode=true) mit unsicherem Zugang zum Objektspeicher."
          + " Folgende Werte fehlen oder tragen den mitgelieferten Standardwert: ";

  private static final String ENTWICKLUNGSBETRIEB_RAT =
      ". Die Anhänge dieser Instanz liegen in einem Speicher mit öffentlich bekannten"
          + " Zugangsdaten. Für den Produktivbetrieb eigene Zugangsdaten setzen (Geheimnis etwa mit"
          + " openssl rand -hex 32) und in der .env unter OBJEKTSPEICHER_ROOT_USER und"
          + " OBJEKTSPEICHER_ROOT_PASSWORD hinterlegen.";

  ObjectStorageStartupCheck(
      ObjectStorageProperties properties, @Value("${manban.dev-mode:false}") boolean devMode) {
    List<String> befunde = new ArrayList<>();
    if (istUnsicher(properties.endpoint(), INSECURE_DEFAULT_ENDPOINT)) {
      befunde.add(BEFUND_ENDPUNKT);
    }
    if (istUnsicher(
        properties.accessKey(),
        INSECURE_DEFAULT_ACCESS_KEY,
        INSECURE_DEFAULT_ACCESS_KEY_ENV_VORLAGE)) {
      befunde.add(BEFUND_ZUGANGSSCHLUESSEL);
    }
    if (istUnsicher(
        properties.secretKey(),
        INSECURE_DEFAULT_SECRET_KEY,
        INSECURE_DEFAULT_SECRET_KEY_COMPOSE,
        INSECURE_DEFAULT_SECRET_KEY_ENV_VORLAGE)) {
      befunde.add(BEFUND_GEHEIMNIS);
    }
    if (befunde.isEmpty()) {
      return;
    }
    String befundText = String.join("; ", befunde);
    if (devMode) {
      log.warn("{}{}{}", ENTWICKLUNGSBETRIEB_KOPF, befundText, ENTWICKLUNGSBETRIEB_RAT);
      return;
    }
    String meldung = ABBRUCH_KOPF + befundText + ABBRUCH_RAT;
    log.error(meldung);
    throw new IllegalStateException(meldung);
  }

  /**
   * {@code @Nullable} obwohl die Zusicherungen von {@link ObjectStorageProperties} im
   * {@code @NullMarked}-Paket non-null lauten: Die Prüfung soll auch dann noch tragen, wenn der
   * Rückfall im Kompaktkonstruktor entfiele.
   */
  private static boolean istUnsicher(@Nullable String wert, String... standardwerte) {
    return wert == null || wert.isBlank() || List.of(standardwerte).contains(wert);
  }
}
