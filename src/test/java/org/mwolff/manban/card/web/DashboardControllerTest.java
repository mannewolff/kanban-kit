package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.card.application.AgingWipView;
import org.mwolff.manban.card.application.BoardDashboardKpis;
import org.mwolff.manban.card.application.BoardFlowView;
import org.mwolff.manban.card.application.BoardFlussService;
import org.mwolff.manban.card.application.CardCycleTimeService;
import org.mwolff.manban.card.application.EpicForecastView;
import org.mwolff.manban.card.application.ImplementationTimeView;
import org.mwolff.manban.card.application.VorhabenPrognoseService;
import org.mwolff.manban.card.domain.VorhabenLage.Lage;
import org.mwolff.manban.common.web.KeineRegionszoneException;
import org.mwolff.manban.project.application.ProjectNotFoundException;

/** Unit-Test des Dashboard-Controllers (Service gemockt). */
class DashboardControllerTest {

  private CardCycleTimeService service;
  private BoardFlussService fluss;
  private VorhabenPrognoseService prognose;
  private DashboardController controller;

  @BeforeEach
  void setUp() {
    service = mock(CardCycleTimeService.class);
    fluss = mock(BoardFlussService.class);
    prognose = mock(VorhabenPrognoseService.class);
    controller = new DashboardController(service, fluss, prognose);
  }

  @Test
  void dashboard_delegatesToServiceWithUserAndBoard() {
    BoardDashboardKpis kpis =
        new BoardDashboardKpis(List.of(), List.of(), List.of(), 100L, 4, 200L, 3, List.of());
    when(service.dashboard(3L, 7L)).thenReturn(kpis);

    BoardDashboardKpis result = controller.dashboard(3L, 7L);

    assertThat(result).isSameAs(kpis);
  }

  @Test
  void dashboard_namesTheImplementationFieldsInTheJsonResponse() throws Exception {
    BoardDashboardKpis kpis =
        new BoardDashboardKpis(List.of(), List.of(), List.of(), 100L, 4, 7200L, 3, List.of());
    when(service.dashboard(3L, 7L)).thenReturn(kpis);

    String json = new ObjectMapper().writeValueAsString(controller.dashboard(3L, 7L));

    // Das Frontend liest genau diese Namen; die abgelöste Zykluszeit darf nicht zurückbleiben.
    assertThat(json)
        .contains("\"avgImplementationSeconds\":7200", "\"implementationSampleCount\":3");
    assertThat(json).doesNotContain("CycleTime", "cycleTime");
  }

  @Test
  void dashboard_namesTheWeeklyImplementationSeriesInTheJsonResponse() throws Exception {
    BoardDashboardKpis kpis =
        new BoardDashboardKpis(
            List.of(),
            List.of(),
            List.of(
                new BoardDashboardKpis.WeeklyImplementation(
                    Instant.parse("2026-07-06T00:00:00Z"), 600L, 2)),
            null,
            0,
            null,
            0,
            List.of());
    when(service.dashboard(3L, 7L)).thenReturn(kpis);

    String json =
        new ObjectMapper()
            .findAndRegisterModules()
            .writeValueAsString(controller.dashboard(3L, 7L));

    assertThat(json)
        .contains(
            "\"implementationWeekly\":[{",
            "\"weekStart\":",
            "\"avgImplementationSeconds\":600",
            "\"sampleCount\":2");
  }

  @Test
  void implementationTime_delegatesWithoutBounds() {
    ImplementationTimeView view = new ImplementationTimeView(1200L, 5);
    when(service.implementationTime(3L, 7L, null, null)).thenReturn(view);

    assertThat(controller.implementationTime(3L, 7L, null, null)).isSameAs(view);
  }

  @Test
  void implementationTime_delegatesWithBounds() {
    Instant from = Instant.parse("2026-07-01T00:00:00Z");
    Instant to = Instant.parse("2026-07-08T00:00:00Z");
    ImplementationTimeView view = new ImplementationTimeView(900L, 2);
    when(service.implementationTime(3L, 7L, from, to)).thenReturn(view);

    assertThat(controller.implementationTime(3L, 7L, from, to)).isSameAs(view);
  }

