package org.mwolff.manban.nightrun.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.accesstoken.application.KanbanPrincipal;
import org.mwolff.manban.nightrun.application.NightRunNowService;
import org.mwolff.manban.nightrun.application.TokenNotBoundForIngestException;
import org.mwolff.manban.nightrun.domain.NightRunMode;
import org.mwolff.manban.nightrun.domain.ReadyBewertung.Grund;
import org.mwolff.manban.nightrun.domain.RunnerLage;
import org.mwolff.manban.nightrun.domain.RunnerLage.AktiverLauf;
import org.mwolff.manban.nightrun.domain.RunnerLage.Gesamt;
import org.mwolff.manban.nightrun.domain.RunnerLage.LaufZustand;
import org.mwolff.manban.nightrun.domain.RunnerStufe;
import org.mwolff.manban.nightrun.domain.Wartegrund;
import org.mwolff.manban.nightrun.web.NightRunNowController.AssessmentEntryRequest;
import org.mwolff.manban.nightrun.web.NightRunNowController.AssessmentRequest;
import org.mwolff.manban.nightrun.web.NightRunNowController.Result;
import org.mwolff.manban.nightrun.web.NightWaitView.CardReasonView;
import org.mwolff.manban.nightrun.web.RunnerNowView.StageView;
import org.springframework.security.authentication.TestingAuthenticationToken;

/**
 * Die Pfade von {@link NightRunNowController}, die die Integrationstests nicht erreichen (Issue
 * #1735).
 *
 * <p>Ohne Anmeldung kommt eine Meldung dort nie an, und die Bean Validation prüft eine fehlende
 * Liste schon vor {@code isJedeKarteEinmal}. Der Controller fängt beides trotzdem selbst ab.
 */
class NightRunNowControllerTest {

  private static final Instant T = Instant.parse("2026-10-13T22:40:00Z");

  private final NightRunNowService service = mock(NightRunNowService.class);

  private final NightRunNowController controller = new NightRunNowController(service);

  private static AssessmentRequest meldung(List<AssessmentEntryRequest> entries) {
    return new AssessmentRequest(T, null, "kit:nightrun", entries);
  }

  @Test
  void meldeBewertung_ohneAnmeldung_weistAb() {
    AssessmentRequest request = meldung(List.of());

    assertThatThrownBy(() -> controller.meldeBewertung(null, request))
        .isInstanceOf(TokenNotBoundForIngestException.class);
    verifyNoInteractions(service);
  }

  @Test
  void meldeBewertung_ohneTokenDetails_weistAb() {
    TestingAuthenticationToken sitzung = new TestingAuthenticationToken(7L, null);
    AssessmentRequest request = meldung(List.of());

    assertThatThrownBy(() -> controller.meldeBewertung(sitzung, request))
        .isInstanceOf(TokenNotBoundForIngestException.class);
    verifyNoInteractions(service);
  }

  @Test
  void meldeBewertung_mitGebundenemToken_meldetGespeichert() {
    TestingAuthenticationToken token = new TestingAuthenticationToken(7L, null);
    token.setDetails(new KanbanPrincipal(7L, 3L, 11L, 13L, "runner"));
    AssessmentRequest request = meldung(List.of());

    assertThat(controller.meldeBewertung(token, request).result()).isEqualTo(Result.STORED);
    verify(service).meldeBewertung(7L, 11L, request.toDomain());
  }

  @Test
  void jedeKarteEinmal_ohneListe_istErfuellt() {
    assertThat(meldung(null).isJedeKarteEinmal()).isTrue();
  }

  @Test
  void jedeKarteEinmal_uebergehtFehlendeEintraege() {
    AssessmentEntryRequest karte = new AssessmentEntryRequest(1695, 1, null);

    assertThat(meldung(Arrays.asList(karte, null)).isJedeKarteEinmal()).isTrue();
    assertThat(meldung(Arrays.asList(karte, null, karte)).isJedeKarteEinmal()).isFalse();
  }

  @Test
  void wartegrund_reichtDieGruendeDerKarteDurch() {
    Grund grund =
        new Grund("PROTECTED_FILE", "PLAN", List.of(1698), List.of("a.md"), "geschuetzte Datei");
    when(service.wartegrund(7L, 4711L))
        .thenReturn(new Wartegrund(List.of(), List.of(grund), null, List.of(), false));

    assertThat(controller.wartegrund(7L, 4711L).cardReasons())
        .containsExactly(
            new CardReasonView(
                "PROTECTED_FILE", "PLAN", List.of(1698), List.of("a.md"), "geschuetzte Datei"));
  }

  @Test
  void jetzt_zeigtBeimVerstummtenLaufDieZuletztBegonneneStufe() {
    RunnerStufe stufe = new RunnerStufe(1665, "UMSETZUNG", T, null, "SESSION", true);
    AktiverLauf lauf =
        new AktiverLauf(
            312L, NightRunMode.CHAIN, T, T, LaufZustand.VERSTUMMT, List.of(stufe), stufe);
    when(service.jetzt(7L, 3L)).thenReturn(new RunnerLage(Gesamt.AKTIV, List.of(lauf), null));

    StageView erwartet = new StageView(1665, "UMSETZUNG", T, null, "SESSION", true);
    assertThat(controller.jetzt(7L, 3L).runs().getFirst().lastStage()).isEqualTo(erwartet);
  }
}
