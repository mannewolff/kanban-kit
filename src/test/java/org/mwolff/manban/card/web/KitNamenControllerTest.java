package org.mwolff.manban.card.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.card.web.KitNamenController.ArtView;
import org.mwolff.manban.card.web.KitNamenController.KitNamenView;
import org.mwolff.manban.card.web.KitNamenController.SacheView;
import org.mwolff.manban.common.KitNamen;
import org.mwolff.manban.common.KitNamen.Art;
import org.mwolff.manban.common.KitNamen.Sache;

/** Unit-Tests des Lese-Endpunkts der Kit-Namen (Issue #1588). */
class KitNamenControllerTest {

  private final KitNamenView antwort = new KitNamenController().kitNamen();

  @Test
  void sachen_stehenInRegisterreihenfolgeMitAllenNamen() {
    // Then
    assertThat(antwort.sachen())
        .extracting(SacheView::sache)
        .containsExactlyElementsOf(Arrays.stream(Sache.values()).map(Enum::name).toList());
    assertThat(antwort.sachen())
        .allSatisfy(
            s ->
                assertThat(s.namen()).isEqualTo(KitNamen.STANDARD.namen(Sache.valueOf(s.sache()))));
  }

  @Test
  void klaeren_traegtBeideNamen_night_nurEinen() {
    // Then
    assertThat(namenDerSache("KLAEREN")).containsExactly("kit:klaeren", "kit:clarify");
    assertThat(namenDerSache("NIGHT")).containsExactly("kit:night");
  }

  @Test
  void schutz_stehtJeSacheGleichDerRegelDerSache() {
    // Then
    assertThat(antwort.sachen())
        .allSatisfy(
            s -> assertThat(s.schutz()).isEqualTo(Sache.valueOf(s.sache()).schutz().name()));
  }

  @Test
  void schutz_klaerenNimmtNurDerMenschAb_nightSetztNurDerMensch_laufLaeuftOhneSchutz() {
    // Then
    assertThat(schutzDerSache("KLAEREN")).isEqualTo("NUR_MENSCH_NIMMT_AB");
    assertThat(schutzDerSache("NIGHT")).isEqualTo("NUR_MENSCH_SETZT");
    assertThat(schutzDerSache("LAUF_LAEUFT")).isEqualTo("KEINER");
  }

  @Test
  void geklaert_traegtBeideNamen() {
    // Then
    assertThat(namenDerSache("GEKLAERT")).containsExactly("kit:geklaert", "kit:clarified");
  }

  @Test
  void titelPraefixe_stehenJeArtInRegisterreihenfolge() {
    // Then
    assertThat(antwort.titelPraefixe())
        .extracting(ArtView::art)
        .containsExactly("FACHLICH", "IDEE", "MENSCH", "PLAN", "TASK");
    assertThat(antwort.titelPraefixe().getFirst().namen())
        .containsExactly("[Fachlich]", "[Requirement]");
    assertThat(antwort.titelPraefixe())
        .allSatisfy(
            a ->
                assertThat(a.namen())
                    .isEqualTo(KitNamen.titelPraefixe().get(Art.valueOf(a.art()))));
  }

  @Test
  void namensraum_enthaeltAllePraefixeInFesterReihenfolge() {
    // Then
    assertThat(antwort.namensraum())
        .containsExactly("kit:", "lauf:", "ziel:", "review:", "planreview:", "run:", "goal:");
  }

  private List<String> namenDerSache(String sache) {
    return sacheView(sache).namen();
  }

  private String schutzDerSache(String sache) {
    return sacheView(sache).schutz();
  }

  private SacheView sacheView(String sache) {
    return antwort.sachen().stream().filter(s -> s.sache().equals(sache)).findFirst().orElseThrow();
  }
}
