package org.mwolff.manban.fremdboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.fremdboard.application.AnstossService;
import org.mwolff.manban.fremdboard.application.KeinFremdesBoardException;
import org.mwolff.manban.fremdboard.web.FremdBoardAnstossController.AnstossRequest;
import org.mwolff.manban.fremdboard.web.FremdBoardAnstossController.AnstossResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;

/**
 * Anmeldung am Anstoß ohne gebundenes Token (Issue #2184): Fehlt die Anmeldung oder trägt sie
 * keinen Token-Principal, gibt es kein Board, also 409 — die Sicherheitskette lässt beides über
 * HTTP nicht durch, darum prüft der Test den Controller direkt.
 */
class FremdBoardAnstossControllerTest {

  private AnstossService service;
  private FremdBoardAnstossController controller;

  @BeforeEach
  void aufbau() {
    service = mock(AnstossService.class);
    controller = new FremdBoardAnstossController(service);
  }

  @Test
  void ohneAnmeldungGibtEsKeinBoard() {
    assertThatThrownBy(() -> controller.anstossen(null, new AnstossRequest(7)))
        .isInstanceOf(KeinFremdesBoardException.class);
    verify(service, never()).anstossen(anyLong(), anyLong(), anyInt());
  }

  @Test
  void anmeldungOhneTokenPrincipalGibtKeinBoard() {
    TestingAuthenticationToken sitzung = new TestingAuthenticationToken("anna", null, List.of());
    sitzung.setDetails("keine Token-Angaben");

    assertThatThrownBy(() -> controller.anstossen(sitzung, new AnstossRequest(7)))
        .isInstanceOf(KeinFremdesBoardException.class);
    verify(service, never()).anstossen(anyLong(), anyLong(), anyInt());
  }

  @Test
  void ungebundenerTokenPrincipalGibtKeinBoard() {
    TestingAuthenticationToken token = new TestingAuthenticationToken("anna", null, List.of());
    token.setDetails(new KanbanPrincipal(1L, 2L, null, null, "Kit"));

    assertThatThrownBy(() -> controller.anstossen(token, new AnstossRequest(7)))
        .isInstanceOf(KeinFremdesBoardException.class);
    verify(service, never()).anstossen(anyLong(), anyLong(), anyInt());
  }

  @Test
  void gebundenerTokenPrincipalStoesstImProjektDerBindungAn() {
    TestingAuthenticationToken token = new TestingAuthenticationToken("anna", null, List.of());
    token.setDetails(new KanbanPrincipal(1L, 2L, 30L, 40L, "Kit"));
    when(service.anstossen(1L, 30L, 7)).thenReturn(true);

    ResponseEntity<AnstossResponse> antwort = controller.anstossen(token, new AnstossRequest(7));

    assertThat(antwort.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(antwort.getBody()).isEqualTo(new AnstossResponse(true));
  }
}
