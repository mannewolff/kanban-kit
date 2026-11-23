package org.mwolff.manban.fremdboard.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.fremdboard.application.FremdBoardStand;
import org.mwolff.manban.fremdboard.application.VerbindungAnsicht;
import org.mwolff.manban.fremdboard.application.VerbindungService;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;
import org.mwolff.manban.fremdboard.domain.StatusLabels;
import org.mwolff.manban.fremdboard.web.FremdBoardController.FremdBoardRequest;
import org.mwolff.manban.fremdboard.web.FremdBoardController.FremdBoardStandView;
import org.mwolff.manban.fremdboard.web.FremdBoardController.FremdBoardView;
import org.mwolff.manban.fremdboard.web.FremdBoardController.Statuslabels;

/**
 * Umsetzung zwischen Anfrage, Antwort und Anwendungsschicht (Issue #2185): eigene Statuslabels
 * kommen unverändert an, ohne Statuslabels gilt die Vorgabe, die Textform der Anfrage verrät den
 * Zugang nicht, und die Antwort trägt Statuslabels nur, wenn die Verbindung welche hat.
 */
class FremdBoardControllerTest {

  private static final StatusLabels EIGENE = new StatusLabels("Offen", "Bereit", "Dran", "Prüfen");

  private VerbindungService service;
  private FremdBoardController controller;

  @BeforeEach
  void aufbau() {
    service = mock(VerbindungService.class);
    controller = new FremdBoardController(service);
  }

  @Test
  void eigeneStatuslabelsKommenUnveraendertAn() {
    FremdBoardRequest request =
        anfrage("glpat-geheim", new Statuslabels("Offen", "Bereit", "Dran", "Prüfen"));

    assertThat(request.eingabe().statuslabels()).isEqualTo(EIGENE);
  }

  @Test
  void ohneStatuslabelsGiltDieVorgabe() {
    assertThat(anfrage("glpat-geheim", null).eingabe().statuslabels())
        .isEqualTo(StatusLabels.STANDARD);
  }

  @Test
  void textformDerAnfrageVerraetDenZugangNicht() {
    FremdBoardRequest request = anfrage("glpat-geheim", null);

    assertThat(request.toString())
        .isEqualTo("FremdBoardRequest[" + request.eingabe() + "]")
        .startsWith("FremdBoardRequest[VerbindungEingabe[art=GITLAB")
        .contains("zugang=***")
        .doesNotContain("glpat-geheim");
  }

  @Test
  void antwortTraegtDieStatuslabelsDerVerbindung() {
    Instant angebunden = Instant.parse("2026-11-01T08:00:00Z");
    Instant abgleich = Instant.parse("2026-11-02T09:30:00Z");
    when(service.lesen(3L, 7L))
        .thenReturn(
            new VerbindungAnsicht(
                true,
                true,
                FremdBoardArt.GITLAB,
                "https://gitlab.com",
                "gruppe/projekt",
                "kit-bot",
                EIGENE,
                angebunden,
                abgleich,
                "Zeitüberschreitung",
                true));

    FremdBoardView view = controller.lies(3L, 7L);

    assertThat(view)
        .isEqualTo(
            new FremdBoardView(
                true,
                true,
                FremdBoardArt.GITLAB,
                "https://gitlab.com",
                "gruppe/projekt",
                "kit-bot",
                new Statuslabels("Offen", "Bereit", "Dran", "Prüfen"),
                angebunden,
                abgleich,
                "Zeitüberschreitung",
                true));
  }

  @Test
  void antwortOhneVerbindungHatKeineStatuslabels() {
    when(service.lesen(3L, 7L))
        .thenReturn(
            new VerbindungAnsicht(
                false, true, null, null, null, null, null, null, null, null, false));

    FremdBoardView view = controller.lies(3L, 7L);

    assertThat(view.statuslabels()).isNull();
    assertThat(view.angebunden()).isFalse();
    assertThat(view.schluesselGesetzt()).isTrue();
    assertThat(view.zugangLesbar()).isFalse();
  }

  @Test
  void standBildetDieVierFelderAb() {
    Instant stand = Instant.parse("2026-11-23T09:58:00Z");
    when(service.stand(3L, 7L))
        .thenReturn(new FremdBoardStand(true, FremdBoardArt.GITLAB, stand, true));

    assertThat(controller.stand(3L, 7L))
        .isEqualTo(new FremdBoardStandView(true, FremdBoardArt.GITLAB, stand, true));
  }

  @Test
  void standOhneVerbindungBleibtLeer() {
    when(service.stand(3L, 7L)).thenReturn(FremdBoardStand.ohne());

    assertThat(controller.stand(3L, 7L))
        .isEqualTo(new FremdBoardStandView(false, null, null, false));
  }

  @Test
  void standTraegtGenauDieVierFelder() {
    assertThat(
            Arrays.stream(FremdBoardStandView.class.getRecordComponents())
                .map(RecordComponent::getName))
        .containsExactly("imFremdsystem", "art", "standAm", "abgleichGescheitert");
  }

  private static FremdBoardRequest anfrage(String zugang, @Nullable Statuslabels statuslabels) {
    return new FremdBoardRequest(
        FremdBoardArt.GITLAB,
        "https://gitlab.com",
        "gruppe/projekt",
        zugang,
        "kit-bot",
        statuslabels);
  }
}
