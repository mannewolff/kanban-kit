package org.mwolff.manban.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.i18n.SprachFixture;
import org.mwolff.manban.common.i18n.Sprache;
import org.mwolff.manban.common.i18n.Texte;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Die Ablehnung einer Offset-Zone ist eine übersetzbare Meldung mit Status 400 (Issue #1642, Plan
 * #1638 A5): Bei Session-Anfrage und Sprache Englisch erscheint ihr {@code detail} englisch.
 */
class RegionszoneTest {

  @Test
  void eineRegionszoneKommtDurch() {
    ZoneId berlin = ZoneId.of("Europe/Berlin");

    assertThat(Regionszone.of(berlin)).isEqualTo(berlin);
  }

  @Test
  void eineOffsetZoneIstEineUebersetzbareMeldungMitStatus400() {
    Texte texte = SprachFixture.mit(Sprache.EN);

    assertThatThrownBy(() -> Regionszone.of(ZoneId.of("+05:30")))
        .isInstanceOf(UebersetzbareMeldung.class)
        .hasMessage("zone muss eine Regionszone sein, etwa Europe/Berlin")
        .satisfies(
            ex -> {
              assertThat(texte.text(Sprache.EN, (UebersetzbareMeldung) ex))
                  .isEqualTo("zone must be a region zone, such as Europe/Berlin");
              assertThat(ex.getClass().getAnnotation(ResponseStatus.class).value())
                  .isEqualTo(HttpStatus.BAD_REQUEST);
            });
  }
}
