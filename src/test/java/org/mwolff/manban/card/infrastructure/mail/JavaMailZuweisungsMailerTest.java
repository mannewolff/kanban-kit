package org.mwolff.manban.card.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
 * Nachricht an neu Zuständige in der eingestellten Sprache (Issue #1916): Nummer, Titel, Projekt,
 * Board, wer zugewiesen hat, und der direkte Link der Karte — nie der Kartentext, den die Nachricht
 * gar nicht kennt. Ohne Mailversand stehen Empfänger und Link im Protokoll.
 */
@ExtendWith(OutputCaptureExtension.class)
class JavaMailZuweisungsMailerTest {

  private static final String LINK = "https://app/projects/9/karten/1916";
  private static final JavaMailZuweisungsMailer.Zuweisung ZUWEISUNG =
      new JavaMailZuweisungsMailer.Zuweisung(
          "bo@example.org", "1916", "Bob's {0} Karte", "Projekt P", "Board B", "Manne", LINK);

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private JavaMailZuweisungsMailer mailer(Sprache sprache, boolean mailEnabled) {
    return new JavaMailZuweisungsMailer(
        mailSender, mailEnabled, "no-reply@example.org", "kanban-kit", SprachFixture.mit(sprache));
  }

  private SimpleMailMessage versandt() {
    ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captured.capture());
    return captured.getValue();
  }

  @Test
  void deutsch() {
    mailer(Sprache.DE, true).sendZuweisungsEmail(ZUWEISUNG);

    SimpleMailMessage message = versandt();
    assertThat(message.getFrom()).isEqualTo("no-reply@example.org");
    assertThat(message.getTo()).containsExactly("bo@example.org");
    assertThat(message.getSubject()).isEqualTo("kanban-kit: Karte #1916 ist dir zugewiesen");
    assertThat(message.getText())
        .isEqualTo(
            "Dir wurde die Karte #1916 'Bob's {0} Karte' zugewiesen.\n\n"
                + "Projekt: Projekt P\n"
                + "Board: Board B\n"
                + "Zugewiesen von: Manne\n\n"
                + "Zur Karte:\n\n"
                + LINK
                + "\n");
  }

  @Test
  void englisch() {
    mailer(Sprache.EN, true).sendZuweisungsEmail(ZUWEISUNG);

    SimpleMailMessage message = versandt();
    assertThat(message.getSubject()).isEqualTo("kanban-kit: Card #1916 has been assigned to you");
    assertThat(message.getText())
        .isEqualTo(
            "Card #1916 'Bob's {0} Karte' has been assigned to you.\n\n"
                + "Project: Projekt P\n"
                + "Board: Board B\n"
                + "Assigned by: Manne\n\n"
                + "Open the card:\n\n"
                + LINK
                + "\n");
  }

  @Test
  void mitMailversandMeldetDasProtokollNurDenVersand(CapturedOutput output) {
    mailer(Sprache.DE, true).sendZuweisungsEmail(ZUWEISUNG);

    assertThat(output.getOut())
        .contains("Zuweisungs-E-Mail an bo@example.org versandt")
        .doesNotContain("[DEV]");
  }

  @Test
  void abgeschaltetGehtNichtsHinausUndDasProtokollTraegtDenLink(CapturedOutput output) {
    mailer(Sprache.DE, false).sendZuweisungsEmail(ZUWEISUNG);

    verifyNoInteractions(mailSender);
    assertThat(output.getOut())
        .contains("[DEV] Karte #1916 ist bo@example.org zugewiesen: " + LINK);
  }
}
