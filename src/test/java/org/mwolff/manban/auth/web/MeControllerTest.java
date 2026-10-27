package org.mwolff.manban.auth.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.MeService;
import org.mwolff.manban.auth.application.MeService.MeView;
import org.mwolff.manban.auth.application.MeService.VorschauView;
import org.mwolff.manban.auth.domain.ColorScheme;
import org.mwolff.manban.auth.domain.PlatformRole;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit-Tests des Selbstauskunft-Controllers (Service gemockt). Die Farbwahl läuft über einen
 * standalone MockMvc, damit Mapping, {@code @AuthenticationPrincipal}, das Lesen des Enums und das
 * {@code @Valid} am Request-Body wirklich greifen. Anmeldung (401), Problem-Detail und die Vorgabe
 * eines frischen Kontos belegt {@code MeColorSchemeIT} gegen die laufende Anwendung.
 */
class MeControllerTest {

  private static final long USER = 3L;
  private static final String COLOR_SCHEME = "/api/me/color-scheme";

  private MeService service;
  private MeController controller;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    service = mock(MeService.class);
    controller = new MeController(service);
    mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(
                    Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build()))
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(USER, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void me_delegatesToService() {
    // Given
    var view = view("Alice", ColorScheme.SYSTEM);
    when(service.load(3L)).thenReturn(view);

    // When
    MeView result = controller.me(3L);

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void updateProfile_delegatesDisplayNameToService() {
    // Given
    var view = view("Neu", ColorScheme.SYSTEM);
    when(service.updateDisplayName(3L, "Neu")).thenReturn(view);

    // When
    MeView result = controller.updateProfile(3L, new MeController.UpdateProfileRequest("Neu"));

    // Then
    assertThat(result).isSameAs(view);
  }

  @Test
  void updateColorScheme_savesDarkAndReturnsUpdatedSelf() throws Exception {
    when(service.updateColorScheme(USER, ColorScheme.DARK))
        .thenReturn(view("Alice", ColorScheme.DARK));

    mvc.perform(
            put(COLOR_SCHEME)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"colorScheme\":\"DARK\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.colorScheme").value("DARK"));

    verify(service).updateColorScheme(USER, ColorScheme.DARK);
  }

  @Test
  void updateColorScheme_rejectsUnknownValue_beforeReachingService() throws Exception {
    mvc.perform(
            put(COLOR_SCHEME)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"colorScheme\":\"PURPLE\"}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  /** Pinnt {@code @Valid} und {@code @NotNull}: Ohne sie erreichte {@code null} den Service. */
  @Test
  void updateColorScheme_rejectsMissingValue_beforeReachingService() throws Exception {
    mvc.perform(put(COLOR_SCHEME).contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(service);
  }

  private static MeView view(String displayName, ColorScheme colorScheme) {
    return new MeView(USER, "a@b.de", displayName, PlatformRole.USER, List.of(), colorScheme, null);
  }

  @Test
  void me_outsideVorschau_deliversVorschauAsNull() throws Exception {
    when(service.load(USER)).thenReturn(view("Alice", ColorScheme.SYSTEM));

    mvc.perform(get("/api/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(USER))
        .andExpect(jsonPath("$.vorschau").isEmpty());
  }

  /** Issue #1921, Plan #1914 E14: Kennung, Name von XY, Name des Admins und Ablauf. */
  @Test
  void me_inVorschau_deliversVorschauField() throws Exception {
    Instant ablauf = Instant.parse("2026-10-27T10:30:00Z");
    when(service.load(USER))
        .thenReturn(
            new MeView(
                USER,
                "xy@b.de",
                "Xaver",
                PlatformRole.USER,
                List.of(),
                ColorScheme.DARK,
                new VorschauView("kennung-a", "Xaver", "Ada Admin", ablauf)));

    mvc.perform(get("/api/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.vorschau.kennung").value("kennung-a"))
        .andExpect(jsonPath("$.vorschau.zielName").value("Xaver"))
        .andExpect(jsonPath("$.vorschau.adminName").value("Ada Admin"))
        .andExpect(jsonPath("$.vorschau.laeuftAbAm").value(ablauf.toString()));
  }
}
