package org.mwolff.manban.entscheidungskette.application;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.common.KitMeldungen.Meldungsart;
import org.mwolff.manban.entscheidungskette.domain.Akteurtyp;
import org.mwolff.manban.entscheidungskette.domain.CodeReview;
import org.mwolff.manban.entscheidungskette.domain.Dokumentart;
import org.mwolff.manban.entscheidungskette.domain.Freigabeangaben;
import org.mwolff.manban.entscheidungskette.domain.Fundzahlen;
import org.mwolff.manban.entscheidungskette.domain.Gliedart;
import org.mwolff.manban.entscheidungskette.domain.Hinweis;
import org.mwolff.manban.entscheidungskette.domain.Nachfahre;
import org.mwolff.manban.entscheidungskette.domain.Reviewer;
import org.mwolff.manban.entscheidungskette.domain.Uebertragung;
import org.mwolff.manban.entscheidungskette.domain.Umsetzungsangaben;
import org.mwolff.manban.entscheidungskette.domain.Veroeffentlichung;
import org.mwolff.manban.entscheidungskette.domain.Verweis;
import org.mwolff.manban.entscheidungskette.domain.Zustand;

/**
 * Die Entscheidungskette einer Karte, wie die Oberfläche sie liest (Issue #2117, Plan #2108). Ein
 * Mensch steht mit seinem Anzeigenamen da, nie mit seiner Personen-ID; eine fehlende Angabe ist
 * {@code null} und erscheint als „nicht erfasst“.
 *
 * @param sichtbar ob die Karte den Abschnitt trägt; {@code false} in einem Projekt ohne Kit, an
 *     einer Idee und an einem Vorhaben
 * @param art das Dokument der geöffneten Karte; {@code null}, wenn nicht sichtbar
 * @param abschnitte von der Idee bis zur geöffneten Karte
 * @param nachfahren nur am Fachplan und am Plan
 */
@Schema(description = "Die Entscheidungskette einer Karte: wie es zu ihr kam.")
public record EntscheidungsketteView(
    @Schema(
            description =
                "Ob die Karte den Abschnitt Entscheidungskette trägt. false in einem Projekt, in"
                    + " dem das Kit nicht arbeitet, an einer Idee und an einem Vorhaben; dann sind"
                    + " die Listen leer.")
        boolean sichtbar,
    @Schema(description = "Das Dokument der geöffneten Karte; null, wenn nicht sichtbar.")
        @Nullable Dokumentart art,
    @Schema(description = "Die Abschnitte von der Idee bis zur geöffneten Karte.")
        List<AbschnittView> abschnitte,
    @Schema(
            description =
                "Am Fachplan und am Plan je Nachfahre ein Verweis mit Spalte und zuletzt erreichtem"
                    + " Glied; sonst leer.")
        List<Nachfahre> nachfahren) {

  /** Die Antwort an einer Karte ohne Abschnitt. */
  public static final EntscheidungsketteView UNSICHTBAR =
      new EntscheidungsketteView(false, null, List.of(), List.of());

  /** Die Glieder eines Dokuments der Kette. */
  @Schema(description = "Die Glieder eines Dokuments der Kette.")
  public record AbschnittView(Dokumentart art, List<GliedView> glieder) {}

  /**
   * Ein Glied. Nur das Feld seiner Art unter {@code pruefung}, {@code freigabe}, {@code umsetzung},
   * {@code codeReview}, {@code uebertragung} und {@code veroeffentlichung} ist gesetzt.
   */
  @Schema(
      description =
          "Ein Glied der Kette. Eine fehlende Angabe ist null und steht als „nicht erfasst“. Von"
              + " den Angaben pruefung, freigabe, umsetzung, codeReview, uebertragung und"
              + " veroeffentlichung ist höchstens die der Art des Glieds gesetzt.")
  public record GliedView(
      Gliedart art,
      Zustand zustand,
      @Nullable AkteurView freigegebenVon,
      @Nullable AkteurView ausgefuehrtVon,
      @Nullable Instant zeitpunkt,
      @Nullable String ergebnis,
      Verweis quelle,
      Set<Hinweis> hinweise,
      @Nullable PruefungView pruefung,
      @Nullable Freigabeangaben freigabe,
      @Nullable Umsetzungsangaben umsetzung,
      @Nullable CodeReview codeReview,
      @Nullable Uebertragung uebertragung,
      @Nullable Veroeffentlichung veroeffentlichung) {}

  /**
   * Wer ein Glied getan hat.
   *
   * @param name der Anzeigename eines Menschen, der Name eines Modells oder eines Zugangs; {@code
   *     null}, wenn nicht erfasst, etwa bei einem gelöschten Nutzer
   * @param modell das gemeldete Modell eines Werkzeugs
   */
  @Schema(
      description =
          "Wer ein Glied getan hat: ein Mensch mit Anzeigenamen, ein Modell mit seinem Namen oder"
              + " ein Werkzeug mit dem Namen seines Zugangs und, soweit gemeldet, dem Modell.")
  public record AkteurView(Akteurtyp typ, @Nullable String name, @Nullable String modell) {}

  /** Die Angaben einer Prüfrunde mit ihrer Einarbeitung. */
  @Schema(description = "Die Angaben einer Prüfrunde mit ihrer Einarbeitung.")
  public record PruefungView(
      Meldungsart art,
      int runde,
      List<Reviewer> pruefer,
      @Nullable Fundzahlen funde,
      GliedView einarbeitung) {}
}
