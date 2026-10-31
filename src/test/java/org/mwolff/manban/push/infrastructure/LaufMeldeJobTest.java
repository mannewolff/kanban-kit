package org.mwolff.manban.push.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.nightrun.application.MeldbareLaeufe;
import org.mwolff.manban.nightrun.application.MeldbareLaeufe.MeldbarerLauf;
import org.mwolff.manban.nightrun.domain.Meldegrund;
import org.mwolff.manban.nightrun.domain.Meldegrund.Grund;
import org.mwolff.manban.push.application.LaufMeldeService;
import org.mwolff.manban.push.application.PushProperties;

/**
 * Der Meldejob (Issue #1998, Plan #1989 E12, E16): Er läuft nur mit eingeschaltetem Push und meldet
 * jeden Lauf für sich, damit ein Fehler an einem Lauf die anderen nicht aufhält.
 */
class LaufMeldeJobTest {

  private static final PushProperties AN = new PushProperties("pub", "priv", "mailto:a@b.de", null);

  private final MeldbareLaeufe meldbare = mock(MeldbareLaeufe.class);
  private final LaufMeldeService service = mock(LaufMeldeService.class);

  private static MeldbarerLauf lauf(long id) {
    return new MeldbarerLauf(id, 7L, "Alpha", null, null, new Meldegrund(Grund.VERSTUMMT, null));
  }

  @Test
  void ohnePushFragtDerJobNichtsAb() {
    new LaufMeldeJob(meldbare, service, new PushProperties(null, null, null, null)).run();

    verifyNoInteractions(meldbare, service);
  }

  @Test
  void meldetJedenLaufUndZaehltDieNachrichten() {
    when(meldbare.laeufe()).thenReturn(List.of(lauf(1L), lauf(2L)));
    when(service.melde(lauf(1L))).thenReturn(2);
    when(service.melde(lauf(2L))).thenReturn(0);

    assertThat(new LaufMeldeJob(meldbare, service, AN).melden()).isEqualTo(2);

    verify(service).melde(lauf(1L));
    verify(service).melde(lauf(2L));
  }

  @Test
  void derTaktMeldetDieLaeufe() {
    when(meldbare.laeufe()).thenReturn(List.of(lauf(1L)));
    when(service.melde(lauf(1L))).thenReturn(1);

    new LaufMeldeJob(meldbare, service, AN).run();

    verify(service).melde(lauf(1L));
  }

  @Test
  void einFehlerAnEinemLaufHaeltDieAnderenNichtAuf() {
    when(meldbare.laeufe()).thenReturn(List.of(lauf(1L), lauf(2L)));
    when(service.melde(lauf(1L))).thenThrow(new IllegalStateException("Datenbank weg"));
    when(service.melde(lauf(2L))).thenReturn(1);

    assertThat(new LaufMeldeJob(meldbare, service, AN).melden()).isEqualTo(1);

    verify(service).melde(lauf(2L));
  }
}
