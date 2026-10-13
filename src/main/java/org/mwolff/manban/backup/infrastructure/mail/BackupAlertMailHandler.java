package org.mwolff.manban.backup.infrastructure.mail;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.mwolff.manban.backup.domain.BackupVerdict;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.mwolff.manban.outbox.application.OutboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Stellt den vom {@link OutboxBackupAlertMailer} vorgemerkten Sicherungs-Alarm zu (Issue #828).
 * Läuft nach dem Commit im Outbox-Worker; ein Versandfehler wird dort als Fehlversuch verbucht und
 * wiederholt.
 *
 * <p><strong>Ohne eigene {@code JavaMail…}-Zustellklasse daneben</strong>, anders als bei den
 * Mailern des {@code auth}-Moduls: Deren Trennung stammt aus der Zeit vor der Outbox, als die
 * SMTP-Klasse selbst der Port war. Hier gibt es nur diesen einen Aufrufer, und eine zweite Klasse
 * dazwischen wäre eine Naht ohne zweite Seite.
 *
 * <p>Der Versand ist über {@code manban.mail.enabled} schaltbar. Ausgeliefert ist er aus; dann
 * bleibt nur eine Protokollzeile. Das ist <em>kein</em> Fehler — der Handler darf deshalb nicht
 * werfen, sonst liefe der Alarm in den Wiederholungszyklus und träte am Ende als „endgültig
 * gescheitert" auf, obwohl alles wie eingestellt lief.
 *
 * <p>Betreff und Text erscheinen in der eingestellten Sprache, bestimmt beim Versand (Issue #1643,
 * Plan #1638 A6). Den Zeitpunkt des letzten gelungenen Laufs trägt die Payload als {@link Instant};
 * er erscheint in UTC in der Schreibweise der Sprache mit dem Zusatz „UTC" (E5).
 */
@Component
class BackupAlertMailHandler implements OutboxHandler {

  static final String TYPE = "mail.backup-alert";

  /** Wie {@code OutboxBackupAlertMailer} die Marke für „noch nie gelungen" schreibt. */
  private static final String NIE = "nie";

  private static final Logger log = LoggerFactory.getLogger(BackupAlertMailHandler.class);

  private final JavaMailSender mailSender;
  private final boolean mailEnabled;
  private final String from;
  private final String productName;
  private final String instanz;
  private final Texte texte;

  BackupAlertMailHandler(
      JavaMailSender mailSender,
      @Value("${manban.mail.enabled:false}") boolean mailEnabled,
      @Value("${manban.mail.from:no-reply@kanban-kit.local}") String from,
      @Value("${manban.mail.product-name:kanban-kit}") String productName,
      // Die Adresse der Instanz statt eines eigenen Namens: Wer mehrere Instanzen betreibt,
      // erkennt an ihr sofort, welche sich meldet — und kann sie unmittelbar aufrufen.
      @Value("${manban.auth.base-url:http://localhost:8080}") String instanz,
      Texte texte) {
    this.mailSender = mailSender;
    this.mailEnabled = mailEnabled;
    this.from = from;
    this.productName = productName;
    this.instanz = instanz;
    this.texte = texte;
  }

  @Override
  public String eventType() {
    return TYPE;
  }

  @Override
  public void handle(String payload) {
    List<String> fields = PayloadFields.split(payload, 3);
    String adminEmail = fields.get(0);
    BackupVerdict verdict = BackupVerdict.valueOf(fields.get(1));
    Sprache sprache = texte.eingestellte();
    String art = art(sprache, verdict);
    String letzterErfolg = letzterErfolg(sprache, fields.get(2));
    if (!mailEnabled) {
      log.warn(
          "[DEV] Sicherungs-Alarm für {} ({}): {} — letzter gelungener Lauf: {}",
          instanz,
          adminEmail,
          art,
          letzterErfolg);
      return;
    }
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(adminEmail);
    message.setSubject(
        texte.text(sprache, "mail.sicherungsalarm.betreff", productName, instanz, art));
    message.setText(texte.text(sprache, "mail.sicherungsalarm.text", instanz, art, letzterErfolg));
    mailSender.send(message);
    log.info("Sicherungs-Alarm an {} versandt", adminEmail);
  }

  /**
   * Die Art des Ausfalls in Worten. Der Wachhund alarmiert nur bei {@link BackupVerdict#VERALTET}
   * und {@link BackupVerdict#FEHLGESCHLAGEN}; die Unterscheidung ist die zwischen „schweigt zu
   * lange" und „ist kaputt" (Plan #825 E6).
   */
  private String art(Sprache sprache, BackupVerdict verdict) {
    return texte.text(
        sprache,
        verdict == BackupVerdict.FEHLGESCHLAGEN
            ? "mail.sicherungsalarm.art.fehlgeschlagen"
            : "mail.sicherungsalarm.art.veraltet");
  }

  /** Der Zeitpunkt aus der Payload in UTC nach der Schreibweise der Sprache, oder „noch nie". */
  private String letzterErfolg(Sprache sprache, String marke) {
    if (NIE.equals(marke)) {
      return texte.text(sprache, "mail.sicherungsalarm.noch-nie");
    }
    DateTimeFormatter muster =
        DateTimeFormatter.ofPattern(
                texte.text(sprache, "mail.sicherungsalarm.zeitpunkt-muster"), sprache.locale())
            .withZone(ZoneOffset.UTC);
    return muster.format(Instant.parse(marke));
  }
}
