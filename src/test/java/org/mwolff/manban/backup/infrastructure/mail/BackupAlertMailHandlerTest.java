package org.mwolff.manban.backup.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.common.PayloadFields;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Zustellung des vorgemerkten Sicherungs-Alarms (Issue #828).
 *
 * <p>Die Mail muss aus sich heraus verständlich sein: Wer sie um drei Uhr nachts liest, hat weder
 * die Admin-Ansicht offen noch weiß er, welche der Instanzen sie geschickt hat.
 */
class BackupAlertMailHandlerTest {

  private static final String INSTANZ = "https://kanban.example.org";
  private static final String ZEITPUNKT = "2026-09-20T03:00:00Z";

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private BackupAlertMailHandler handler(boolean mailEnabled) {
    return new BackupAlertMailHandler(
        mailSender, mailEnabled, "no-reply@example.org", "kanban-kit", INSTANZ);
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
    assertThat(message.getSubject()).contains("kanban-kit", INSTANZ, "schweigt");
    assertThat(message.getText()).contains(INSTANZ, "schweigt", ZEITPUNKT);
  }

  @Test
  void fehlgeschlagenNenntDenAusfallStattDesSchweigens() {
    // Given / When
    handler(true).handle(PayloadFields.join("admin@example.org", "FEHLGESCHLAGEN", ZEITPUNKT));

    // Then
    SimpleMailMessage message = versandt();
    assertThat(message.getSubject()).contains("gescheitert");
    assertThat(message.getText()).contains("gescheitert");
  }

  @Test
  void ohneJemalsGelungenenLaufStehtDasInWortenInDerMail() {
    // Given / When
    handler(true).handle(PayloadFields.join("admin@example.org", "VERALTET", "nie"));

    // Then
    assertThat(versandt().getText()).contains("noch nie");
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
