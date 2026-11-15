package org.mwolff.manban.fremdboard.application;

import java.io.Serializable;
import org.mwolff.manban.common.i18n.UebersetzbareMeldung;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Das Projekt lässt sich im jetzigen Zustand nicht an ein fremdes Board binden, oder die bestehende
 * Bindung lässt sich nicht lösen (Issue #2166, Plan #2152 A2, A13, A18). Zustandskonflikt, darum
 * 409; die Meldung nennt den Grund.
 *
 * <p>Je Fall eine Fabrikmethode mit eigenem Meldungsschlüssel (Issue #1642).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public final class AnbindenAbgelehntException extends RuntimeException
    implements UebersetzbareMeldung {

  private static final long serialVersionUID = 1L;

  private final String meldungsschluessel;
  private final Serializable[] meldungsargumente;

  private AnbindenAbgelehntException(String schluessel, String text, Serializable... argumente) {
    super(text);
    this.meldungsschluessel = schluessel;
    this.meldungsargumente = argumente;
  }

  /** Das Projekt hat schon Karten, gleich wo sie liegen (A2). */
  public static AnbindenAbgelehntException hatKarten(
      int aufBoards, int archiviert, int papierkorb, int vorhaben) {
    return new AnbindenAbgelehntException(
        "fremdboard.hat-karten",
        "Das Projekt hat schon Karten in kanban-kit: "
            + aufBoards
            + " auf Boards, "
            + archiviert
            + " im Archiv, "
            + papierkorb
            + " im Papierkorb, "
            + vorhaben
            + " Vorhaben. Ein Board in einem fremden System lässt sich nur für ein Projekt ohne"
            + " Karten einrichten.",
        aufBoards,
        archiviert,
        papierkorb,
        vorhaben);
  }

  /** Das Projekt hat Boards, die dem Spiegel-Board im Weg stehen (A18). */
  public static AnbindenAbgelehntException weitereBoards(String boards) {
    return new AnbindenAbgelehntException(
        "fremdboard.weitere-boards",
        "Das Projekt hat diese Boards: "
            + boards
            + ". Für ein Board in einem fremden System darf das Projekt höchstens ein aktives"
            + " Board haben, das kanban-kit als Spiegel übernimmt; archivierte zählen mit.",
        boards);
  }

  /** Das einzige Board hat nicht für jeden Status eine Spalte (A18). */
  public static AnbindenAbgelehntException spaltenFehlen(String board, String status) {
    return new AnbindenAbgelehntException(
        "fremdboard.spalten-fehlen",
        "Das Board "
            + board
            + " hat keine Spalte für: "
            + status
            + ". Das Spiegel-Board braucht je eine Spalte Backlog, Ready, In progress, In review"
            + " und Done.",
        board,
        status);
  }

  /** Ohne Schlüssel lässt sich kein Zugang verschlüsselt ablegen (A13). */
  public static AnbindenAbgelehntException keinSchluessel() {
    return new AnbindenAbgelehntException(
        "fremdboard.kein-schluessel",
        "Für den Zugang ist kein Schlüssel gesetzt (MANBAN_SECRET_KEY). Ohne ihn lässt sich kein"
            + " fremdes Board anbinden.");
  }

  /** Eine bestehende Bindung lässt sich weder lösen noch umhängen. */
  public static AnbindenAbgelehntException loesenGesperrt() {
    return new AnbindenAbgelehntException(
        "fremdboard.loesen-gesperrt",
        "Ein angebundenes Board lässt sich nicht lösen und nicht an ein anderes Projekt im fremden"
            + " System hängen. Ändern lassen sich Zugang, Kit-Konto und Statuslabels.");
  }

  @Override
  public String schluessel() {
    return meldungsschluessel;
  }

  @Override
  public Object[] argumente() {
    return meldungsargumente.clone();
  }
}
