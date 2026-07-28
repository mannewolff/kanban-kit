package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.application.InvalidCardNumberException;
import org.mwolff.manban.card.application.ProjectStartNumberService;
import org.mwolff.manban.card.web.ProjectStartNumberController.NextCardNumberRequest;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Tests der projektweiten Startnummer-Endpoints (Service gemockt). Läuft bewusst über einen
 * standalone MockMvc statt über direkte Methodenaufrufe: nur so werden die Mapping-Pfade,
 * {@code @PathVariable}, {@code @AuthenticationPrincipal} und vor allem das {@code @Valid} am
 * Request-Body tatsächlich ausgelöst — ein Entfernen dieser Annotationen fällt sonst keinem Test
 * auf.
 */
class ProjectStartNumberControllerTest {

  private static final long USER = 7L;
  private static final long PROJECT = 3L;
  private static final String PATH = "/api/projects/" + PROJECT + "/next-card-number";

  private ProjectStartNumberService service;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(ProjectStartNumberService.class);
    mvc =
        MockMvcBuilders.standaloneSetup(new ProjectStartNumberController(service))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(USER, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void get_returnsEffectiveNextCardNumber() throws Exception {
    when(service.effectiveNextCardNumber(USER, PROJECT)).thenReturn(13457);

    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextCardNumber").value(13457));

    verify(service).effectiveNextCardNumber(USER, PROJECT);
  }

  @Test
  void get_propagatesNotFound_forNonMember() throws Exception {
    when(service.effectiveNextCardNumber(USER, PROJECT)).thenThrow(new ProjectNotFoundException());

    mvc.perform(get(PATH)).andExpect(status().isNotFound());
  }

  @Test
  void set_returnsNewEffectiveNextCardNumber() throws Exception {
    when(service.setNextCardNumber(USER, PROJECT, 13457)).thenReturn(13457);

    mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body(13457)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nextCardNumber").value(13457));

    verify(service).setNextCardNumber(USER, PROJECT, 13457);
  }

  /**
   * Pinnt das {@code @Valid} am Request-Body: Ohne die Annotation erreichte die 0 den Service und
   * der Aufruf endete mit 200 statt 400.
   */
  @Test
  void set_rejectsValueBelowOne_beforeReachingService() throws Exception {
    mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body(0)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  @Test
  void set_propagatesBadRequest_whenNumberAlreadyTaken() throws Exception {
    when(service.setNextCardNumber(USER, PROJECT, 13457))
        .thenThrow(new InvalidCardNumberException("bereits vergeben"));

    mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body(13457)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void set_propagatesForbidden_forMemberWithoutEditPermission() throws Exception {
    when(service.setNextCardNumber(USER, PROJECT, 13457))
        .thenThrow(new ProjectAccessDeniedException());

    mvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body(13457)))
        .andExpect(status().isForbidden());
  }

  /**
   * Pinnt die {@code @Min(1)}-Bedingung des Request-Records an drei Grenzwerten — ergänzend zum
   * MockMvc-Fall oben, der nur den Weg über Spring belegt.
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

  private static String body(int nextCardNumber) {
    return "{\"nextCardNumber\":" + nextCardNumber + "}";
  }
}
