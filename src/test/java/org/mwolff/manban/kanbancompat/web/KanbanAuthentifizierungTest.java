package org.mwolff.manban.kanbancompat.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.kanbancompat.application.TokenNotBoundException;
import org.springframework.security.core.Authentication;

/** Unit-Tests der geteilten Principal-Hilfe der Kanban-Strecke (Issue #1696). */
class KanbanAuthentifizierungTest {

  private static final KanbanPrincipal PRINCIPAL = new KanbanPrincipal(7L, 1L, 2L, 3L, "Token");

  @Test
  void principal_boundAuthentication_returnsPrincipal() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn(PRINCIPAL);

    assertThat(KanbanAuthentifizierung.principal(authentication)).isSameAs(PRINCIPAL);
  }

  @Test
  void principal_nullAuthentication_throwsTokenNotBound() {
    assertThatThrownBy(() -> KanbanAuthentifizierung.principal(null))
        .isInstanceOf(TokenNotBoundException.class);
  }

  @Test
  void principal_detailsNotKanbanPrincipal_throwsTokenNotBound() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getDetails()).thenReturn("not-a-principal");

    assertThatThrownBy(() -> KanbanAuthentifizierung.principal(authentication))
        .isInstanceOf(TokenNotBoundException.class);
  }
}
