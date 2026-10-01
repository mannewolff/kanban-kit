package org.mwolff.manban.config;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verweigert den Start, wenn die Datenbank dieser Instanz unter einem öffentlich bekannten
 * Vorgabekennwort läuft (Issue #1266, Plan #1262, fachlich #675).
 *
 * <p><strong>Warum eine Prüfung auf „fehlt“ allein ins Leere liefe.</strong> Das Betriebs-Overlay
 * {@code docker-compose.betrieb.yml} erzwingt {@code POSTGRES_PASSWORD} mit {@code :?}, und das ist
 * die früheste Stelle, an der ein fehlender Wert auffallen kann — aber {@code :?} fängt nur
 * <em>ungesetzt</em> und <em>leer</em>. Der Basis-Stack {@code docker-compose.yml} reicht dasselbe
 * {@code POSTGRES_PASSWORD} mit {@code :-manban} durch, und {@code application.yml} setzt für
 * {@code MANBAN_DB_PASSWORD} denselben Rückfall: Im Container ist die Property also nie leer. Wer
 * die {@code .env}-Vorlage kopiert, ohne sie anzufassen, oder wer das Betriebs-Overlay weglässt,
 * bekommt einen laufenden Stack mit einem Kennwort, das im öffentlichen Repository steht — und
 * merkt davon nichts. Genau dieser Fall, ein Platzhalter, der gültig aussieht, hat Issue #1243
 * ausgelöst. Deshalb prüft diese Bean — wie {@code ObjectStorageStartupCheck} für die
 * Speicher-Zugangsdaten und {@code SessionSecretStartupCheck} für den Signaturschlüssel — auch auf
 * <em>Gleichheit mit einem mitgelieferten Vorgabewert</em>.
 *
 * <p>Ort: Die Klasse gehört zu keinem Fachmodul — das Datenbankkennwort ist anwendungsweite
 * Konfiguration, so wie {@code AutomationStartupLogger} nebenan die Automatiken aller Module
 * meldet. Sie fasst dabei weder einen Domänentyp noch ein Repository an; die beiden ArchUnit-Regeln
 * der Composition-Root bleiben also erfüllt.
 *
 * <p>Die Prüfung läuft im Konstruktor und damit in {@code finishBeanFactoryInitialization} — vor
 * der Freigabe des Webservers in {@code finishRefresh}. Es geht also kein Request gegen eine
 * Instanz, deren Daten unter einem bekannten Kennwort liegen.
 *
 * <p><strong>Keine Ausgabe nennt einen Kennwortwert</strong> — auch nicht gekürzt oder als
 * Prüfsumme. Log-Ziele sind selten so geschützt wie die Konfiguration; eine Meldung, die den Wert
 * trägt, verteilte das Geheimnis genau dorthin. Genannt werden nur Umgebungsvariable und
 * Property-Name.
 */
// final: Der Konstruktor wirft — bei einer ableitbaren Klasse wäre das ein Finalizer-Angriffspfad
// (SpotBugs CT_CONSTRUCTOR_THROW), weil ein teilinitialisiertes Objekt zurückbliebe. Abgeleitet
// wird hier ohnehin nichts, und ein Proxy ist nicht nötig (kein @Transactional/@Async).
@Component
final class DatabasePasswordStartupCheck {

  // Die abgelehnten Werte bleiben private: Der Test hält dieselben Werte als eigene Literale und
  // schreibt damit unabhängig fest, was als unsicher gilt — gegen dieselbe Konstante zu prüfen
  // wäre tautologisch.

  /**
   * Rückfall aus {@code application.yml} ({@code ${MANBAN_DB_PASSWORD:manban}}) und {@code
   * :-}-Vorgabe von {@code POSTGRES_PASSWORD} in {@code docker-compose.yml}. Genau dieser Wert
   * erreicht die Anwendung in dem Fall, den diese Bean abfängt.
   */
  private static final String ABGELEHNTE_VORGABE = "manban";

  /**
   * Platzhalter von {@code POSTGRES_PASSWORD} in {@code .env.example} (Issue #1266) — wie {@code
   * change-me-benutzer} und {@code change-me-objektspeicher} für den Objektspeicher seit Issue
   * #1243. Dass er hier steht, hält die Vorlage bewusst nicht lauffähig.
   */
  private static final String ABGELEHNTE_ENV_VORLAGE = "change-me";

  private static final Logger log = LoggerFactory.getLogger(DatabasePasswordStartupCheck.class);

  /** Die Stellen, an denen ein Betreiber das Kennwort setzt — in beiden Meldungen dieselben. */
  private static final String STELLEN =
      "Ein eigenes Kennwort setzen (etwa openssl rand -hex 32) und in der .env unter"
          + " POSTGRES_PASSWORD hinterlegen; die Anwendung liest denselben Wert als"
          + " MANBAN_DB_PASSWORD (Property spring.datasource.password).";

  /**
   * Begleitet den Startabbruch als ERROR-Zeile und trägt zugleich die Ausnahmemeldung: Wer nur die
   * Logs liest, soll dieselbe Aussage vorfinden wie in der Stacktrace-Ursache.
   */
  private static final String UNSICHER_IM_PRODUKTIVBETRIEB =
      "Start abgebrochen: Das Datenbankkennwort ist nicht gesetzt oder trägt einen mitgelieferten"
          + " Vorgabewert aus dem öffentlichen Repository. Wer ihn kennt, kann jeden Datensatz"
          + " dieser Instanz lesen, ändern und löschen. "
          + STELLEN
          + " Für einen bewussten Entwicklungs- oder Testbetrieb: manban.dev-mode=true.";

  /**
   * Der Entwicklungs-/Testbetrieb darf mit dem Vorgabekennwort laufen — aber nicht schweigend:
   * Diese Zeile steht bei jedem Start im Log, damit niemand eine solche Instanz für produktionsreif
   * hält.
   */
  private static final String UNSICHER_IM_ENTWICKLUNGSBETRIEB =
      "Entwicklungs-/Testbetrieb (manban.dev-mode=true) mit unsicherem Datenbankkennwort: Das"
          + " Kennwort ist nicht gesetzt oder trägt einen mitgelieferten Vorgabewert aus dem"
          + " öffentlichen Repository — die Daten dieser Instanz liegen offen. Für den"
          + " Produktivbetrieb: "
          + STELLEN;

  /**
   * Leere Vorgabe im Platzhalter, obwohl {@code application.yml} die Property immer setzt: Fehlte
   * sie einmal, bräche der Kontext an einem unauflösbaren Platzhalter ab — die Meldung nennte dann
   * gerade nicht den fehlenden Wert, was diese Prüfung zusagt. So wird „fehlt“ zu „leer“ und läuft
   * in dieselbe, sprechende Ablehnung.
   */
  DatabasePasswordStartupCheck(
      @Value("${spring.datasource.password:}") @Nullable String kennwort,
      @Value("${manban.dev-mode:false}") boolean devMode) {
    if (!istAbgelehnt(kennwort)) {
      return;
    }
    if (devMode) {
      log.warn(UNSICHER_IM_ENTWICKLUNGSBETRIEB);
      return;
    }
    log.error(UNSICHER_IM_PRODUKTIVBETRIEB);
    throw new IllegalStateException(UNSICHER_IM_PRODUKTIVBETRIEB);
  }

  /**
   * {@code @Nullable} obwohl der Platzhalter oben eine leere Vorgabe trägt und {@code null} damit
   * nicht ankommen kann: Die Prüfung soll auch dann noch tragen, wenn die Vorgabe einmal entfiele.
   */
  private static boolean istAbgelehnt(@Nullable String kennwort) {
    return kennwort == null
        || kennwort.isBlank()
        || ABGELEHNTE_VORGABE.equals(kennwort)
        || ABGELEHNTE_ENV_VORLAGE.equals(kennwort);
  }
}
