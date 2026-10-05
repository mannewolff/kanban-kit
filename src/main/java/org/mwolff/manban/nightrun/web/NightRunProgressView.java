package org.mwolff.manban.nightrun.web;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.nightrun.domain.CardRef;
import org.mwolff.manban.nightrun.domain.ChainProgress;
import org.mwolff.manban.nightrun.domain.NightRunProgress;
import org.mwolff.manban.nightrun.domain.PackageProgress;
import org.mwolff.manban.nightrun.domain.PackageState;
import org.mwolff.manban.nightrun.domain.ProgressAssignment;
import org.mwolff.manban.nightrun.domain.ProgressStage;
import org.mwolff.manban.nightrun.domain.StageProgress;
import org.mwolff.manban.nightrun.domain.StageState;

/**
 * Der Fortschritt eines Laufs als Antwort von {@code GET …/night-runs/{runId}/progress} (Issue
 * #1375, Plan #1372).
 *
 * <p>Spiegelt {@link NightRunProgress} Feld für Feld, Enums als Namen — das Frontend baut seine
 * Typen genau danach. Eine Karte trägt nur Nummer, Titel und Board, <b>nie</b> Beschreibung oder
 * Kommentare (E10, Nicht-Ziel „Inhalte nicht wiedergeben").
 *
 * @param zuordnung ob sich die Karten des Laufs überhaupt zuordnen lassen (E3)
 * @param ketten die Ketten des Laufs; leer in der Umsetzungsnacht
 * @param pakete alle Arbeitspakete des Laufs
 * @param unbekannt Karten, deren Zuordnung sich nicht feststellen lässt
 * @param offeneFragen Karten mit einer offenen Frage an den Menschen
 * @param unbekanntOhneAusweis ob unter {@code unbekannt} Karten stehen, weil der Lauf sich nicht
 *     ausgewiesen hat (Issue #1429)
 */
record NightRunProgressView(
    ProgressAssignment zuordnung,
    List<ChainProgressView> ketten,
    List<PackageProgressView> pakete,
    List<CardRefView> unbekannt,
    List<CardRefView> offeneFragen,
    boolean unbekanntOhneAusweis) {

  static NightRunProgressView of(NightRunProgress p) {
    return new NightRunProgressView(
        p.zuordnung(),
        p.ketten().stream().map(ChainProgressView::of).toList(),
        p.pakete().stream().map(PackageProgressView::of).toList(),
        p.unbekannt().stream().map(CardRefView::of).toList(),
        p.offeneFragen().stream().map(CardRefView::of).toList(),
        p.unbekanntOhneAusweis());
  }

  /** Eine Kette des Laufs — siehe {@link ChainProgress}. */
  record ChainProgressView(
      CardRefView anforderung,
      @Nullable CardRefView plan,
      List<PackageProgressView> pakete,
      List<StageProgressView> stufen,
      @Nullable ProgressStage aktuelleStufe,
      boolean endeErreicht) {

    static ChainProgressView of(ChainProgress c) {
      CardRef plan = c.plan();
      return new ChainProgressView(
          CardRefView.of(c.anforderung()),
          plan == null ? null : CardRefView.of(plan),
          c.pakete().stream().map(PackageProgressView::of).toList(),
          c.stufen().stream().map(StageProgressView::of).toList(),
          c.aktuelleStufe(),
          c.endeErreicht());
    }
  }

  /** Eine Stufe im Weg einer Kette. */
  record StageProgressView(ProgressStage stufe, StageState zustand) {

    static StageProgressView of(StageProgress s) {
      return new StageProgressView(s.stufe(), s.zustand());
    }
  }

  /** Ein Arbeitspaket des Laufs mit seinem Zustand. */
  record PackageProgressView(CardRefView karte, PackageState zustand) {

    static PackageProgressView of(PackageProgress p) {
      return new PackageProgressView(CardRefView.of(p.karte()), p.zustand());
    }
  }

  /** Verweis auf eine Karte: Nummer, Titel und Board — keine Inhalte. */
  record CardRefView(int number, String title, long boardId) {

    static CardRefView of(CardRef k) {
      return new CardRefView(k.number(), k.title(), k.boardId());
    }
  }
}
