package org.mwolff.manban.auth.infrastructure.security;

import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Verweigert den Start, wenn Session-Cookies mit dem mitgelieferten Standardschlüssel signiert
 * würden (Issue #890, fachlich #839).
 *
 * <p>Der Schlüssel steht im öffentlichen Repository — wer ihn kennt, kann jedes Session-Cookie
 * dieser Instanz fälschen, auch das eines Plattform-Administrators. Bisher fiel die Anwendung
 * darauf still zurück und lief scheinbar normal weiter. Diese Bean macht den Zustand laut: Ohne
 * ausdrücklich eingeschalteten Entwicklungs- oder Testbetrieb ({@code manban.dev-mode=true}) bricht
 * der Start ab.
 *
 * <p>Die Prüfung hängt bewusst <em>nicht</em> am Rückfall in {@link AuthProperties}: Sie hält auch
 * einen leeren oder fehlenden Schlüssel für unsicher, selbst wenn der Kompaktkonstruktor ihn nie so
 * durchreichen würde. Fiele der Rückfall einmal weg, prüfte sie weiter das Richtige.
 *
 * <p>Die Prüfung läuft im Konstruktor und damit in {@code finishBeanFactoryInitialization} — vor
 * der Freigabe des Webservers in {@code finishRefresh}. Es geht also kein Request gegen eine
 * unsicher signierende Instanz.
 *
 * <p><strong>Keine Ausgabe nennt einen Schlüsselwert</strong> — auch nicht gekürzt oder als
 * Prüfsumme. Log-Ziele sind selten so geschützt wie die Konfiguration; eine Meldung, die den Wert
 * trägt, verteilte das Geheimnis genau dorthin.
 */
// final: Der Konstruktor wirft — bei einer ableitbaren Klasse wäre das ein Finalizer-Angriffspfad
// (SpotBugs CT_CONSTRUCTOR_THROW), weil ein teilinitialisiertes Objekt zurückbliebe. Abgeleitet
// wird hier ohnehin nichts, und ein Proxy ist nicht nötig (kein @Transactional/@Async).
@Component
final class SessionSecretStartupCheck {

  private static final Logger log = LoggerFactory.getLogger(SessionSecretStartupCheck.class);

  /**
   * Begleitet den Startabbruch als ERROR-Zeile und trägt zugleich die Ausnahmemeldung: Wer nur die
   * Logs liest, soll dieselbe Aussage vorfinden wie in der Stacktrace-Ursache.
   */
  private static final String UNSICHER_IM_PRODUKTIVBETRIEB =
      "Start abgebrochen: Der Signaturschlüssel für Sitzungs-Cookies ist nicht gesetzt oder trägt"
          + " den mitgelieferten Standardwert aus dem öffentlichen Repository. Damit signierte"
          + " Sitzungs-Cookies kann jeder fälschen, auch die von Plattform-Administratoren."
          + " Einen eigenen Schlüssel erzeugen (openssl rand -hex 32) und über die"
          + " Umgebungsvariable MANBAN_SESSION_SECRET bzw. die Property manban.auth.session-secret"
          + " setzen. Für einen bewussten Entwicklungs- oder Testbetrieb: manban.dev-mode=true.";

  /**
   * Der Entwicklungs-/Testbetrieb darf mit dem Standardschlüssel laufen — aber nicht schweigend:
   * Diese Zeile steht bei jedem Start im Log, damit niemand eine solche Instanz für produktionsreif
   * hält.
   */
  private static final String UNSICHER_IM_ENTWICKLUNGSBETRIEB =
      "Entwicklungs-/Testbetrieb (manban.dev-mode=true) mit unsicherem Signaturschlüssel für"
          + " Sitzungs-Cookies: Der Schlüssel ist nicht gesetzt oder trägt den mitgelieferten"
          + " Standardwert — Sitzungs-Cookies dieser Instanz sind fälschbar. Für den"
          + " Produktivbetrieb einen eigenen Schlüssel erzeugen (openssl rand -hex 32) und über"
          + " MANBAN_SESSION_SECRET bzw. manban.auth.session-secret setzen.";

  SessionSecretStartupCheck(
      AuthProperties authProperties, @Value("${manban.dev-mode:false}") boolean devMode) {
    if (!istUnsicher(authProperties.sessionSecret())) {
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
   * {@code @Nullable} obwohl {@link AuthProperties#sessionSecret()} im {@code @NullMarked}-Paket
   * non-null zusagt: Die Prüfung soll auch dann noch tragen, wenn der Rückfall im
   * Kompaktkonstruktor entfiele.
   */
  private static boolean istUnsicher(@Nullable String sessionSecret) {
    return sessionSecret == null
        || sessionSecret.isBlank()
        || AuthProperties.INSECURE_DEFAULT_SESSION_SECRET.equals(sessionSecret);
  }
}
