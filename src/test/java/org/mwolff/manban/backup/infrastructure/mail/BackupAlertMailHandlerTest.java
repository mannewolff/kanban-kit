package org.mwolff.manban.backup.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.common.PayloadFields;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Zustellung des vorgemerkten Sicherungs-Alarms (Issue #828).
 *
 * <p>Die Mail muss aus sich heraus verständlich sein: Wer sie um drei Uhr nachts liest, hat weder
 * die Admin-Ansicht offen noch weiß er, welche der Instanzen sie geschickt hat.
 *
 * <p>Seit Issue #1643 in der eingestellten Sprache, der Zeitpunkt in UTC nach ihrer Schreibweise
 * (Plan #1638 E5).
 */
class BackupAlertMailHandlerTest {

  private static final String INSTANZ = "https://kanban.example.org";
  private static final String ZEITPUNKT = "2026-10-13T07:11:00Z";

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private BackupAlertMailHandler handler(boolean mailEnabled) {
    return handler(Sprache.DE, mailEnabled);
  }

  private BackupAlertMailHandler handler(Sprache sprache, boolean mailEnabled) {
    return new BackupAlertMailHandler(
        mailSender,
        mailEnabled,
        "no-reply@example.org",
        "kanban-kit",
        INSTANZ,
        SprachFixture.mit(sprache));
  }

  private SimpleMailMessage versandt() {
    ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captured.capture());
    return captured.getValue();
  }

  @Test
  void eventTypePasstZurVormerkung() {
    assertThat(handler(true).eventType()).isEqualTo("mail.backup-alert");
  }

  @Test
  void veraltetNenntInstanzArtUndLetztenGelungenenLauf() {
    // Given / When
    handler(true).handle(PayloadFields.join("admin@example.org", "VERALTET", ZEITPUNKT));

    // Then
    SimpleMailMessage message = versandt();
    assertThat(message.getFrom()).isEqualTo("no-reply@example.org");
    assertThat(message.getTo()).containsExactly("admin@example.org");
    assertThat(message.getSubject())
        .isEqualTo("kanban-kit (" + INSTANZ + "): Die Sicherung schweigt länger als vorgesehen");
    assertThat(message.getText())
        .isEqualTo(
            "Die Sicherung der Instanz "
                + INSTANZ
                + " meldet einen Ausfall.\n\nArt des Ausfalls: Die Sicherung schweigt länger als"
                + " vorgesehen\nLetzter gelungener Lauf: 13.10.2026, 07:11 UTC\n\nEine Sicherung,"
                + " die still scheitert, ist schlimmer als gar keine — bitte den"
                + " Sicherungs-Container und das Protokoll in der Admin-Ansicht prüfen.\n");
  }

  @Test
  void englischMitZeitpunktInUsSchreibweise() {
    // Given / When
    handler(Sprache.EN, true)
        .handle(PayloadFields.join("admin@example.org", "VERALTET", ZEITPUNKT));

    // Then
    SimpleMailMessage message = versandt();
    assertThat(message.getSubject())
        .isEqualTo(
            "kanban-kit (" + INSTANZ + "): The backup has been silent for longer than expected");
    assertThat(message.getText())
        .isEqualTo(
            "The backup of instance "
                + INSTANZ
                + " reports a failure.\n\nType of failure: The backup has been silent for longer"
                + " than expected\nLast successful run: Oct 13, 2026, 7:11 AM UTC\n\nA backup"
                + " that fails silently is worse than none at all — please check the backup"
                + " container and the log in the admin view.\n");
  }

  @Test
  void englischFehlgeschlagen() {
    // Given / When
    handler(Sprache.EN, true)
        .handle(PayloadFields.join("admin@example.org", "FEHLGESCHLAGEN", ZEITPUNKT));

    // Then
    SimpleMailMessage message = versandt();
    assertThat(message.getSubject()).isEqualTo("kanban-kit (" + INSTANZ + "): A backup run failed");
    assertThat(message.getText()).contains("Type of failure: A backup run failed\n");
  }

  @Test
  void englischOhneJemalsGelungenenLauf() {
    // Given / When
    handler(Sprache.EN, true).handle(PayloadFields.join("admin@example.org", "VERALTET", "nie"));

    // Then
    assertThat(versandt().getText()).contains("Last successful run: never\n");
  }

  @Test
  void zeitpunktAmNachmittagMitPm() {
    // Given / When — die Uhrzeit bleibt UTC, auch bei Englisch.
    handler(Sprache.EN, true)
        .handle(PayloadFields.join("admin@example.org", "VERALTET", "2026-10-13T19:05:00Z"));

    // Then
    assertThat(versandt().getText()).contains("Last successful run: Oct 13, 2026, 7:05 PM UTC\n");
  }

  @Test
  void fehlgeschlagenNenntDenAusfallStattDesSchweigens() {
    // Given / When
    handler(true).handle(PayloadFields.join("admin@example.org", "FEHLGESCHLAGEN", ZEITPUNKT));

    // Then
    SimpleMailMessage message = versandt();
    assertThat(message.getSubject())
        .isEqualTo("kanban-kit (" + INSTANZ + "): Ein Sicherungslauf ist gescheitert");
    assertThat(message.getText())
        .contains("Art des Ausfalls: Ein Sicherungslauf ist gescheitert\n");
  }

  @Test
  void ohneJemalsGelungenenLaufStehtDasInWortenInDerMail() {
    // Given / When
    handler(true).handle(PayloadFields.join("admin@example.org", "VERALTET", "nie"));

    // Then
    assertThat(versandt().getText()).contains("Letzter gelungener Lauf: noch nie\n");
  }

  @Test
  void beiAbgeschaltetemVersandGehtNichtsHinausUndNichtsScheitert() {
    // Given / When — der ausgelieferte Zustand: manban.mail.enabled=false.
    handler(false).handle(PayloadFields.join("admin@example.org", "VERALTET", ZEITPUNKT));

    // Then
    verifyNoInteractions(mailSender);
  }

  @Test
  void kaputtePayloadWirdAbgewiesenOhneZuVersenden() {
    // Given / When / Then
    assertThatThrownBy(() -> handler(true).handle(PayloadFields.join("a", "b")))
        .isInstanceOf(IllegalArgumentException.class);
    verifyNoInteractions(mailSender);
  }
}
