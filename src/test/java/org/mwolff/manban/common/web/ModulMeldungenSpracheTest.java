package org.mwolff.manban.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.AccessTokenNotFoundException;
import org.mwolff.manban.accesstoken.application.InvalidTokenBindingException;
import org.mwolff.manban.auth.application.InvalidCredentialsException;
import org.mwolff.manban.auth.application.UserNotApprovedException;
import org.mwolff.manban.comment.application.CommentNotFoundException;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.kanbancompat.application.IdempotencyKeyReusedException;
import org.mwolff.manban.kanbancompat.application.InvalidKanbanColumnException;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.mwolff.manban.project.application.MemberNotApprovedException;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;

/**
 * Die Meldungen der Module {@code auth}, {@code project}, {@code accesstoken}, {@code comment} und
 * {@code kanbancompat} kommen über den {@link GlobalExceptionHandler} bei Session-Anfrage und
 * Sprache Englisch englisch an, bei Token-Anfrage weiter deutsch (Issue #1641, Plan #1638 A5).
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
}
