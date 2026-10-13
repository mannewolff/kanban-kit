package org.mwolff.manban.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Freigabe-Benachrichtigung in der eingestellten Sprache (Issue #1643, Plan #1638 A6). */
class JavaMailAdminNotificationMailerTest {

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private JavaMailAdminNotificationMailer mailer(Sprache sprache, boolean mailEnabled) {
    return new JavaMailAdminNotificationMailer(
        mailSender, mailEnabled, "no-reply@example.org", "kanban-kit", SprachFixture.mit(sprache));
  }

  private SimpleMailMessage versandt() {
    ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captured.capture());
    return captured.getValue();
  }

  @Test
  void deutschWieBisher() {
    mailer(Sprache.DE, true)
        .sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Neue Nutzerin");

    SimpleMailMessage message = versandt();
    assertThat(message.getFrom()).isEqualTo("no-reply@example.org");
    assertThat(message.getTo()).containsExactly("admin@example.org");
    assertThat(message.getSubject())
        .isEqualTo("kanban-kit: Neue Registrierung wartet auf Freigabe");
    assertThat(message.getText())
        .isEqualTo(
            """
            Neu registriert und verifiziert: Neue Nutzerin (neu@example.org).

            Bitte in der Nutzerverwaltung freigeben.
            """);
  }

  @Test
  void englisch() {
    mailer(Sprache.EN, true)
        .sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Neue Nutzerin");

    SimpleMailMessage message = versandt();
    assertThat(message.getSubject()).isEqualTo("kanban-kit: New registration awaiting approval");
    assertThat(message.getText())
        .isEqualTo(
            """
            Newly registered and verified: Neue Nutzerin (neu@example.org).

            Please approve the account in the user administration.
            """);
  }

  @Test
  void abgeschaltetGehtNichtsHinaus() {
    mailer(Sprache.EN, false)
        .sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Neue Nutzerin");

    verifyNoInteractions(mailSender);
  }
}
