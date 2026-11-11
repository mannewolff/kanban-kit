package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.PAKET;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.TOKEN;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.BERICHT;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.P;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.vonHand;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;

/** Die Meldungen des Kits am Paket (Issue #2116, #2134). */
class PaketmeldungenTest {

  @Test
  void von_meldungMitLeerzeilenVorDemKopf_wirdGelesen() {
    Kommentar bericht =
        new Kommentar(60, P, "\n  \n" + BERICHT, um(9), TOKEN, null, null, null, null);

    List<Paketmeldungen.Gelesen> gelesen =
        new Paketmeldungen(List.of(bericht)).von(PAKET, Meldungsart.ABSCHLUSSBERICHT);

    assertThat(gelesen).hasSize(1);
    assertThat(gelesen.getFirst().kommentar()).isEqualTo(bericht);
    assertThat(gelesen.getFirst().meldung()).isNotNull();
  }

  @Test
  void von_vonHandGeaendertOhneKitHerkunft_zaehltNicht() {
    Kommentar kit = vonHand(61, "## Abschlussbericht Issue #30", um(9));
    Kommentar fremd =
        new Kommentar(
            62,
            P,
            kit.text(),
            um(10),
            "SESSION",
            null,
            null,
            kit.vonHandGeaendertAm(),
            kit.vonHandKopfzeile());

    List<Paketmeldungen.Gelesen> gelesen =
        new Paketmeldungen(List.of(kit, fremd)).von(PAKET, Meldungsart.ABSCHLUSSBERICHT);

    assertThat(gelesen).extracting(Paketmeldungen.Gelesen::kommentar).containsExactly(kit);
    assertThat(gelesen.getFirst().meldung()).isNull();
  }
}
