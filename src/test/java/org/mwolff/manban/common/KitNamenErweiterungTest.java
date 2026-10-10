package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.common.KitNamen.Eintrag;
import org.mwolff.manban.common.KitNamen.Sache;
import org.mwolff.manban.common.KitNamen.Schutz;

/**
 * Ein neuer Name des Kits für eine bekannte Sache genügt als Eintrag im Register (Issue #1584,
 * fachliches AK 7 aus Issue #1573, Plan #1582 E13).
 *
 * <p>Dieses Paket belegt den Fall auf Registerebene; die Pakete für Standardsatz, Schutzregel und
 * Laufstand ergänzen je einen Fall für ihren Nutzer.
 */
class KitNamenErweiterungTest {

  private static final String ZUSATZNAME = "kit:klaeren-neu";

  private static KitNamen registerMitZusatznamen() {
    List<Eintrag> eintraege = new ArrayList<>(KitNamen.STANDARD.eintraege());
    eintraege.add(new Eintrag(Sache.KLAEREN, ZUSATZNAME));
    return KitNamen.aus(eintraege);
  }

  @Test
  void zusatzname_liefertDieselbeSacheUndDenselbenSchutz() {
    // Given
    KitNamen register = registerMitZusatznamen();

    // When / Then
    assertThat(register.sache(ZUSATZNAME)).contains(Sache.KLAEREN);
    assertThat(register.sache(ZUSATZNAME)).isEqualTo(register.sache("kit:klaeren"));
    assertThat(register.sache(ZUSATZNAME).orElseThrow().schutz())
        .isEqualTo(Schutz.NUR_MENSCH_NIMMT_AB);
  }

  @Test
  void zusatzname_stehtInDenNamenDerSacheHinterDenBisherigen() {
    assertThat(registerMitZusatznamen().namen(Sache.KLAEREN))
        .containsExactly("kit:klaeren", "kit:clarify", ZUSATZNAME);
  }

  @Test
  void zusatzname_stehtInAllenNamenAmEnde() {
    List<String> erwartet = new ArrayList<>(KitNamen.STANDARD.alleNamen());
    erwartet.add(ZUSATZNAME);

    assertThat(registerMitZusatznamen().alleNamen()).containsExactlyElementsOf(erwartet);
  }

  @Test
  void zusatzname_laesstDasStandardRegisterUnveraendert() {
    // Given
    registerMitZusatznamen();

    // When / Then
    assertThat(KitNamen.STANDARD.sache(ZUSATZNAME)).isEmpty();
    assertThat(KitNamen.STANDARD.namen(Sache.KLAEREN))
        .containsExactly("kit:klaeren", "kit:clarify");
  }
}
