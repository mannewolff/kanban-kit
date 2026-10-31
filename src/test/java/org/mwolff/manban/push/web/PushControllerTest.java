package org.mwolff.manban.push.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.SitzungsEnde;
import org.mwolff.manban.auth.application.VorschauKontext;
import org.mwolff.manban.push.application.PushAboService;
import org.mwolff.manban.push.application.PushNichtEingerichtetException;
import org.mwolff.manban.push.application.PushOhneSitzungException;
import org.mwolff.manban.push.application.PushProperties;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * Die Zweige des Controllers ohne HTTP (Issue #1997): Push abgeschaltet und eine Anmeldung ohne
 * Sitzungsende. Über HTTP prüft sie {@code PushAboIT}.
 */
class PushControllerTest {

  private static final String ENDPUNKT = "https://push.example/geraet-1";
  private static final String P256DH = "B".repeat(87);
  private static final String AUTH = "A".repeat(22);

  private final PushAboService service = mock(PushAboService.class);

  private PushController controller(PushProperties properties) {
    return new PushController(service, properties);
  }

  private static PushProperties aktiv() {
    return new PushProperties("oeffentlich", "privat", "mailto:ops@example.org", null);
  }

  @Test
  void schluessel_aktiv_liefertOeffentlichenSchluessel() {
    assertThat(controller(aktiv()).schluessel())
        .isEqualTo(new PushController.SchluesselView("oeffentlich"));
  }

  /** Ohne Schlüssel sagt der Server, dass Push nicht eingerichtet ist (E16). */
  @Test
  void schluessel_abgeschaltet_wirdAbgewiesen() {
    PushController controller = controller(new PushProperties(null, null, null, null));

    assertThatThrownBy(controller::schluessel).isInstanceOf(PushNichtEingerichtetException.class);
  }

  @Test
  void abonniere_mitSitzungsende_reichtEsWeiter() {
    // Given
    SitzungsEnde ende = new SitzungsEnde(Instant.parse("2026-11-28T10:00:00Z"));
    var anmeldung = new UsernamePasswordAuthenticationToken(42L, null, List.of());
    anmeldung.setDetails(ende);

    // When
    controller(aktiv())
        .abonniere(42L, anmeldung, new PushController.AboRequest(ENDPUNKT, P256DH, AUTH));

    // Then
    verify(service).abonniere(42L, ENDPUNKT, P256DH, AUTH, ende);
  }

  /**
   * Ohne Sitzungsende kein Abo — etwa mit dem Kontext der Vorschau als Details, den sonst schon die
   * Schreibsperre abweist.
   */
  @Test
  void abonniere_ohneSitzungsende_wirdAbgewiesen() {
    // Given
    var anmeldung = new UsernamePasswordAuthenticationToken(42L, null, List.of());
    anmeldung.setDetails(new VorschauKontext("k", 1L, 42L, Instant.parse("2026-10-29T11:00:00Z")));
    PushController controller = controller(aktiv());
    var request = new PushController.AboRequest(ENDPUNKT, P256DH, AUTH);

    // When / Then
    assertThatThrownBy(() -> controller.abonniere(42L, anmeldung, request))
        .isInstanceOf(PushOhneSitzungException.class);
    verifyNoInteractions(service);
  }
}