  @Test
  void implementationTime_namesTheFieldsInTheJsonResponse() throws Exception {
    when(service.implementationTime(3L, 7L, null, null))
        .thenReturn(new ImplementationTimeView(900L, 2));

    String json =
        new ObjectMapper().writeValueAsString(controller.implementationTime(3L, 7L, null, null));

    assertThat(json)
        .isEqualTo("{\"avgImplementationSeconds\":900,\"implementationSampleCount\":2}");
  }

  @Test
  void flow_delegatesWithUserBoardAndZone() {
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    BoardFlowView view = new BoardFlowView(List.of(), LocalDate.of(2026, 9, 1));
    when(fluss.flow(3L, 7L, berlin)).thenReturn(view);

    assertThat(controller.flow(3L, 7L, berlin)).isSameAs(view);
  }

  @ParameterizedTest
  @ValueSource(strings = {"+05:30", "Z"})
  void flow_rejectsAnOffsetZone(String zone) {
    assertThatThrownBy(() -> controller.flow(3L, 7L, ZoneId.of(zone)))
        .isInstanceOf(KeineRegionszoneException.class);
    verifyNoInteractions(fluss);
  }

  @Test
  void aging_delegatesWithUserAndBoard() {
    AgingWipView.State leer = new AgingWipView.State(0, null, List.of());
    AgingWipView view = new AgingWipView(leer, leer, leer);
    when(fluss.aging(3L, 7L)).thenReturn(view);

    assertThat(controller.aging(3L, 7L)).isSameAs(view);
  }

  @Test
  void epicForecast_delegatesWithUserBoardAndZone() {
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    EpicForecastView view = new EpicForecastView(0, List.of());
    when(prognose.epicForecast(3L, 7L, berlin)).thenReturn(view);

    assertThat(controller.epicForecast(3L, 7L, berlin)).isSameAs(view);
  }

  @ParameterizedTest
  @ValueSource(strings = {"+05:30", "Z", "UTC+02:00"})
  void epicForecast_rejectsAnOffsetZone(String zone) {
    assertThatThrownBy(() -> controller.epicForecast(3L, 7L, ZoneId.of(zone)))
        .isInstanceOf(KeineRegionszoneException.class);
    verifyNoInteractions(prognose);
  }

  @Test
  void epicForecast_passesNotFoundThrough() {
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    when(prognose.epicForecast(3L, 7L, berlin)).thenThrow(new ProjectNotFoundException());

    assertThatThrownBy(() -> controller.epicForecast(3L, 7L, berlin))
        .isInstanceOf(ProjectNotFoundException.class);
  }

  @Test
  void epicForecast_namesTheFieldsInTheJsonResponse() throws Exception {
    ZoneId berlin = ZoneId.of("Europe/Berlin");
    EpicForecastView view =
        new EpicForecastView(
            12,
            List.of(
                new EpicForecastView.Epic(
                    42L,
                    1782,
                    "Leitstand-Prognose",
                    Lage.FORECAST,
                    14,
                    2,
                    12,
                    LocalDate.of(2026, 10, 21),
                    LocalDate.of(2026, 10, 24),
                    true,
                    List.of(new EpicForecastView.PendingDocument(1819, "[Plan] Prognose")),
                    2),
                new EpicForecastView.Epic(
                    43L,
                    1790,
                    "Leer",
                    Lage.NO_PACKAGES,
                    0,
                    0,
                    0,
                    null,
                    null,
                    false,
                    List.of(),
                    1)));
    when(prognose.epicForecast(3L, 7L, berlin)).thenReturn(view);

    String json =
        new ObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .writeValueAsString(controller.epicForecast(3L, 7L, berlin));

    assertThat(json)
        .isEqualTo(
            "{\"throughputDays\":12,\"epics\":["
                + "{\"id\":42,\"number\":1782,\"title\":\"Leitstand-Prognose\","
                + "\"state\":\"FORECAST\",\"total\":14,\"finished\":2,\"open\":12,"
                + "\"p50\":\"2026-10-21\",\"p85\":\"2026-10-24\",\"earliest\":true,"
                + "\"pendingDocuments\":[{\"number\":1819,\"title\":\"[Plan] Prognose\"}],"
                + "\"otherEpicsSharing\":2},"
                + "{\"id\":43,\"number\":1790,\"title\":\"Leer\",\"state\":\"NO_PACKAGES\","
                + "\"total\":0,\"finished\":0,\"open\":0,\"p50\":null,\"p85\":null,"
                + "\"earliest\":false,\"pendingDocuments\":[],\"otherEpicsSharing\":1}]}");
  }
}
