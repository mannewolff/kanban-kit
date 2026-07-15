package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.mwolff.manban.auth.domain.PlatformRole;

/** Unit-Tests des Selbstauskunft-Controllers (Service gemockt). */
class MeControllerTest {

  private MeService service;
  private MeController controller;

  @BeforeEach
  void setUp() {
    service = mock(MeService.class);
    controller = new MeController(service);
  }

  @Test
  void me_delegatesToService() {
    // Given
    var view = new MeView(3L, "a@b.de", "Alice", PlatformRole.USER, List.of());
    when(service.load(3L)).thenReturn(view);

    // When
    MeView result = controller.me(3L);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void updateProfile_delegatesDisplayNameToService() {
    // Given
    var view = new MeView(3L, "a@b.de", "Neu", PlatformRole.USER, List.of());
    when(service.updateDisplayName(3L, "Neu")).thenReturn(view);

    // When
    MeView result = controller.updateProfile(3L, new MeController.UpdateProfileRequest("Neu"));

    // Then
    assertThat(result).isSameAs(view);
  }
}
