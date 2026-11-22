package org.mwolff.manban.anbieter.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.anbieter.application.AnbieterLageService;
import org.mwolff.manban.anbieter.application.AnbieterLageService.AnbieterLage;
import org.mwolff.manban.anbieter.application.AnbieterLageService.BoardLage;
import org.mwolff.manban.anbieter.application.WartetAufDichService;
import org.mwolff.manban.anbieter.application.WartetAufDichService.Grund;
import org.mwolff.manban.anbieter.application.WartetAufDichService.WartendeKarte;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Die Endpunkte des Moduls anbieter (Issue #2216, Plan #2206 E12, E14), Dienste gemockt. */
class AnbieterControllerTest {

  private static final long USER = 7L;
  private static final long PROJEKT = 3L;
  private static final Instant GELESEN = Instant.parse("2026-11-22T10:00:00Z");

  private WartetAufDichService wartetAufDich;
  private AnbieterLageService lage;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    wartetAufDich = mock(WartetAufDichService.class);
    lage = mock(AnbieterLageService.class);
    MappingJackson2HttpMessageConverter jackson =
        new MappingJackson2HttpMessageConverter(
            Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build());
    mvc =
        MockMvcBuilders.standaloneSetup(new AnbieterController(wartetAufDich, lage))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setMessageConverters(jackson)
            .build();
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(USER, null, List.of()));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void wartetAufDichNenntKarteBoardNummerTitelUndGrund() throws Exception {
    // Given
    when(wartetAufDich.liste(USER, PROJEKT))
        .thenReturn(List.of(new WartendeKarte(812L, 4L, 1404, "Export", Grund.FREIGABE)));

    // When / Then
    mvc.perform(get("/api/projects/" + PROJEKT + "/wartet-auf-dich"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].cardId").value(812))
        .andExpect(jsonPath("$[0].boardId").value(4))
        .andExpect(jsonPath("$[0].number").value(1404))
        .andExpect(jsonPath("$[0].title").value("Export"))
        .andExpect(jsonPath("$[0].grund").value("FREIGABE"));
  }

  @Test
  void lageNenntHinweisUndBoards() throws Exception {
    // Given
    when(lage.lage(USER, PROJEKT))
        .thenReturn(new AnbieterLage(true, List.of(new BoardLage(4L, true, GELESEN))));

    // When / Then
    mvc.perform(get("/api/projects/" + PROJEKT + "/anbieter/lage"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nichtErreichbar").value(true))
        .andExpect(jsonPath("$.boards[0].boardId").value(4))
        .andExpect(jsonPath("$.boards[0].nichtErreichbar").value(true))
        .andExpect(jsonPath("$.boards[0].zuletztGelesen").value("2026-11-22T10:00:00Z"));
  }
}
