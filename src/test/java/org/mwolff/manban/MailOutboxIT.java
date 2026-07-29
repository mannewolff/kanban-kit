package org.mwolff.manban;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mwolff.manban.auth.application.AdminNotificationMailer;
import org.mwolff.manban.auth.application.AppUserRepository;
import org.mwolff.manban.auth.application.RegisterUserService;
import org.mwolff.manban.auth.domain.AppUser;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.mwolff.manban.outbox.application.OutboxDispatchService;
import org.mwolff.manban.project.application.InviteOutcome;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.ProjectMembershipRepository;
import org.mwolff.manban.project.application.ProjectRepository;
import org.mwolff.manban.project.domain.Project;
import org.mwolff.manban.project.domain.ProjectMembership;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Die neue Mail-Fehlersemantik gegen echtes Postgres (Issue #502): HTTP-Erfolg bestätigt die
 * persistierte fachliche Operation, die Zustellung läuft nach dem Commit über die Outbox.
 *
 * <p>Der geplante Worker ist abgeschaltet und der Durchlauf direkt aufgerufen (wie im {@code
 * OutboxIT}); der {@link JavaMailSender} ist ein Mockito-Mock, dessen Fehlverhalten die Tests
 * steuern. Den echten SMTP-Pfad inklusive Worker deckt der {@code SmtpMailIT} ab.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
    properties = {
      "manban.outbox.enabled=false",
      "manban.mail.enabled=true",
      // Eigener Spring-Kontext (Primary-Mock + Properties) → zweiter Verbindungspool; klein
      // halten, damit die Suite die max_connections des geteilten Containers nicht reißt.
      "spring.datasource.hikari.maximum-pool-size=3",
      "spring.datasource.hikari.minimum-idle=1"
    })
class MailOutboxIT extends AbstractIntegrationTest {

  @TestConfiguration
  static class MailSenderMockConfig {

    @Bean
    @Primary
    JavaMailSender mockMailSender() {
      return Mockito.mock(JavaMailSender.class);
    }
  }

  @Autowired private RegisterUserService registerService;
  @Autowired private AdminNotificationMailer adminMailer;
  @Autowired private MembershipService membershipService;
  @Autowired private OutboxDispatchService dispatch;
  @Autowired private AppUserRepository users;
  @Autowired private ProjectRepository projects;
  @Autowired private ProjectMembershipRepository memberships;
  @Autowired private JavaMailSender mailSender;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private PlatformTransactionManager transactionManager;

  /** Der Mock ist ein Singleton im geteilten Kontext — sein Zustand darf es nicht sein. */
  @BeforeEach
  void resetMailSenderMock() {
    Mockito.reset(mailSender);
  }

  private List<Map<String, Object>> entries() {
    return jdbc.queryForList("SELECT * FROM outbox_entry ORDER BY id");
  }

  private Map<String, Object> onlyEntry() {
    List<Map<String, Object>> all = entries();
    assertThat(all).hasSize(1);
    return all.get(0);
  }

  /** Wie im {@code OutboxIT}: JVM-Uhr statt Postgres-{@code now()}, Begründung dort. */
  private void makeDue() {
    jdbc.update(
        "UPDATE outbox_entry SET next_attempt_at = ? WHERE status = 'PENDING'",
        Timestamp.from(Instant.now().minus(Duration.ofHours(1))));
  }

