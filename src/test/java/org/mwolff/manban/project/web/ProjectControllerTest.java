package org.mwolff.manban.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.project.application.InvalidMonthlyBudgetException;
import org.mwolff.manban.project.application.MembershipService;
import org.mwolff.manban.project.application.ProjectAccessDeniedException;
import org.mwolff.manban.project.application.ProjectNotFoundException;
import org.mwolff.manban.project.application.ProjectService;
import org.mwolff.manban.project.application.ProjectService.ProjectView;
import org.mwolff.manban.project.domain.ProjectRole;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Unit-Tests des Projekt-Controllers (Service gemockt). */
class ProjectControllerTest {

  private ProjectService service;
  private MembershipService memberships;
  private ProjectController controller;
  private MockMvc mvc;

  private static final String BUDGET_PATH = "/api/projects/5/monthly-budget";

  private static ProjectView project() {
    return new ProjectView(1L, "Project", ProjectRole.OWNER, Instant.EPOCH, false, true);
  }

  @BeforeEach
  void setUp() {
    service = mock(ProjectService.class);
    memberships = mock(MembershipService.class);
    controller = new ProjectController(service, memberships);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(3L, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void create_delegatesToService() {
    // Given
    ProjectView view = project();
    var request = new ProjectController.CreateProjectRequest("Project", "owner@b.de");
    when(service.create(3L, "Project", "owner@b.de")).thenReturn(view);

    // When
    ProjectView result = controller.create(3L, request);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void list_delegatesToService() {
    // Given
    List<ProjectView> views = List.of(project());
    when(service.list(3L)).thenReturn(views);

    // When
    List<ProjectView> result = controller.list(3L);

    // Then
    assertThat(result).isSameAs(views);
  }

  @Test
  void rename_delegatesToService() {
    // Given
    ProjectView view = project();
    when(service.rename(3L, 5L, "New")).thenReturn(view);

    // When
    ProjectView result = controller.rename(3L, 5L, new ProjectController.ProjectRequest("New"));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void delete_delegatesToService() {
    // When
    controller.delete(3L, 5L);

    // Then
    verify(service).delete(3L, 5L);
  }

  @Test
  void transferOwner_delegatesToMembershipService() {
    // When
    controller.transferOwner(3L, 5L, new ProjectController.TransferOwnerRequest(8L));

    // Then
    verify(memberships).transferOwnership(3L, 5L, 8L);
  }

  /**
   * Eigener Endpunkt statt eines zweiten Feldes am PATCH (Issue #1077, Plan #1072 E6): Das
   * Umbenennen und das Schalten der Teilnahme haben gegenläufige Rechteregeln.
   */
  @Test
  void setDashboardParticipation_delegatesToService() {
    // Given
    ProjectView view = project();
    when(service.setDashboardParticipation(3L, 5L, true)).thenReturn(view);

    // When
    ProjectView result =
        controller.setDashboardParticipation(
            3L, 5L, new ProjectController.ParticipationRequest(true));

    // Then
    assertThat(result).isSameAs(view);
  }

  // --- Monatsbudget (Issue #1965, Plan #1962 E4) ----------------------------------------------

  /**
   * Der Betrag kommt als Zeichenkette mit Punkt und erreicht den Service als exakte Dezimalzahl.
   */
  @Test
  void setMonthlyBudget_liestDenBetragAlsZeichenkette_undAntwortet204() throws Exception {
    mvc.perform(
            put(BUDGET_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountUsd\":\"100.00\"}"))
        .andExpect(status().isNoContent());

    verify(service).setMonthlyBudget(3L, 5L, new BigDecimal("100.00"));
  }

  @Test
  void setMonthlyBudget_weistEinenFehlendenBetragAb_bevorDerServiceIhnSieht() throws Exception {
    mvc.perform(put(BUDGET_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  /** E4: Punkt als Dezimaltrenner; das Komma wandelt erst das Eingabefeld um. */
  @Test
  void setMonthlyBudget_weistEinKommaAlsDezimaltrennerAb() throws Exception {
    mvc.perform(
            put(BUDGET_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountUsd\":\"100,00\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  /**
   * Das {@code detail} aus der Meldung setzt der {@code GlobalExceptionHandler}; das belegt {@code
   * common.web.ModulMeldungenSpracheTest}. Hier steht der Status aus {@code @ResponseStatus}.
   */
  @Test
  void setMonthlyBudget_antwortet400_beiUngueltigemBetrag() throws Exception {
    doThrow(new InvalidMonthlyBudgetException())
        .when(service)
        .setMonthlyBudget(3L, 5L, new BigDecimal("0"));

    mvc.perform(
            put(BUDGET_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountUsd\":\"0\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void setMonthlyBudget_reichtDas403Durch() throws Exception {
    doThrow(new ProjectAccessDeniedException())
        .when(service)
        .setMonthlyBudget(anyLong(), anyLong(), any(BigDecimal.class));

    mvc.perform(
            put(BUDGET_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amountUsd\":\"100.00\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void removeMonthlyBudget_entferntDasBudget_undAntwortet204() throws Exception {
    mvc.perform(delete(BUDGET_PATH)).andExpect(status().isNoContent());

    verify(service).removeMonthlyBudget(3L, 5L);
  }

  @Test
  void removeMonthlyBudget_reichtDas404Durch() throws Exception {
    doThrow(new ProjectNotFoundException()).when(service).removeMonthlyBudget(3L, 5L);

    mvc.perform(delete(BUDGET_PATH)).andExpect(status().isNotFound());
  }
}
