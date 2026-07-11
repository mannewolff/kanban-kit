package org.mwolff.manban.accesstoken.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.AccessTokenService;
import org.mwolff.manban.accesstoken.application.AccessTokenService.AccessTokenView;
import org.mwolff.manban.accesstoken.application.AccessTokenService.CreatedAccessToken;

/** Unit-Tests des dünnen HTTP-Adapters für die Token-Verwaltung (Service gemockt). */
class AccessTokenControllerTest {

  private AccessTokenService service;
  private AccessTokenController controller;

  @BeforeEach
  void setUp() {
    service = mock(AccessTokenService.class);
    controller = new AccessTokenController(service);
  }

  @Test
  void create_delegatesToServiceAndReturnsResult() {
    // Given
    CreatedAccessToken created = new CreatedAccessToken(1L, "CI", "tk_plain");
    var request = new AccessTokenController.CreateAccessTokenRequest("CI", 7L, 9L);
    when(service.create(3L, "CI", 7L, 9L)).thenReturn(created);

    // When
    CreatedAccessToken result = controller.create(3L, request);

    // Then
    assertThat(result).isSameAs(created);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<AccessTokenView> views =
        List.of(new AccessTokenView(1L, "CI", null, null, Instant.EPOCH, null, false));
    when(service.list(3L)).thenReturn(views);

    // When
    List<AccessTokenView> result = controller.list(3L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void revoke_delegatesToService() {
    // When
    controller.revoke(3L, 5L);

    // Then
    verify(service).revoke(3L, 5L);
  }
}
