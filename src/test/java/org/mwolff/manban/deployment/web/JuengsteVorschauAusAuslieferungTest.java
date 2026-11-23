package org.mwolff.manban.deployment.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.comment.domain.Rueckfrage;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung;
import org.mwolff.manban.comment.domain.RueckfragenAuswertung.RueckfrageZustand;
import org.mwolff.manban.comment.domain.Vorschau;
import org.mwolff.manban.deployment.application.DeploymentStore;
import org.mwolff.manban.deployment.application.GemeldeterStand;
import org.mwolff.manban.deployment.domain.Auslieferungsart;

/**
 * Der Adapter des Ports {@code JuengsteVorschau} (Issue #2215, Plan #2206 E10, E11): Er liest die
 * jüngste Vorschau aus den gemeldeten Auslieferungen, und die Auswertung der Rückfragen erkennt
 * daran die überholte Freigabe.
 */
class JuengsteVorschauAusAuslieferungTest {

  private static final long PROJEKT = 3L;
  private static final Instant FRAGE_AM = Instant.parse("2026-11-22T10:00:00Z");
  private static final Instant VORHER = Instant.parse("2026-11-22T09:00:00Z");
  private static final Instant NACHHER = Instant.parse("2026-11-22T11:00:00Z");

  private final DeploymentStore store = mock(DeploymentStore.class);
  private final JuengsteVorschauAusAuslieferung adapter =
      new JuengsteVorschauAusAuslieferung(store);

  private static Rueckfrage freigabe() {
    return Rueckfrage.lies(
            9L,
            FRAGE_AM,
            """
            ## Rückfrage
            Vorschau: https://vorschau.example.org/1
            Commit: abc
            ```json kit-vorlage
            [{"nr": 1, "frage": "Freigeben?", "empfehlung": "Ja"}]
            ```
            """)
        .orElseThrow();
  }

  private void vorschau(String revision, Instant fertig, Instant eingang) {
    when(store.juengster(PROJEKT, Auslieferungsart.VORSCHAU))
        .thenReturn(
            Optional.of(
                new GemeldeterStand(revision, "https://vorschau.example.org/2", fertig, eingang)));
  }

  @Test
  void ohneVorschauIstNichtsGemeldet() {
    when(store.juengster(PROJEKT, Auslieferungsart.VORSCHAU)).thenReturn(Optional.empty());

    assertThat(adapter.juengste(PROJEKT)).isEmpty();
  }

  @Test
  void dieVorschauTraegtIhrenStandUndDenEingangAlsMeldezeit() {
    vorschau("neu", VORHER, NACHHER);

    assertThat(adapter.juengste(PROJEKT)).contains(new Vorschau(NACHHER, "neu"));
  }

  @Test
  void eineNachDerRueckfrageGemeldeteVorschauMitAnderemStandUeberholtDieFreigabe() {
    vorschau("neu", NACHHER, NACHHER);

    assertThat(
            RueckfragenAuswertung.auswerten(freigabe(), List.of(), adapter.juengste(PROJEKT))
                .zustand())
        .isEqualTo(RueckfrageZustand.UEBERHOLT);
  }

  @Test
  void eineVorDerRueckfrageGemeldeteVorschauUeberholtNicht() {
    vorschau("neu", VORHER, VORHER);

    assertThat(
            RueckfragenAuswertung.auswerten(freigabe(), List.of(), adapter.juengste(PROJEKT))
                .zustand())
        .isEqualTo(RueckfrageZustand.OFFEN);
  }

  @Test
  void eineNeueMeldungDesGezeigtenStandsUeberholtNicht() {
    vorschau("abc", NACHHER, NACHHER);

    assertThat(RueckfragenAuswertung.istUeberholt(freigabe(), adapter.juengste(PROJEKT))).isFalse();
  }
}
