package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.InvalidCardNumberException;
import org.mwolff.manban.card.application.ProjectStartNumberService;
import org.mwolff.manban.card.web.ProjectStartNumberController.NextCardNumberRequest;
import org.mwolff.manban.card.web.ProjectStartNumberController.NextCardNumberView;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Unit-Tests der projektweiten Startnummer-Endpoints (Service gemockt). */
class ProjectStartNumberControllerTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;

  private ProjectStartNumberService service;
  private ProjectStartNumberController controller;

  @BeforeEach
  void setUp() {
    service = mock(ProjectStartNumberService.class);
    controller = new ProjectStartNumberController(service);
  }

  @Test
  void get_returnsEffectiveNextCardNumber() {
    when(service.effectiveNextCardNumber(USER, PROJECT)).thenReturn(13457);

    NextCardNumberView result = controller.get(USER, PROJECT);

    assertThat(result.nextCardNumber()).isEqualTo(13457);
    verify(service).effectiveNextCardNumber(USER, PROJECT);
  }

  @Test
  void get_propagatesNotFound_forNonMember() {
    when(service.effectiveNextCardNumber(USER, PROJECT)).thenThrow(new ProjectNotFoundException());

    assertThatThrownBy(() -> controller.get(USER, PROJECT))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void set_returnsNewEffectiveNextCardNumber() {
    when(service.setNextCardNumber(USER, PROJECT, 13457)).thenReturn(13457);

    NextCardNumberView result = controller.set(USER, PROJECT, new NextCardNumberRequest(13457));

    assertThat(result.nextCardNumber()).isEqualTo(13457);
    verify(service).setNextCardNumber(USER, PROJECT, 13457);
  }

  @Test
  void set_propagatesBadRequest_whenNumberAlreadyTaken() {
    when(service.setNextCardNumber(USER, PROJECT, 13457))
        .thenThrow(new InvalidCardNumberException("bereits vergeben"));

    assertThatThrownBy(() -> controller.set(USER, PROJECT, new NextCardNumberRequest(13457)))
        .isInstanceOf(InvalidCardNumberException.class);
  }

  @Test
  void set_propagatesForbidden_forMemberWithoutEditPermission() {
    when(service.setNextCardNumber(USER, PROJECT, 13457))
        .thenThrow(new ProjectAccessDeniedException());

    assertThatThrownBy(() -> controller.set(USER, PROJECT, new NextCardNumberRequest(13457)))
        .isInstanceOf(ProjectAccessDeniedException.class);
  }

  /**
   * Pinnt die {@code @Min(1)}-Bedingung des Request-Records: 0 und negative Werte werden schon von
   * der Bean-Validation abgelehnt (400), bevor der Service gerufen wird.
   */
  @Test
  void request_rejectsValuesBelowOne() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      Validator validator = factory.getValidator();

      assertThat(validator.validate(new NextCardNumberRequest(1))).isEmpty();
      assertThat(validator.validate(new NextCardNumberRequest(0))).hasSize(1);
      assertThat(validator.validate(new NextCardNumberRequest(-1))).hasSize(1);
    }
  }
}
