package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.AdminService.UserView;
import org.mwolff.manban.auth.application.BootstrapService;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Unit-Tests des Admin-Bootstrap-Controllers (Service gemockt). */
class BootstrapControllerTest {

  private BootstrapService service;
  private BootstrapController controller;

  @BeforeEach
  void setUp() {
    service = mock(BootstrapService.class);
    controller = new BootstrapController(service);
  }

  @Test
  void bootstrap_delegatesToService() {
    // Given
    UserView user = new UserView(3L, "a@b.de", "A", PlatformRole.ADMIN, true, Instant.EPOCH, false);
    var request = new BootstrapController.BootstrapRequest("env-token");
    when(service.bootstrap(3L, "env-token")).thenReturn(user);

    // When
    UserView result = controller.bootstrap(3L, request);

    // Then
    assertThat(result).isSameAs(user);
  }
}
