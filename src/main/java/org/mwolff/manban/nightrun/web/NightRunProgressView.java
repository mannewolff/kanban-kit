package org.mwolff.manban.nightrun.web;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(
    description =
        "Der Laufstand: wie weit ein Lauf gekommen ist, abgelesen an seinen Spuren am Board."
            + " Je Karte nur Nummer, Titel und Board.")
record NightRunProgressView(
    @Schema(
            description =
                "OK: Die Karten des Laufs lassen sich zuordnen (einzelne können trotzdem unter"
                    + " unbekannt stehen). UNBEKANNT: Der Lauf trägt keinen Token-Namen, etwa weil"
                    + " er per Browser hochgeladen wurde — dann ist der ganze Laufstand unbekannt.",
            example = "OK")
        ProgressAssignment zuordnung,
    @Schema(
            description =
                "Die Ketten des Laufs, nach Nummer der Anforderung. Eine Kette führt eine"
                    + " fachliche Anforderung über Plan, Review, Arbeitspakete und Abdeckung."
                    + " Leer bei einem Lauf, der nur umsetzt.")
        List<ChainProgressView> ketten,
    @Schema(description = "Alle Arbeitspakete des Laufs mit ihrem Zustand, nach Nummer.")
        List<PackageProgressView> pakete,
    @Schema(description = "Karten, deren Zuordnung zum Lauf sich nicht feststellen lässt.")
        List<CardRefView> unbekannt,
    @Schema(description = "Karten mit einer offenen Frage an einen Menschen.")
        List<CardRefView> offeneFragen,
    @Schema(
            description =
                "true, wenn unter unbekannt Karten stehen, weil der Lauf sich nicht ausgewiesen"
                    + " hat und seine Spuren zeitlich zu einem anderen Lauf passen.",
            example = "false")
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
  @Schema(description = "Eine Kette des Laufs: eine fachliche Anforderung auf ihrem Weg.")
  record ChainProgressView(
      @Schema(description = "Die fachliche Anforderung, an der die Kette hängt.")
          CardRefView anforderung,
      @Schema(description = "Der Plan der Kette; null, solange keiner bekannt ist.")
          @Nullable CardRefView plan,
      @Schema(description = "Die Arbeitspakete, die der Lauf zum Plan angelegt hat, nach Nummer.")
          List<PackageProgressView> pakete,
      @Schema(
              description =
                  "Der Weg in seiner Reihenfolge: PLAN (Plan anlegen), REVIEW (Plan prüfen),"
                      + " PAKETE (Arbeitspakete anlegen), ABDECKUNG (Pakete gegen die"
                      + " Anforderung prüfen); UMSETZUNG nur, wenn die Kette die Pakete auch"
                      + " umsetzt.")
          List<StageProgressView> stufen,
      @Schema(
              description = "Die aktuelle Stufe; null, wenn das Ende des Wegs erreicht ist.",
              example = "PAKETE")
          @Nullable ProgressStage aktuelleStufe,
      @Schema(description = "true, wenn jede Stufe des Wegs erreicht ist.", example = "false")
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
  @Schema(description = "Eine Stufe im Weg einer Kette.")
  record StageProgressView(
      @Schema(description = "Die Stufe.", example = "REVIEW") ProgressStage stufe,
      @Schema(
              description =
                  "OFFEN: noch nicht erreicht; LAEUFT: die aktuelle Stelle; ERREICHT: erledigt.",
              example = "ERREICHT")
          StageState zustand) {

    static StageProgressView of(StageProgress s) {
      return new StageProgressView(s.stufe(), s.zustand());
    }
  }

  /** Ein Arbeitspaket des Laufs mit seinem Zustand. */
  @Schema(description = "Ein Arbeitspaket des Laufs mit seinem Zustand.")
  record PackageProgressView(
      @Schema(description = "Die Karte des Pakets.") CardRefView karte,
      @Schema(
              description =
                  "Aus dem heutigen Status der Karte: ANGELEGT (nur angelegt), GEZOGEN (nach"
                      + " Ready gezogen), IN_UMSETZUNG (In progress), FERTIG (In review oder"
                      + " Done), ZURUECKGESTELLT (zurück ins Backlog — ein Fehlschlag).",
              example = "FERTIG")
          PackageState zustand) {

    static PackageProgressView of(PackageProgress p) {
      return new PackageProgressView(CardRefView.of(p.karte()), p.zustand());
    }
  }

  /** Verweis auf eine Karte: Nummer, Titel und Board — keine Inhalte. */
  @Schema(description = "Verweis auf eine Karte — keine Inhalte.")
  record CardRefView(
      @Schema(description = "Projektweite Nummer der Karte.", example = "1403") int number,
      @Schema(description = "Titel der Karte.", example = "Export als CSV") String title,
      @Schema(description = "Kennung des Boards der Karte.", example = "3") long boardId) {

    static CardRefView of(CardRef k) {
      return new CardRefView(k.number(), k.title(), k.boardId());
    }
  }
}
