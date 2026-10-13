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

/** Passwort-Reset-Mail in der eingestellten Sprache (Issue #1643, Plan #1638 A6). */
class JavaMailPasswordResetMailerTest {

  private static final String LINK = "https://app/reset?token=abc";

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private JavaMailPasswordResetMailer mailer(Sprache sprache, boolean mailEnabled) {
    return new JavaMailPasswordResetMailer(
        mailSender, mailEnabled, "no-reply@example.org", "kanban-kit", SprachFixture.mit(sprache));
  }

  private SimpleMailMessage versandt() {
    ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captured.capture());
    return captured.getValue();
  }

  @Test
  void deutschWieBisher() {
    mailer(Sprache.DE, true).sendPasswordResetEmail("alice@example.org", LINK);

    SimpleMailMessage message = versandt();
    assertThat(message.getFrom()).isEqualTo("no-reply@example.org");
    assertThat(message.getTo()).containsExactly("alice@example.org");
    assertThat(message.getSubject()).isEqualTo("kanban-kit: Passwort zurücksetzen");
    assertThat(message.getText())
        .isEqualTo("Setze dein Passwort über diesen Link zurück:\n\n" + LINK + "\n");
  }

  @Test
  void englisch() {
    mailer(Sprache.EN, true).sendPasswordResetEmail("alice@example.org", LINK);

    SimpleMailMessage message = versandt();
    assertThat(message.getSubject()).isEqualTo("kanban-kit: Reset your password");
    assertThat(message.getText())
        .isEqualTo("Reset your password using this link:\n\n" + LINK + "\n");
  }

  @Test
  void abgeschaltetGehtNichtsHinaus() {
    mailer(Sprache.EN, false).sendPasswordResetEmail("alice@example.org", LINK);

    verifyNoInteractions(mailSender);
  }
}
