package org.mwolff.manban.card.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.BOARD;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.FREMD;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.JETZT;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.PROJEKT;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.SPALTE_EIGEN;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.SPALTE_READY;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.imProjekt;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.inSpalte;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.mitId;
import static org.mwolff.manban.card.application.KartenKetteLeserAufbau.neu;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mwolff.manban.card.application.KartenKetteLeser.KartenKette;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenAktivitaet;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenKarte;
import org.mwolff.manban.card.application.KartenKetteLeser.KettenLabelEreignis;
import org.mwolff.manban.card.domain.Card;
import org.mwolff.manban.card.domain.CardActivity;
import org.mwolff.manban.card.domain.CardActivityOrigin;
import org.mwolff.manban.card.domain.CardActivityType;
import org.mwolff.manban.card.domain.CardStatus;
import org.mwolff.manban.card.domain.CardType;
import org.mwolff.manban.card.domain.LabelEreignisMitPerson;

/**
 * Die Lese-Fassade (Issue #2114, Plan #2108 E1, E6, E10) gegen einen Bestand im Speicher: Einstieg,
 * Sicht einer Karte, Ideen, Aktivitäten und Label-Ereignisse. Vorfahren und Nachfahren prüft {@link
 * KartenKetteLeserVerwandteTest}, die Abfragen {@code KartenKetteLeserIT}.
 */
class KartenKetteLeserTest {

  private final KartenKetteLeserAufbau bau = new KartenKetteLeserAufbau();

  // --- Einstieg -------------------------------------------------------------------------------

