package org.mwolff.manban.fremdboard.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Die Verbindung eines Projekts zu seinem Board in einem fremden System (Issue #2156, Plan #2152).
 * Je Projekt höchstens eine; ihr Vorhandensein macht das Board des Projekts zu einem fremden.
 *
 * @param projectId das Projekt in kanban-kit
 * @param art das fremde System
 * @param basisUrl Adresse des fremden Systems
 * @param projektPfad Pfad des angebundenen Projekts im fremden System
 * @param zugangVerschluesselt der Lesezugang, verschlüsselt (A13) — erscheint nie in {@link
 *     #toString()}
 * @param kitKonto Benutzername des Kits im fremden System; {@code null}, wenn keiner hinterlegt ist
 * @param statuslabels Labelnamen je Status
 * @param boardId das Spiegel-Board in kanban-kit (A18)
 * @param angebundenAm ab diesem Zeitpunkt wird gezählt (A4)
 * @param letzterAbgleichAm Zeitpunkt des letzten Abgleichs; {@code null} vor dem ersten
 * @param letzterVollabgleichAm Zeitpunkt des letzten Vollabgleichs; {@code null} vor dem ersten
 * @param letzterFehler Fehler des letzten Abgleichs; {@code null}, wenn er gelang
 */
public record Verbindung(
    long projectId,
    FremdBoardArt art,
    String basisUrl,
    String projektPfad,
    String zugangVerschluesselt,
    @Nullable String kitKonto,
    StatusLabels statuslabels,
    long boardId,
    Instant angebundenAm,
    @Nullable Instant letzterAbgleichAm,
    @Nullable Instant letzterVollabgleichAm,
    @Nullable String letzterFehler) {

  /** Textform ohne den Zugang: Auch verschlüsselt gehört er in kein Protokoll. */
  @Override
  public String toString() {
    return "Verbindung[projectId="
        + projectId
        + ", art="
        + art
        + ", basisUrl="
        + basisUrl
        + ", projektPfad="
        + projektPfad
        + ", zugangVerschluesselt=***, kitKonto="
        + kitKonto
        + ", statuslabels="
        + statuslabels
        + ", boardId="
        + boardId
        + ", angebundenAm="
        + angebundenAm
        + ", letzterAbgleichAm="
        + letzterAbgleichAm
        + ", letzterVollabgleichAm="
        + letzterVollabgleichAm
        + ", letzterFehler="
        + letzterFehler
        + "]";
  }
}
