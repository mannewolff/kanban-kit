package org.mwolff.manban.push.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.mwolff.manban.push.application.PushProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Prüft die Push-Schlüssel beim Start (Issue #1996, Plan #1989 E16, Muster {@code
 * SessionSecretStartupCheck}).
 *
 * <p>Push ist abschaltbar: Ganz ohne Schlüssel startet die Anwendung, und eine INFO-Zeile sagt,
 * dass Benachrichtigungen aus bleiben. Den Start verweigert die Prüfung nur, wenn die Angaben halb
 * sind — ein Schlüssel ohne den anderen, Schlüssel ohne {@code subject} oder umgekehrt — oder
 * ungültig: nicht lesbar, nicht zusammengehörig, {@code subject} weder {@code mailto:} noch {@code
 * https:}. Sonst liefe eine Installation scheinbar mit Push, und jede Nachricht scheiterte erst
 * beim Push-Dienst.
 *
 * <p>Die Prüfung läuft im Konstruktor, also vor der Freigabe des Webservers.
 *
 * <p><strong>Keine Ausgabe nennt einen Schlüsselwert</strong>, auch nicht gekürzt — die Meldungen
 * nennen nur die Namen der Umgebungsvariablen.
 */
// final: Der Konstruktor wirft — bei einer ableitbaren Klasse wäre das ein Finalizer-Angriffspfad
// (SpotBugs CT_CONSTRUCTOR_THROW).
@Component
final class PushSchluesselStartupCheck {

  private static final Logger log = LoggerFactory.getLogger(PushSchluesselStartupCheck.class);

  private static final String PUBLIC_KEY = "MANBAN_PUSH_VAPID_PUBLIC_KEY";
  private static final String PRIVATE_KEY = "MANBAN_PUSH_VAPID_PRIVATE_KEY";
  private static final String SUBJECT = "MANBAN_PUSH_SUBJECT";

  /** Zahl der Angaben, die zusammen Push einschalten. */
  private static final int ANGABEN = 3;

  private static final String AUS =
      "Web Push ist aus: Ohne "
          + PUBLIC_KEY
          + ", "
          + PRIVATE_KEY
          + " und "
          + SUBJECT
          + " verschickt diese Installation keine Benachrichtigungen (docs/betrieb.md).";

  PushSchluesselStartupCheck(PushProperties properties) {
    List<String> fehlend = fehlend(properties);
    if (fehlend.size() == ANGABEN) {
      log.info(AUS);
      return;
    }
    if (!fehlend.isEmpty()) {
      throw abbrechen(
          "Web Push ist nur halb eingerichtet, es fehlt "
              + String.join(", ", fehlend)
              + ". Entweder alle drei setzen oder keinen (docs/betrieb.md).");
    }
    if (!sindLesbar(properties)) {
      throw abbrechen(
          "Die VAPID-Schlüssel in "
              + PUBLIC_KEY
              + " und "
              + PRIVATE_KEY
              + " sind nicht lesbar oder gehören nicht zusammen. Ein neues Paar erzeugt"
              + " node scripts/vapid-schluessel.mjs (docs/betrieb.md).");
    }
    if (!istKontakt(Objects.requireNonNull(properties.subject()))) {
      throw abbrechen(SUBJECT + " muss mit mailto: oder https:// beginnen (RFC 8292).");
    }
  }

  private static List<String> fehlend(PushProperties properties) {
    List<String> fehlend = new ArrayList<>();
    if (properties.vapidPublicKey() == null) {
      fehlend.add(PUBLIC_KEY);
    }
    if (properties.vapidPrivateKey() == null) {
      fehlend.add(PRIVATE_KEY);
    }
    if (properties.subject() == null) {
      fehlend.add(SUBJECT);
    }
    return fehlend;
  }

  /** Wahr, wenn beide Schlüssel lesbar sind und zusammengehören. */
  private static boolean sindLesbar(PushProperties properties) {
    try {
      VapidSchluessel.aus(
          Objects.requireNonNull(properties.vapidPublicKey()),
          Objects.requireNonNull(properties.vapidPrivateKey()));
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static boolean istKontakt(String subject) {
    String klein = subject.toLowerCase(Locale.ROOT);
    return klein.startsWith("mailto:") || klein.startsWith("https://");
  }

  private static IllegalStateException abbrechen(String grund) {
    String meldung = "Start abgebrochen: " + grund;
    log.error(meldung);
    return new IllegalStateException(meldung);
  }
}
