package org.mwolff.manban.push.infrastructure;

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.auth.application.UserLookup;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.mwolff.manban.nightrun.domain.Meldegrund.Zustand;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.mwolff.manban.project.application.PermissionChecker;
import org.mwolff.manban.push.application.LaufNachricht;
import org.mwolff.manban.push.application.PushAboRepository;
import org.mwolff.manban.push.application.PushProperties;
import org.mwolff.manban.push.domain.PushAbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Stellt eine vom {@code LaufMeldeService} geplante Nachricht zu einem Lauf zu (Issue #1998, Plan
 * #1989 E17, E21). Läuft nach dem Commit im Outbox-Worker.
 *
 * <p><b>Prüfung vor dem Senden (E21).</b> Zwischen Planen und Senden kann Zeit vergehen, im
 * Wiederholungsfall Stunden. Darum prüft der Handler noch einmal: Das Abo besteht, gehört noch
 * demselben Konto, begann vor dem Melden und ist nicht abgelaufen; das Konto ist aktiv und darf die
 * Läufe des Projekts sehen. Fehlt etwas davon, wird nichts gesendet, und der Eintrag gilt als
 * erledigt — eine Wiederholung änderte daran nichts.
 *
 * <p><b>Text (E17).</b> Titel „Projekt · Board“, Inhalt Zustand und Grund in der Sprache der
 * Installation, bestimmt beim Senden. Die Nutzdaten tragen die Adresse des Laufs in der mobilen
 * Oberfläche.
 *
 * <p><b>Versandfehler (E21).</b> 404 und 410 heißen: Der Push-Dienst hat das Abo verworfen, es
 * kommt nicht zurück. Der Handler löscht es und meldet Erfolg. Abweichend vom {@link
 * OutboxHandler}-Vertrag schreibt er damit auf die Fachdatenbank — eine einzelne Löschung nach
 * Kennung, die nicht scheitert, wenn die Zeile schon fehlt. Jeder andere Status und jeder
 * Verbindungsfehler wirft, und die Outbox wiederholt mit Abstand; eine Wiederholung erzeugt keine
 * zweite Nachricht, weil der Schlüssel je Lauf und Abo eindeutig ist.
 *
 * <p>Ohne VAPID-Schlüssel ist Push aus (E16): Dann verwirft der Handler die Nachricht mit einer
 * Protokollzeile, statt sie in den Wiederholungszyklus zu schicken.
 */
@Component
class LaufNachrichtHandler implements OutboxHandler {

  /** Wie lange der Push-Dienst eine Nachricht für ein nicht erreichbares Gerät aufhebt. */
  static final Duration AUFBEWAHRUNG = Duration.ofHours(12);

  private static final Logger log = LoggerFactory.getLogger(LaufNachrichtHandler.class);

  private static final int NICHT_GEFUNDEN = 404;
  private static final int VERSCHWUNDEN = 410;
  private static final int ERFOLG_VON = 200;
  private static final int ERFOLG_BIS = 300;

  /** Die Zustellung an einen Push-Dienst; in Produktion {@link WebPushSender#senden}. */
  @FunctionalInterface
  interface Zustellung {
    int senden(URI endpunkt, byte[] p256dh, byte[] auth, byte[] klartext, Duration ttl)
        throws IOException, InterruptedException;
  }

  private final PushAboRepository abos;
  private final UserLookup users;
  private final PermissionChecker permissions;
  private final Texte texte;
  private final @Nullable Zustellung zustellung;
  private final Clock clock;

  @Autowired
  LaufNachrichtHandler(
      PushAboRepository abos,
      UserLookup users,
      PermissionChecker permissions,
      Texte texte,
      PushProperties properties,
      Clock clock) {
    this(abos, users, permissions, texte, webPush(properties, clock), clock);
  }

  LaufNachrichtHandler(
      PushAboRepository abos,
      UserLookup users,
      PermissionChecker permissions,
      Texte texte,
      @Nullable Zustellung zustellung,
      Clock clock) {
    this.abos = abos;
    this.users = users;
    this.permissions = permissions;
    this.texte = texte;
    this.zustellung = zustellung;
    this.clock = clock;
  }

  /**
   * Die Zustellung über Web Push, oder {@code null}, wenn Push aus ist. Die Schlüssel hat {@link
   * PushSchluesselStartupCheck} schon beim Start geprüft.
   */
  static @Nullable Zustellung webPush(PushProperties properties, Clock clock) {
    if (!properties.aktiv()) {
      return null;
    }
    VapidSchluessel schluessel =
        VapidSchluessel.aus(
            Objects.requireNonNull(properties.vapidPublicKey()),
            Objects.requireNonNull(properties.vapidPrivateKey()));
    WebPushSender sender =
        new WebPushSender(
            HttpClient.newBuilder().connectTimeout(WebPushSender.ZEITGRENZE).build(),
            new VapidSignatur(schluessel, Objects.requireNonNull(properties.subject()), clock),
            new SecureRandom());
    return sender::senden;
  }

  @Override
  public String eventType() {
    return LaufNachricht.TYP;
  }

  @Override
  public void handle(String payload) {
    LaufNachricht nachricht = LaufNachricht.aus(payload);
    if (zustellung == null) {
      log.warn("Web Push ist aus: Nachricht zu Lauf {} verworfen", nachricht.laufId());
      return;
    }
    PushAbo abo = abos.findById(nachricht.aboId()).orElse(null);
    if (abo == null || !giltNoch(abo, nachricht)) {
      log.info(
          "Nachricht zu Lauf {} an Abo {} entfällt: Abo, Konto oder Recht bestehen nicht mehr",
          nachricht.laufId(),
          nachricht.aboId());
      return;
    }
    int status = senden(zustellung, abo, klartext(nachricht));
    if (status == NICHT_GEFUNDEN || status == VERSCHWUNDEN) {
      abos.deleteById(abo.requireId());
      log.info("Push-Dienst kennt Abo {} nicht mehr ({}), Abo gelöscht", abo.requireId(), status);
      return;
    }
    if (status < ERFOLG_VON || status >= ERFOLG_BIS) {
      throw new IllegalStateException("Push-Dienst antwortete mit Status " + status);
    }
  }

  private boolean giltNoch(PushAbo abo, LaufNachricht nachricht) {
    return abo.userId() == nachricht.userId()
        && abo.angelegtAt().isBefore(nachricht.gemeldetAt())
        && !abo.istAbgelaufen(clock.instant())
        && users.istAktiv(nachricht.userId())
        && permissions.hatNachtlaufZugriff(nachricht.userId(), nachricht.projectId());
  }

  private static int senden(Zustellung zustellung, PushAbo abo, byte[] klartext) {
    try {
      return zustellung.senden(
          URI.create(abo.endpoint()),
          Base64.getUrlDecoder().decode(abo.p256dh()),
          Base64.getUrlDecoder().decode(abo.auth()),
          klartext,
          AUFBEWAHRUNG);
    } catch (IOException e) {
      throw new UncheckedIOException("Push-Dienst nicht erreichbar", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Versand unterbrochen", e);
    }
  }

  /** Die Nachricht als JSON, wie der Service Worker sie zeigt: Titel, Inhalt, Adresse. */
  private byte[] klartext(LaufNachricht nachricht) {
    return ("{\"title\":\""
            + zeichenkette(nachricht.titel())
            + "\",\"body\":\""
            + zeichenkette(inhalt(nachricht))
            + "\",\"url\":\""
            + zeichenkette(nachricht.adresse())
            + "\"}")
        .getBytes(StandardCharsets.UTF_8);
  }

  /** Zustand und Grund, etwa „Lauf hängt: Paket #1975 wartet auf eine Entscheidung“ (E17). */
  private String inhalt(LaufNachricht nachricht) {
    Sprache sprache = texte.eingestellte();
    String grundSchluessel = "push.lauf.grund." + nachricht.grund().name().toLowerCase(Locale.ROOT);
    Integer karte = nachricht.karte();
    String grund =
        karte == null
            ? texte.text(sprache, grundSchluessel)
            : texte.text(sprache, grundSchluessel, karte.toString());
    String zustand =
        nachricht.grund().zustand() == Zustand.HAENGT
            ? "push.lauf.haengt"
            : "push.lauf.gescheitert";
    return texte.text(sprache, zustand, grund);
  }

  /** Maskiert einen Wert für eine JSON-Zeichenkette, ohne die Anführungszeichen. */
  private static String zeichenkette(String wert) {
    return new String(JsonStringEncoder.getInstance().quoteAsString(wert));
  }
}
