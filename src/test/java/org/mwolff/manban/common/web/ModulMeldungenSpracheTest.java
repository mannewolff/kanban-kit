package org.mwolff.manban.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.AccessTokenNotFoundException;
import org.mwolff.manban.accesstoken.application.InvalidTokenBindingException;
import org.mwolff.manban.attachment.application.AttachmentLimitExceededException;
import org.mwolff.manban.attachment.application.AttachmentNotFoundException;
import org.mwolff.manban.auth.application.InvalidCredentialsException;
import org.mwolff.manban.auth.application.UserNotApprovedException;
import org.mwolff.manban.board.application.BoardNotFoundException;
import org.mwolff.manban.board.application.ColumnNotEmptyException;
import org.mwolff.manban.card.application.CardNotFoundException;
import org.mwolff.manban.card.application.FreigabeLabelException;
import org.mwolff.manban.card.application.InvalidDependencyException;
import org.mwolff.manban.card.application.InvalidDerivedFromException;
import org.mwolff.manban.comment.application.CommentNotFoundException;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.kanbancompat.application.IdempotencyKeyReusedException;
import org.mwolff.manban.kanbancompat.application.InvalidKanbanColumnException;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.mwolff.manban.project.application.MemberNotApprovedException;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/**
 * Die Meldungen der Module {@code auth}, {@code project}, {@code accesstoken}, {@code comment},
 * {@code kanbancompat} (Issue #1641) sowie {@code card}, {@code board} und {@code attachment}
 * (Issue #1642) kommen über den {@link GlobalExceptionHandler} bei Session-Anfrage und Sprache
 * Englisch englisch an, bei Token-Anfrage weiter deutsch (Plan #1638 A5). Die Meldung aus {@code
 * nightrun} prüft {@code common.web.RegionszoneTest} (Plan #1765 E14).
 */
class ModulMeldungenSpracheTest {

  private final GlobalExceptionHandler handler =
      new GlobalExceptionHandler(SprachFixture.mit(Sprache.EN));

  @AfterEach
  void tearDown() {
    SprachFixture.abmelden();
  }

  /** Das {@code detail} einer Exception bei Session-Anfrage und eingestellter Sprache Englisch. */
  private String englischesDetail(RuntimeException ex) {
    SprachFixture.angemeldetMit("ROLE_USER");
    return handler.handleException(ex).getDetail();
  }

  @Test
  void authMeldungenAufEnglisch() {
    assertThat(englischesDetail(new InvalidCredentialsException()))
        .isEqualTo("Invalid credentials");
    assertThat(englischesDetail(new UserNotApprovedException()))
        .isEqualTo(
            "This account is waiting for approval by a platform admin."
                + " Please contact the operator of this instance.");
  }

  @Test
  void projectMeldungenAufEnglisch() {
    assertThat(englischesDetail(new ProjectAccessDeniedException()))
        .isEqualTo("You do not have permission for this project action");
    assertThat(englischesDetail(new MemberNotApprovedException("a@example.org")))
        .isEqualTo("User has not been approved yet: a@example.org");
  }

  @Test
  void accesstokenMeldungenAufEnglisch() {
    assertThat(englischesDetail(new AccessTokenNotFoundException()))
        .isEqualTo("Access token not found");
    assertThat(englischesDetail(InvalidTokenBindingException.boardGehoertNichtZuProjekt(1234, 56)))
        .isEqualTo("Board 1234 does not belong to project 56");
  }

  @Test
  void commentMeldungAufEnglisch() {
    assertThat(englischesDetail(new CommentNotFoundException())).isEqualTo("Comment not found");
  }

  @Test
  void kanbancompatMeldungenAufEnglisch() {
    assertThat(englischesDetail(InvalidKanbanColumnException.unbekannt("ANSTEHEND")))
        .isEqualTo("Unknown Kanban column: ANSTEHEND");
    assertThat(englischesDetail(new TokenNotBoundException()))
        .isEqualTo("Token is not bound to a board. Please use a board-bound Kanban token.");
  }

  @Test
  void kanbancompatMeldungFuerTokenBleibtDeutsch() {
    SprachFixture.angemeldetMit("AUTH_PAT");

    assertThat(
            handler.handleException(new IdempotencyKeyReusedException("k1", "POST /x")).getDetail())
        .isEqualTo(
            "Idempotency-Key 'k1' wurde bereits für POST /x verwendet — für einen neuen Befehl"
                + " einen neuen Schlüssel wählen.");
  }

  @Test
  void cardMeldungenAufEnglisch() {
    assertThat(englischesDetail(new CardNotFoundException())).isEqualTo("Card not found");
    assertThat(englischesDetail(InvalidDependencyException.unbekannteNummer(42)))
        .isEqualTo("Unknown card number: 42");
    assertThat(englischesDetail(FreigabeLabelException.nurMenschSetzt("kit:clarify")))
        .isEqualTo("Only a human on the board sets label kit:clarify");
  }

  @Test
  void cardFeldmeldungAufEnglisch() {
    SprachFixture.angemeldetMit("ROLE_USER");

    assertThat(
            handler
                .handleException(InvalidDerivedFromException.unbekannteNummer(1234))
                .getProperties())
        .containsEntry("fieldErrors", Map.of("derivedFrom", "Unknown card number as origin: 1234"));
  }

  @Test
  void cardMeldungFuerTokenBleibtDeutsch() {
    SprachFixture.angemeldetMit("AUTH_PAT");

    assertThat(handler.handleException(InvalidDependencyException.unbekannteNummer(42)).getDetail())
        .isEqualTo("Unbekannte Kartennummer: 42");
  }

  @Test
  void boardMeldungenAufEnglisch() {
    assertThat(englischesDetail(new BoardNotFoundException())).isEqualTo("Board not found");
    assertThat(englischesDetail(new ColumnNotEmptyException()))
        .isEqualTo("Column still contains cards and cannot be deleted");
  }

  @Test
  void attachmentMeldungenAufEnglisch() {
    assertThat(englischesDetail(new AttachmentNotFoundException()))
        .isEqualTo("Attachment not found");
    assertThat(englischesDetail(new AttachmentLimitExceededException(20)))
        .isEqualTo("At most 20 attachments per card are allowed");
  }
}
