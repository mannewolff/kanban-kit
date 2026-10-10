package org.mwolff.manban.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.auth.application.PlatformAdminChecker;
import org.mwolff.manban.board.application.BoardService;
import org.mwolff.manban.card.application.FreigabeLabelException;
import org.mwolff.manban.card.application.FreigabeLabels;
import org.mwolff.manban.card.application.FreigabeLabels.Richtung;
import org.mwolff.manban.card.application.LabelRepository;
import org.mwolff.manban.card.application.StandardLabelService;
import org.mwolff.manban.card.application.StandardLabelService.StandardLabel;
import org.mwolff.manban.card.domain.CardActivityOrigin;
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

  @Test
  void standardLabelService_fuehrtDenZusatznamenMitGruppeUndFarbeDerSache() {
    // Given
    PlatformAdminChecker admins = mock(PlatformAdminChecker.class);
    when(admins.isPlatformAdmin(1L)).thenReturn(true);
    StandardLabelService service =
        new StandardLabelService(
            mock(LabelRepository.class),
            mock(BoardService.class),
            admins,
            registerMitZusatznamen());

    // When
    List<StandardLabel> satz = service.standardsatz(1L);

    // Then
    List<String> namen = satz.stream().map(StandardLabel::name).toList();
    assertThat(namen.subList(namen.indexOf("kit:klaeren"), namen.indexOf("kit:klaeren") + 3))
        .containsExactly("kit:klaeren", "kit:clarify", ZUSATZNAME);
    assertThat(satz).contains(new StandardLabel(ZUSATZNAME, "Kit", "#6a1b9a"));
  }

  @Test
  void freigabeLabels_sperrtDasAbnehmenDesZusatznamensPerToken() {
    // Given
    KitNamen register = registerMitZusatznamen();

    // When / Then
    assertThatThrownBy(
            () ->
                FreigabeLabels.pruefe(
                    register, CardActivityOrigin.TOKEN, ZUSATZNAME, Richtung.ABNEHMEN))
        .isInstanceOf(FreigabeLabelException.class)
        .hasMessage("Label " + ZUSATZNAME + " nimmt nur ein Mensch im Board ab");
    assertThatCode(
            () ->
                FreigabeLabels.pruefe(
                    register, CardActivityOrigin.TOKEN, ZUSATZNAME, Richtung.SETZEN))
        .doesNotThrowAnyException();
  }
}
