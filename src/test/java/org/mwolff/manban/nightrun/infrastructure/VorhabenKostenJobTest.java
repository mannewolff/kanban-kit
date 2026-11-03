package org.mwolff.manban.nightrun.infrastructure;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mwolff.manban.nightrun.application.VorhabenKostenService;

/**
 * Der Job der Kosten je Vorhaben (Issue #2004): je Projekt ein eigener Aufruf zum Zeitpunkt der
 * Uhr, ein Fehler an einem Projekt hält die übrigen nicht auf.
 */
class VorhabenKostenJobTest {

  private static final Instant FIXED = Instant.parse("2026-11-02T06:00:00Z");

  private final VorhabenKostenService service = mock(VorhabenKostenService.class);
  private final VorhabenKostenJob job =
      new VorhabenKostenJob(service, Clock.fixed(FIXED, ZoneOffset.UTC));

  @Test
  void haeltJedesProjektZumZeitpunktDerUhrFest() {
    when(service.projekteMitVorhaben()).thenReturn(List.of(3L, 7L));

    job.run();

    InOrder order = inOrder(service);
    order.verify(service).projekteMitVorhaben();
    order.verify(service).festhalten(3L, FIXED);
    order.verify(service).festhalten(7L, FIXED);
    verifyNoMoreInteractions(service);
  }

  @Test
  void einFehlerAnEinemProjektHaeltDieUebrigenNichtAuf() {
    when(service.projekteMitVorhaben()).thenReturn(List.of(3L, 7L));
    doThrow(new IllegalStateException("kaputt")).when(service).festhalten(3L, FIXED);

    job.run();

    verify(service).festhalten(7L, FIXED);
  }
}
