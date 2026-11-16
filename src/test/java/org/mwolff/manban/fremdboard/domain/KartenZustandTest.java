package org.mwolff.manban.fremdboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mwolff.manban.fremdboard.domain.FremdEreignis.Art;

/**
 * Der Zustand einer Karte zwischen zwei Ereignissen (Issue #2167, Plan #2152 A4): rückwärts aus dem
 * heutigen Stand, vorwärts Ereignis für Ereignis.
 */
class KartenZustandTest {

  private static final Instant ANGELEGT = Instant.parse("2026-11-01T09:00:00Z");
  private static final Instant GEAENDERT = Instant.parse("2026-11-02T10:00:00Z");
  private static final StatusZuordnung ZUORDNUNG = new StatusZuordnung(StatusLabels.STANDARD);

  private static FremdKarte karte(List<String> labels, boolean geschlossen) {
    return new FremdKarte(7, "Export", "", labels, geschlossen, ANGELEGT, GEAENDERT);
  }

  private static FremdEreignis ereignis(Art art, String label) {
    return new FremdEreignis("e", 7, art, label, GEAENDERT, "anna");
  }

  @Test
  void vonDerKarte_uebernimmtLabelsUndZustand() {
    KartenZustand zustand = KartenZustand.von(karte(List.of("Ready", "kit:nightrun"), true));

    assertThat(zustand.labels()).containsExactly("Ready", "kit:nightrun");
    assertThat(zustand.geschlossen()).isTrue();
  }

  @Test
  void nach_labelGesetzt_fuegtHinzuOhneDoppelung() {
    KartenZustand zustand = KartenZustand.von(karte(List.of("Ready"), false));

    KartenZustand danach =
        zustand
            .nach(ereignis(Art.LABEL_GESETZT, "In progress"))
            .nach(ereignis(Art.LABEL_GESETZT, "In progress"));

    assertThat(danach.labels()).containsExactly("Ready", "In progress");
    assertThat(zustand.labels()).containsExactly("Ready");
  }

  @Test
  void nach_labelEntfernt_nimmtWeg() {
    KartenZustand danach =
        KartenZustand.von(karte(List.of("Ready", "kit:nightrun"), false))
            .nach(ereignis(Art.LABEL_ENTFERNT, "Ready"));

    assertThat(danach.labels()).containsExactly("kit:nightrun");
  }

  @Test
  void nach_geschlossenUndWiederGeoeffnet() {
    KartenZustand offen = KartenZustand.von(karte(List.of(), false));

    KartenZustand zu = offen.nach(ereignis(Art.GESCHLOSSEN, null));

    assertThat(zu.geschlossen()).isTrue();
    assertThat(zu.nach(ereignis(Art.WIEDER_GEOEFFNET, null)).geschlossen()).isFalse();
  }

  @Test
  void vor_machtJedesEreignisRueckgaengig() {
    KartenZustand heute = KartenZustand.von(karte(List.of("In review", "kit:nightrun"), true));

    KartenZustand vorher =
        heute.vor(
            List.of(
                ereignis(Art.LABEL_ENTFERNT, "Ready"),
                ereignis(Art.LABEL_GESETZT, "In review"),
                ereignis(Art.GESCHLOSSEN, null)));

    assertThat(vorher.labels()).containsExactlyInAnyOrder("Ready", "kit:nightrun");
    assertThat(vorher.geschlossen()).isFalse();
  }

  @Test
  void vor_wiederGeoeffnet_warVorherGeschlossen() {
    KartenZustand vorher =
        KartenZustand.von(karte(List.of(), false))
            .vor(List.of(ereignis(Art.WIEDER_GEOEFFNET, null)));

    assertThat(vorher.geschlossen()).isTrue();
  }

  @Test
  void vor_inUmgekehrterReihenfolge() {
    // Given: erst gesetzt, dann wieder entfernt — heute fehlt das Label.
    KartenZustand heute = KartenZustand.von(karte(List.of(), false));

    // When
    KartenZustand vorher =
        heute.vor(
            List.of(ereignis(Art.LABEL_GESETZT, "Ready"), ereignis(Art.LABEL_ENTFERNT, "Ready")));

    // Then: vor beiden fehlte es ebenfalls.
    assertThat(vorher.labels()).isEmpty();
  }

  @Test
  void ohneLabelImEreignis_bleibenDieLabels() {
    KartenZustand zustand = KartenZustand.von(karte(List.of("Ready"), false));

    assertThat(zustand.nach(ereignis(Art.LABEL_GESETZT, null)).labels()).containsExactly("Ready");
    assertThat(zustand.nach(ereignis(Art.LABEL_ENTFERNT, null)).labels()).containsExactly("Ready");
  }

  @Test
  void status_folgtDerZuordnungMitDemTitelDerKarte() {
    FremdKarte heute = karte(List.of("In progress"), false);

    assertThat(KartenZustand.von(heute).status(heute, ZUORDNUNG)).contains(FremdStatus.IN_PROGRESS);
    assertThat(
            KartenZustand.von(heute).nach(ereignis(Art.GESCHLOSSEN, null)).status(heute, ZUORDNUNG))
        .contains(FremdStatus.DONE);
    FremdKarte plan =
        new FremdKarte(3, "[Plan] Export", "", List.of("Ready"), false, ANGELEGT, GEAENDERT);
    assertThat(KartenZustand.von(plan).status(plan, ZUORDNUNG)).isEmpty();
  }

  @Test
  void labelsSindUnveraenderlich() {
    KartenZustand zustand = KartenZustand.von(karte(List.of("Ready"), false));

    assertThat(zustand.labels()).isUnmodifiable();
  }
}
