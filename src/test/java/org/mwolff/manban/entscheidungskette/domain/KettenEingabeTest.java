package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Aktivitaet;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Bezug;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Bezugsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Karte;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.LabelEreignis;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.ReviewVorReady;

/** Der Konstruktor der Eingabe eines Projekts mit eigenem Board (Issue #2192). */
class KettenEingabeTest {

  @Test
  void eingabeOhneAngabeZumBoardGiltAlsEigenesBoardUndUebernimmtAlleTeile() {
    Karte karte = KettenAufbau.PAKET;
    List<Bezug> vorfahren = List.of(new Bezug(Bezugsart.VORHANDEN, 20, KettenAufbau.PLAN));
    List<Bezug> nachfahren = List.of(new Bezug(Bezugsart.PAPIERKORB, 40, null));
    List<Karte> ideen = List.of(KettenAufbau.FACHPLAN);
    List<Aktivitaet> aktivitaeten =
        List.of(
            new Aktivitaet(
                3, 9L, "MOVE", KettenAufbau.T0, "TOKEN", "kit-nacht", "agent", "READY", null));
    List<Kommentar> kommentare =
        List.of(
            new Kommentar(
                5, 3, "## Laufstand", KettenAufbau.T0, "TOKEN", "kit-nacht", "agent", null, null));
    List<LabelEreignis> labels =
        List.of(new LabelEreignis(3, "kit:night", KettenAufbau.T0, 9L, "SESSION", null));
    ReviewVorReady review = new ReviewVorReady(true, Instant.parse("2026-11-01T00:00:00Z"));

    KettenEingabe eingabe =
        new KettenEingabe(
            karte, vorfahren, nachfahren, ideen, aktivitaeten, kommentare, labels, review);

    assertThat(eingabe)
        .isEqualTo(
            new KettenEingabe(
                karte,
                vorfahren,
                nachfahren,
                ideen,
                aktivitaeten,
                kommentare,
                labels,
                review,
                false));
    assertThat(eingabe.fremdesBoard()).isFalse();
  }

  @Test
  void eingabeOhneMeldungZurPruefungVorReadyBehaeltNull() {
    KettenEingabe eingabe =
        new KettenEingabe(
            KettenAufbau.PAKET,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            null);

    assertThat(eingabe.reviewVorReady()).isNull();
    assertThat(eingabe.fremdesBoard()).isFalse();
  }
}
