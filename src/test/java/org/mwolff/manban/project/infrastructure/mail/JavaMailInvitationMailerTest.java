package org.mwolff.manban.project.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Einladung und Zuordnung in der eingestellten Sprache (Issue #1643, Plan #1638 A6). Der
 * Projektname ist Nutzerinhalt und erscheint unverändert, auch mit Apostroph.
 */
class JavaMailInvitationMailerTest {

  private static final String EINLADUNG = "https://app/invitations/accept?token=abc";
  private static final String PROJEKT_URL = "https://app/projects/9";

  private final JavaMailSender mailSender = mock(JavaMailSender.class);

  private JavaMailInvitationMailer mailer(Sprache sprache, boolean mailEnabled) {
    return new JavaMailInvitationMailer(
        mailSender, mailEnabled, "no-reply@example.org", "kanban-kit", SprachFixture.mit(sprache));
  }

  private SimpleMailMessage versandt() {
    ArgumentCaptor<SimpleMailMessage> captured = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(captured.capture());
    return captured.getValue();
  }

  @Test
  void einladungDeutschWieBisher() {
    mailer(Sprache.DE, true).sendInvitationEmail("bob@example.org", "Bob's {0} Projekt", EINLADUNG);

    SimpleMailMessage message = versandt();
    assertThat(message.getFrom()).isEqualTo("no-reply@example.org");
    assertThat(message.getTo()).containsExactly("bob@example.org");
    assertThat(message.getSubject())
        .isEqualTo("kanban-kit: Einladung ins Projekt Bob's {0} Projekt");
    assertThat(message.getText())
        .isEqualTo(
            "Du wurdest ins Projekt 'Bob's {0} Projekt' eingeladen.\n\n"
                + "So nimmst du die Einladung an:\n"
                + "1. Registriere dich mit genau dieser E-Mail-Adresse (der, an die diese Nachricht"
                + " ging).\n"
                + "2. Bestätige deine E-Mail über den Link aus der Bestätigungs-Mail.\n"
                + "3. Öffne anschließend diesen Link, um die Einladung anzunehmen:\n\n"
                + EINLADUNG
                + "\n\n"
                + "Hast du bereits ein Konto mit dieser E-Mail-Adresse, genügt Schritt 3.\n");
  }

  @Test
  void einladungEnglisch() {
    mailer(Sprache.EN, true).sendInvitationEmail("bob@example.org", "Projekt P", EINLADUNG);

    SimpleMailMessage message = versandt();
    assertThat(message.getSubject()).isEqualTo("kanban-kit: Invitation to project Projekt P");
    assertThat(message.getText())
        .isEqualTo(
            "You have been invited to project 'Projekt P'.\n\n"
                + "How to accept the invitation:\n"
                + "1. Register with exactly this email address (the one this message was sent"
                + " to).\n"
                + "2. Confirm your email address using the link in the confirmation email.\n"
                + "3. Then open this link to accept the invitation:\n\n"
                + EINLADUNG
                + "\n\n"
                + "If you already have an account with this email address, step 3 is enough.\n");
  }

  @Test
  void zuordnungDeutschWieBisher() {
    mailer(Sprache.DE, true)
        .sendProjectAssignedEmail("bob@example.org", "Projekt P", ProjectRole.ADMIN, PROJEKT_URL);

    SimpleMailMessage message = versandt();
    assertThat(message.getFrom()).isEqualTo("no-reply@example.org");
    assertThat(message.getTo()).containsExactly("bob@example.org");
    assertThat(message.getSubject())
        .isEqualTo("kanban-kit: Du wurdest dem Projekt Projekt P hinzugefügt");
    assertThat(message.getText())
        .isEqualTo(
            "Du wurdest dem Projekt 'Projekt P' als ADMIN hinzugefügt. Zum Projekt:\n\n"
                + PROJEKT_URL
                + "\n");
  }

  @Test
  void zuordnungEnglisch() {
    mailer(Sprache.EN, true)
        .sendProjectAssignedEmail("bob@example.org", "Projekt P", ProjectRole.ADMIN, PROJEKT_URL);

    SimpleMailMessage message = versandt();
    assertThat(message.getSubject())
        .isEqualTo("kanban-kit: You have been added to project Projekt P");
    assertThat(message.getText())
        .isEqualTo(
            "You have been added to project 'Projekt P' as ADMIN. Go to the project:\n\n"
                + PROJEKT_URL
                + "\n");
  }

  @Test
  void abgeschaltetGehtNichtsHinaus() {
    JavaMailInvitationMailer mailer = mailer(Sprache.EN, false);

    mailer.sendInvitationEmail("bob@example.org", "Projekt P", EINLADUNG);
    mailer.sendProjectAssignedEmail("bob@example.org", "Projekt P", ProjectRole.ADMIN, PROJEKT_URL);

    verifyNoInteractions(mailSender);
  }
}