  @Test
  void eineUnbekannteKarteGibtEsNicht() {
    assertThatThrownBy(() -> bau.leser.lade(99L)).isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void eineKarteImPapierkorbLaesstSichNichtOeffnen() {
    long id = bau.lege(neu(1, "A", null, null), true, null);

    assertThatThrownBy(() -> bau.leser.lade(id)).isInstanceOf(CardNotFoundException.class);
  }

  @Test
  void ohneVerwandteSindDieListenLeer() {
    long id = bau.lege(neu(1, "A", null, null));

    KartenKette kette = bau.leser.lade(id);

    assertThat(kette.karte().id()).isEqualTo(id);
    assertThat(kette.vorfahren()).isEmpty();
    assertThat(kette.nachfahren()).isEmpty();
    assertThat(kette.ideen()).isEmpty();
  }

  // --- Sicht einer Karte ----------------------------------------------------------------------

  @Test
  void dieSichtTraegtAlleAngabenDerKarte() {
    Instant marke = JETZT.minusSeconds(60);
    Card karte =
        new Card(
            null,
            BOARD,
            SPALTE_READY,
            7,
            "[Plan] Titel",
            "Text",
            0,
            true,
            null,
            null,
            JETZT,
            JETZT,
            CardType.CARD,
            null,
            null,
            null,
            PROJEKT,
            null,
            null,
            null,
            null,
            marke);
    long id = bau.lege(karte);

    assertThat(bau.leser.lade(id).karte())
        .isEqualTo(
            new KettenKarte(id, 7, "[Plan] Titel", "Text", "PLAN", "Ready", "READY", true, marke));
  }

  @Test
  void einEigenerStatusGehtDerSpalteVor() {
    long id = bau.lege(neu(1, "Paket", null, null).withStatus(CardStatus.IN_REVIEW), false, null);

    KettenKarte sicht = bau.leser.lade(id).karte();

    assertThat(sicht.spalte()).isEqualTo("Ready");
    assertThat(sicht.spaltenSchluessel()).isEqualTo("IN_REVIEW");
  }

  @Test
  void eineEigeneSpalteTraegtKeinenSchluesselUndFaelltNichtAufBacklogZurueck() {
    long id = bau.lege(inSpalte(neu(1, "A", null, null), SPALTE_EIGEN));

    KettenKarte sicht = bau.leser.lade(id).karte();

    assertThat(sicht.spalte()).isEqualTo("Anstehend");
    assertThat(sicht.spaltenSchluessel()).isNull();
  }

  @Test
  void dieSpaltenEinesBoardsWerdenJeAufrufNurEinmalGeladen() {
    long plan = bau.lege(neu(1, "Plan", null, null));
    long paket = bau.lege(neu(2, "Paket", null, plan));

    bau.leser.lade(paket);

    verify(bau.boards, times(1)).listColumns(BOARD);
  }

  // --- Ideen ----------------------------------------------------------------------------------

  @Test
  void dieIdeenWerdenMitDenNummernDerGanzenKetteGefragt() {
    long plan = bau.lege(neu(2, "Plan", null, null));
    long paket = bau.lege(neu(3, "Paket", null, plan), false, null);
    bau.lege(neu(4, "Kind", null, paket), true, null);
    bau.lege(imProjekt(neu(5, "Fremd", null, paket), FREMD));

    bau.leser.lade(paket);

    assertThat(bau.gefragteIdeenNummern.getFirst()).containsExactlyInAnyOrder(2, 3, 4);
    verify(bau.bestand).findMitErgebnisNummer(eq(PROJEKT), any());
  }

  @Test
  void dieNummerEinesGeloeschtenVorfahrenZaehltBeiDenIdeen() {
    long paket = bau.lege(neu(3, "Paket", null, null), false, 2);

    bau.leser.lade(paket);

    assertThat(bau.gefragteIdeenNummern.getFirst()).containsExactlyInAnyOrder(2, 3);
  }

  @Test
  void nurIdeenZaehlenAuchWennDieAbfrageAndereKartenFindet() {
    long paket = bau.lege(neu(3, "Paket", null, null));
    Card idee = mitId(neu(9, "[Idea] Einfall", "Ergebnis: Issue #3", null), 90L);
    Card keine = mitId(neu(8, "[Plan] Kein Einfall", "Ergebnis: Issue #3", null), 80L);
    bau.ideenKandidaten.addAll(List.of(keine, idee));

    assertThat(bau.leser.lade(paket).ideen()).extracting(KettenKarte::id).containsExactly(90L);
  }

  // --- Aktivitäten und Label-Ereignisse -------------------------------------------------------

  @Test
  void geladenWirdNurFuerSichtbareKartenDerKetteUndIhreIdeen() {
    long fremd = bau.lege(imProjekt(neu(9, "Geheim", null, null), FREMD));
    long papierkorb = bau.lege(neu(2, "Weg", null, fremd), true, null);
    long paket = bau.lege(neu(3, "Paket", null, papierkorb));
    long kind = bau.lege(neu(4, "Kind", null, paket));
    bau.lege(neu(5, "Unbeteiligt", null, null));
    bau.ideenKandidaten.add(mitId(neu(7, "[Idea] Einfall", "Ergebnis: Issue #3", null), 70L));

    bau.leser.lade(paket);

    Set<Long> erwartet = new HashSet<>(List.of(paket, kind, 70L));
    assertThat(bau.gefragteAktivitaeten.getFirst()).containsExactlyInAnyOrderElementsOf(erwartet);
    assertThat(bau.gefragteLabelEreignisse.getFirst())
        .containsExactlyInAnyOrderElementsOf(erwartet);
  }

  @Test
  void einAktivitaetseintragGehtMitAllenAngabenUeberDieGrenze() {
    long id = bau.lege(neu(1, "A", null, null));
    bau.aktivitaeten.add(
        new CardActivity(
            5L,
            id,
            3L,
            CardActivityType.MOVED,
            "Verschoben nach Ready",
            JETZT,
            CardActivityOrigin.TOKEN,
            "Nachtlauf",
            "claude-opus-5-5",
            JETZT.minusSeconds(5),
            CardStatus.READY));

    assertThat(bau.leser.lade(id).aktivitaeten())
        .containsExactly(
            new KettenAktivitaet(
                id,
                3L,
                "MOVED",
                "Verschoben nach Ready",
                JETZT,
                "TOKEN",
                "Nachtlauf",
                "claude-opus-5-5",
                "READY",
                JETZT.minusSeconds(5)));
  }

  @ParameterizedTest
  @CsvSource({
    "MOVED,Verschoben nach In review,IN_REVIEW",
    "MOVED,Verschoben nach Anstehend,",
    "MOVED,Sonst etwas,",
    "UPDATED,Verschoben nach Ready,"
  })
  void einZugOhneStatusNachherTraegtDenSchluesselNurAusDerProzessspalte(
      CardActivityType typ, String text, @Nullable String erwartet) {
    long id = bau.lege(neu(1, "A", null, null));
    bau.aktivitaeten.add(
        new CardActivity(5L, id, null, typ, text, JETZT, null, null, null, null, null));

    KettenAktivitaet eintrag = bau.leser.lade(id).aktivitaeten().getFirst();

    assertThat(eintrag.spaltenSchluessel()).isEqualTo(erwartet);
    assertThat(eintrag.origin()).isNull();
  }

  @Test
  void einLabelEreignisGehtMitAllenAngabenUeberDieGrenze() {
    long id = bau.lege(neu(1, "A", null, null));
    bau.labelEreignisse.add(
        new LabelEreignisMitPerson(
            5L, id, "kit:nightrun", JETZT, 3L, CardActivityOrigin.SESSION, null));
    bau.labelEreignisse.add(new LabelEreignisMitPerson(6L, id, "alt", JETZT, null, null, null));

    assertThat(bau.leser.lade(id).labelEreignisse())
        .containsExactly(
            new KettenLabelEreignis(id, "kit:nightrun", JETZT, 3L, "SESSION", null),
            new KettenLabelEreignis(id, "alt", JETZT, null, null, null));
  }

  // --- Kit-Karte im Projekt -------------------------------------------------------------------

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void obDasProjektEineKitKarteTraegtSagtDerBestand(boolean antwort) {
    when(bau.bestand.hatUeberTokenAngelegteKitKarte(PROJEKT)).thenReturn(antwort);

    assertThat(bau.leser.traegtKitKarte(PROJEKT)).isEqualTo(antwort);
    verify(bau.bestand, never()).findImBestand(any());
  }
}
