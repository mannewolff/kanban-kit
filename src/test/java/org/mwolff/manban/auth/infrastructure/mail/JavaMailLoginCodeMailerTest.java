package org.mwolff.manban.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Zustellung der Anmeldecode-Mail (Issue #1804, Plan #1801 A12): Ohne Mailversand stehen Code und
 * Link im Protokoll, mit Mailversand nie (fachliches AK 9).
 */
@ExtendWith(OutputCaptureExtension.class)
class JavaMailLoginCodeMailerTest {

  private static final String CODE = "042137";
  private static final String LINK = "https://app/login/link?token=abc";

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private JavaMailLoginCodeMailer mailer(Sprache sprache, boolean mailEnabled) {
    return new JavaMailLoginCodeMailer(
        mailSender, mailEnabled, "no-reply@example.org", "kanban-kit", SprachFixture.mit(sprache));
  }

  private SimpleMailMessage versandt() {
    ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captured.capture());
    return captured.getValue();
  }

  @Test
  @DisplayName("Szenario: Betrieb ohne Mailversand")
  void ohneMailversandStehenCodeUndLinkImProtokoll(CapturedOutput output) {
    // Angenommen der Mailversand ist ausgeschaltet
    JavaMailLoginCodeMailer mailer = mailer(Sprache.DE, false);

    // Wenn ein Mitglied einen Code anfordert
    mailer.sendLoginCode("alice@example.org", CODE, LINK);

    // Dann steht der Code im Protokoll des Servers
    assertThat(output.getOut())
        .contains("[DEV] Anmeldecode für alice@example.org")
        .contains(CODE)
        .contains(LINK);
    verifyNoInteractions(mailSender);
  }

  @Test
  void mitMailversandStehtDerCodeNieImProtokoll(CapturedOutput output) {
    mailer(Sprache.DE, true).sendLoginCode("alice@example.org", CODE, LINK);

    assertThat(output.getOut())
        .contains("Anmeldecode-Mail an alice@example.org versandt")
        .doesNotContain(CODE)
        .doesNotContain("token=abc");
  }

  @Test
  void deutsch() {
    mailer(Sprache.DE, true).sendLoginCode("alice@example.org", CODE, LINK);

    SimpleMailMessage message = versandt();
    assertThat(message.getFrom()).isEqualTo("no-reply@example.org");
    assertThat(message.getTo()).containsExactly("alice@example.org");
    assertThat(message.getSubject()).isEqualTo("kanban-kit: Dein Anmeldecode " + CODE);
    assertThat(message.getText()).contains(CODE).contains(LINK);
  }

  @Test
  void englisch() {
    mailer(Sprache.EN, true).sendLoginCode("alice@example.org", CODE, LINK);

    SimpleMailMessage message = versandt();
    assertThat(message.getSubject()).isEqualTo("kanban-kit: Your sign-in code " + CODE);
    assertThat(message.getText()).contains(CODE).contains(LINK).contains("sign in");
  }
}
