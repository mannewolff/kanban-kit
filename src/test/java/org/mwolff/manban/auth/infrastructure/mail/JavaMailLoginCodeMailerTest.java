package org.mwolff.manban.auth.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * Link im Protokoll, mit Mailversand nie (fachliches AK 9). Ins Protokoll gelangen sie nur im
 * ausdrücklich eingeschalteten Entwicklungsbetrieb (Issue #2267).
 */
@ExtendWith(OutputCaptureExtension.class)
class JavaMailLoginCodeMailerTest {

  private static final String CODE = "042137";
  private static final String LINK = "https://app/login/link?token=abc";

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private JavaMailLoginCodeMailer mailer(Sprache sprache, boolean mailEnabled) {
    return mailer(sprache, mailEnabled, true);
  }

  private JavaMailLoginCodeMailer mailer(Sprache sprache, boolean mailEnabled, boolean devMode) {
    return new JavaMailLoginCodeMailer(
        mailSender,
        mailEnabled,
        devMode,
        "no-reply@example.org",
        "kanban-kit",
        SprachFixture.mit(sprache));
  }

  private SimpleMailMessage versandt() {
    ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captured.capture());
    return captured.getValue();
  }

  @Test
  @DisplayName("Szenario: Betrieb ohne Mailversand")
  void ohneMailversandStehenCodeUndLinkImProtokoll(CapturedOutput output) {
    // Angenommen der Mailversand ist ausgeschaltet, im Entwicklungsbetrieb
    JavaMailLoginCodeMailer mailer = mailer(Sprache.DE, false, true);

    // Wenn ein Mitglied einen Code anfordert
    mailer.sendLoginCode("alice@example.org", CODE, LINK);

    // Dann steht der Code im Protokoll des Servers
    assertThat(output.getOut())
        .contains("[DEV] Anmeldecode für alice@example.org")
        .contains(CODE)
        .contains(LINK);
    verifyNoInteractions(mailSender);
  }

  /**
   * Angriff aus Issue #2267: Wer die Anwendungsprotokolle lesen darf, aber nicht das Postfach des
   * Kontoinhabers, darf dort keinen gültigen Anmeldecode und keinen Anmeldelink finden — auch nicht
   * im Text der Ausnahme, die der Outbox-Worker protokolliert. Ohne Entwicklungsbetrieb ist ein
   * abgeschalteter Mailversand ein Konfigurationsfehler.
   */
  @Test
  void ohneEntwicklungsbetriebGelangtKeinAnmeldegeheimnisInsProtokoll(CapturedOutput output) {
    JavaMailLoginCodeMailer mailer = mailer(Sprache.DE, false, false);

    assertThatThrownBy(() -> mailer.sendLoginCode("alice@example.org", CODE, LINK))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("manban.mail.enabled")
        .hasMessageNotContaining(CODE)
        .hasMessageNotContaining("token=abc");

    assertThat(output.getAll())
        .contains("Anmeldecode für alice@example.org nicht zugestellt")
        .doesNotContain(CODE)
        .doesNotContain("token=abc")
        .doesNotContain("[DEV]");
    verifyNoInteractions(mailSender);
  }

  @Test
  void mitMailversandStehtDerCodeNieImProtokoll(CapturedOutput output) {
    mailer(Sprache.DE, true, false).sendLoginCode("alice@example.org", CODE, LINK);

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
