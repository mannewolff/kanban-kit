package org.mwolff.manban.entscheidungskette.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.AGENT;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.ZUGANG;
import static org.mwolff.manban.entscheidungskette.domain.KettenAufbau.um;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.P;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.einziges;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.glieder;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.paket;
import static org.mwolff.manban.entscheidungskette.domain.PaketAufbau.vonHand;

import org.junit.jupiter.api.Test;

/** Das Glied Issue-Review am Paket (Issue #2116, Plan #2108 E18). */
class IssueReviewTest {

  // --- Issue-Review (E18) ----------------------------------------------------------------------

  @Test
  void issueReview_meldungVorhanden_erfasstMitPruefung() {
    Entscheidungskette kette =
        paket()
            .reviewVorReady(false, null)
            .kommentar(50, P, "## Issue-Review, Runde 1\n\n### Reviewer 1: Prüfer, fable\n", um(3))
            .kommentar(51, P, "## Einarbeitung, Runde 1\n", um(4))
            .rechne();

    Glied glied = einziges(kette, Gliedart.ISSUE_REVIEW);
    assertThat(glied.zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glied.ausgefuehrtVon()).isEqualTo(Akteur.werkzeug(ZUGANG, AGENT));
    assertThat(glied.zeitpunkt()).isEqualTo(um(3));
    assertThat(glied.quelle().kommentar()).isEqualTo(50L);
    assertThat(glied.pruefung().runde()).isEqualTo(1);
    assertThat(glied.pruefung().pruefer()).extracting(Reviewer::modell).containsExactly("fable");
    assertThat(glied.pruefung().einarbeitung().zustand()).isEqualTo(Zustand.ERFASST);
    assertThat(glieder(kette, Gliedart.PRUEFRUNDE)).isEmpty();
  }

  @Test
  void issueReview_vonHandGeaendert_nichtErfasstMitHinweis() {
    Entscheidungskette kette =
        paket().kommentar(vonHand(50, "## Issue-Review, Runde 2", um(3))).rechne();

    Glied glied = einziges(kette, Gliedart.ISSUE_REVIEW);
    assertThat(glied.zustand()).isEqualTo(Zustand.NICHT_ERFASST);
    assertThat(glied.hinweise()).containsExactly(Hinweis.VON_HAND_GEAENDERT);
    assertThat(glied.pruefung().runde()).isEqualTo(2);
  }

  @Test
  void issueReview_gemeldetFalse_nichtVorgesehen() {
    Entscheidungskette kette =
        paket().reviewVorReady(false, null).zugVonMensch(P, "READY", 77, um(5)).rechne();

    assertThat(einziges(kette, Gliedart.ISSUE_REVIEW).zustand())
        .isEqualTo(Zustand.NICHT_VORGESEHEN);
  }

  @Test
  void issueReview_gemeldetTrue_nichtErfasst() {
    Entscheidungskette kette =
        paket().reviewVorReady(true, null).zugVonMensch(P, "READY", 77, um(5)).rechne();

    assertThat(einziges(kette, Gliedart.ISSUE_REVIEW).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void issueReview_nieGemeldet_nichtErfasst() {
    Entscheidungskette kette = paket().zugVonMensch(P, "READY", 77, um(5)).rechne();

    assertThat(einziges(kette, Gliedart.ISSUE_REVIEW).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void issueReview_falseNachDemZugGeaendert_nichtErfasst() {
    Entscheidungskette kette =
        paket().reviewVorReady(false, um(6)).zugVonMensch(P, "READY", 77, um(5)).rechne();

    assertThat(einziges(kette, Gliedart.ISSUE_REVIEW).zustand()).isEqualTo(Zustand.NICHT_ERFASST);
  }

  @Test
  void issueReview_falseVorDemZugGeaendert_nichtVorgesehen() {
    Entscheidungskette kette =
        paket().reviewVorReady(false, um(5)).zugVonMensch(P, "READY", 77, um(5)).rechne();

    assertThat(einziges(kette, Gliedart.ISSUE_REVIEW).zustand())
        .isEqualTo(Zustand.NICHT_VORGESEHEN);
  }

  @Test
  void issueReview_falseGeaendertOhneZugNachReady_nichtVorgesehen() {
    Entscheidungskette kette = paket().reviewVorReady(false, um(6)).rechne();

    assertThat(einziges(kette, Gliedart.ISSUE_REVIEW).zustand())
        .isEqualTo(Zustand.NICHT_VORGESEHEN);
  }
}