  private int userCount(String email) {
    Integer count =
        jdbc.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email);
    return count == null ? 0 : count;
  }

  @Test
  void rollbackAfterScheduling_leavesNeitherUserNorMail() {
    // Given / When — die Registrierung läuft, dann kippt die umgebende Transaktion.
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    status -> {
                      registerService.register("rollback@example.org", "sup3r-secret", "Rolli");
                      throw new IllegalStateException("fachlicher Abbruch nach der Registrierung");
                    }))
        .isInstanceOf(IllegalStateException.class);

    // Then — weder Benutzer noch Vormerkung; ein Worker-Lauf hätte nichts zuzustellen.
    assertThat(userCount("rollback@example.org")).isZero();
    assertThat(entries()).isEmpty();
    assertThat(dispatch.dispatchDue()).isZero();
    Mockito.verifyNoInteractions(mailSender);
  }

  @Test
  void commit_deliversOnceAndClearsTheSecretLink() {
    // Given
    registerService.register("alice@example.org", "sup3r-secret", "Alice");

    // When
    int dispatched = dispatch.dispatchDue();

    // Then — genau eine Zustellung an die richtige Adresse mit dem Verifikations-Link.
    assertThat(dispatched).isEqualTo(1);
    ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(sent.capture());
    assertThat(sent.getValue().getTo()).containsExactly("alice@example.org");
    assertThat(sent.getValue().getText()).contains("/verify?token=");

    // Kein Klartext-Token bleibt dauerhaft in der Outbox liegen (Akzeptanzkriterium).
    assertThat(onlyEntry()).containsEntry("status", "DONE").containsEntry("payload", "");

    // Ein weiterer Lauf stellt nicht erneut zu.
    assertThat(dispatch.dispatchDue()).isZero();
    verify(mailSender, Mockito.times(1)).send(any(SimpleMailMessage.class));
  }

  @Test
  void temporarySmtpFailure_isRetriedWithoutRepeatingTheRegistration() {
    // Given — der erste Sendeversuch scheitert, der zweite gelingt.
    Mockito.doThrow(new MailSendException("smtp down"))
        .doNothing()
        .when(mailSender)
        .send(any(SimpleMailMessage.class));
    registerService.register("bob@example.org", "sup3r-secret", "Bob");

    // When — Fehlversuch: Eintrag bleibt offen, die Registrierung bleibt unangetastet.
    assertThat(dispatch.dispatchDue()).isZero();

    // Then
    assertThat(onlyEntry()).containsEntry("status", "PENDING").containsEntry("attempts", 1);
    assertThat(userCount("bob@example.org")).isEqualTo(1);

    // When — der Wiederholungslauf stellt zu, ohne die fachliche Operation zu wiederholen.
    makeDue();
    assertThat(dispatch.dispatchDue()).isEqualTo(1);

    // Then
    assertThat(onlyEntry()).containsEntry("status", "DONE").containsEntry("attempts", 2);
    assertThat(userCount("bob@example.org")).isEqualTo(1);
    verify(mailSender, Mockito.times(2)).send(any(SimpleMailMessage.class));
  }

  @Test
  void duplicateAdminNotification_collapsesToOneDelivery() {
    // Given — derselbe Anlass zweimal eingeplant (z. B. wiederholter Verifikationspfad); der
    // Schreibweg verlangt eine laufende Transaktion (MANDATORY).
    TransactionTemplate tx = new TransactionTemplate(transactionManager);
    tx.executeWithoutResult(
        status ->
            adminMailer.sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Neu"));
    tx.executeWithoutResult(
        status ->
            adminMailer.sendNewUserPendingApproval("admin@example.org", "neu@example.org", "Neu"));

    // When / Then — ein Eintrag, eine Zustellung (Akzeptanzkriterium).
    assertThat(entries()).hasSize(1);
    assertThat(dispatch.dispatchDue()).isEqualTo(1);
    verify(mailSender, Mockito.times(1)).send(any(SimpleMailMessage.class));
  }

  @Test
  void invitationMailFailure_keepsTheInvitation() {
    // Given — ein Projekt mit OWNER; der Mailversand ist dauerhaft kaputt.
    long ownerId =
        users
            .save(new AppUser(null, "owner@example.org", "hash", "Owner", true, PlatformRole.USER))
            .requireId();
    long projectId = projects.save(new Project(null, "P", ownerId, Instant.now())).requireId();
    memberships.save(
        new ProjectMembership(null, projectId, ownerId, ProjectRole.OWNER, Instant.now()));
    Mockito.doThrow(new MailSendException("smtp down"))
        .when(mailSender)
        .send(any(SimpleMailMessage.class));

    // When — früher ein 502 mit Rollback; jetzt die bewusste Entscheidung aus Issue #502: Die
    // Einladung bleibt bestehen, der Versand wird vom Worker nachgeholt.
    InviteOutcome outcome =
        membershipService.invite(ownerId, projectId, "gast@example.org", ProjectRole.MEMBER);

    // Then
    assertThat(outcome).isEqualTo(InviteOutcome.INVITED);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM project_invitation", Integer.class))
        .isEqualTo(1);
    assertThat(dispatch.dispatchDue()).isZero();
    assertThat(onlyEntry()).containsEntry("status", "PENDING").containsEntry("attempts", 1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM project_invitation", Integer.class))
        .isEqualTo(1);
  }
}
