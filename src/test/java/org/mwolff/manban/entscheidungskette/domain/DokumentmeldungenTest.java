package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.domain.KettenEingabe.Kommentar;

/** Die Fallunterscheidung der gelesenen Meldungen nach ihrer Art (Issue #2154). */
class DokumentmeldungenTest {

  private static final Instant ZEITPUNKT = Instant.parse("2026-11-15T10:00:00Z");

  private static Kommentar kommentar(String text) {
    return new Kommentar(7, 3, text, ZEITPUNKT, "TOKEN", "zugang", "agent", null, null);
  }

  @Test
  void lies_pruefrunde_gelesenMitRundeUndPruefrunde() {
    Kommentar k = kommentar("## Plan-Review, Runde 2\n");

    Optional<Dokumentmeldungen.Gelesen> gelesen = Dokumentmeldungen.lies(k);

    assertThat(gelesen).isPresent();
    assertThat(gelesen.get().art()).isEqualTo(Meldungsart.PLAN_REVIEW);
    assertThat(gelesen.get().runde()).isEqualTo(2);
    assertThat(gelesen.get().pruefrunde()).isNotNull();
    assertThat(gelesen.get().vonHand()).isFalse();
  }

  @Test
  void lies_einarbeitung_gelesenOhnePruefrunde() {
    Kommentar k = kommentar("## Einarbeitung, Runde 3\n");

    Optional<Dokumentmeldungen.Gelesen> gelesen = Dokumentmeldungen.lies(k);

    assertThat(gelesen).isPresent();
    assertThat(gelesen.get().art()).isEqualTo(Meldungsart.EINARBEITUNG);
    assertThat(gelesen.get().runde()).isEqualTo(3);
    assertThat(gelesen.get().pruefrunde()).isNull();
    assertThat(gelesen.get().kommentar()).isSameAs(k);
  }

  @Test
  void lies_andereMeldungDesKits_leer() {
    Kommentar k = kommentar("## Code-Review (Schritt 6)\n");

    assertThat(KitMeldungLeser.lies("TOKEN", k.text())).containsInstanceOf(CodeReview.class);
    assertThat(Dokumentmeldungen.lies(k)).isEmpty();
  }
}
