package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Status einer Karte des fremden Boards aus Labels und Zustand (Issue #2158, Plan #2152 A3). */
class StatusZuordnungTest {

  private static final Instant ZEIT = Instant.parse("2026-11-15T10:00:00Z");

  private static final StatusZuordnung ZUORDNUNG =
      new StatusZuordnung(new StatusLabels("Backlog", "Ready", "In progress", "In review"));

  private static FremdKarte karte(String titel, boolean geschlossen, String... labels) {
    return new FremdKarte(7, titel, "", List.of(labels), geschlossen, ZEIT, ZEIT);
  }

  @ParameterizedTest
  @CsvSource({
    "Backlog, BACKLOG",
    "Ready, READY",
    "In progress, IN_PROGRESS",
    "In review, IN_REVIEW"
  })
  void statuslabel_ergibtSeinenStatus(String label, FremdStatus status) {
    assertThat(ZUORDNUNG.status(karte("Paket", false, "bug", label))).contains(status);
  }

  @Test
  void geschlossen_istDone() {
    assertThat(ZUORDNUNG.status(karte("Paket", true))).contains(FremdStatus.DONE);
  }

  @Test
  void geschlossen_istDone_auchMitStatuslabel() {
    assertThat(ZUORDNUNG.status(karte("Paket", true, "In review"))).contains(FremdStatus.DONE);
  }

  @Test
  void ohneStatuslabel_istBacklog() {
    assertThat(ZUORDNUNG.status(karte("Paket", false))).contains(FremdStatus.BACKLOG);
    assertThat(ZUORDNUNG.status(karte("Paket", false, "bug", "Doing")))
        .contains(FremdStatus.BACKLOG);
  }

  @Test
  void labelnamen_kommenAusDerEinrichtung() {
    StatusZuordnung eigene =
        new StatusZuordnung(
            new StatusLabels("Status::Offen", "Status::Bereit", "Status::Dran", "Status::Prüfen"));

    assertThat(eigene.status(karte("Paket", false, "Status::Bereit"))).contains(FremdStatus.READY);
    assertThat(eigene.status(karte("Paket", false, "Status::Dran")))
        .contains(FremdStatus.IN_PROGRESS);
    assertThat(eigene.status(karte("Paket", false, "Status::Prüfen")))
        .contains(FremdStatus.IN_REVIEW);
    assertThat(eigene.status(karte("Paket", false, "Status::Offen"))).contains(FremdStatus.BACKLOG);
    assertThat(eigene.status(karte("Paket", false, "Ready"))).contains(FremdStatus.BACKLOG);
  }

  @Test
  void labelnamen_werdenExaktVerglichen() {
    assertThat(ZUORDNUNG.status(karte("Paket", false, "ready"))).contains(FremdStatus.BACKLOG);
    assertThat(ZUORDNUNG.status(karte("Paket", false, "In Review"))).contains(FremdStatus.BACKLOG);
  }

  @Test
  void mehrereStatuslabels_dasErsteInDerReihenfolgeDerSpaltenGilt() {
    assertThat(ZUORDNUNG.status(karte("Paket", false, "In review", "Ready")))
        .contains(FremdStatus.READY);
    assertThat(ZUORDNUNG.status(karte("Paket", false, "In review", "In progress")))
        .contains(FremdStatus.IN_PROGRESS);
    assertThat(ZUORDNUNG.status(karte("Paket", false, "Ready", "Backlog")))
        .contains(FremdStatus.BACKLOG);
  }

  @ParameterizedTest
  @ValueSource(strings = {"[Task] Umbenennen", "[Human] Zugang anlegen", "[Mensch] Konto"})
  void taskUndMensch_sindStatusfaehig(String titel) {
    assertThat(StatusZuordnung.statusfaehig(titel)).isTrue();
    assertThat(ZUORDNUNG.status(karte(titel, false, "Ready"))).contains(FremdStatus.READY);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[Idea] roh",
        "[Idee] roh",
        "[Requirement] Story",
        "[Fachlich] Story",
        "[Plan] Weg",
        "  [plan] klein"
      })
  void dokumente_tragenKeinenStatus(String titel) {
    assertThat(StatusZuordnung.statusfaehig(titel)).isFalse();
    assertThat(ZUORDNUNG.status(karte(titel, false, "Ready"))).isEmpty();
    assertThat(ZUORDNUNG.status(karte(titel, true))).isEmpty();
  }

  @Test
  void ohnePraefix_istStatusfaehig() {
    assertThat(StatusZuordnung.statusfaehig("Schnittstelle bauen")).isTrue();
  }
}
