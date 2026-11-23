package org.mwolff.manban.fremdboard.application;

import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.mwolff.manban.fremdboard.domain.FremdBoardArt;

/**
 * Die knappe Auskunft über das Board eines Projekts für jedes Mitglied (Issue #2223, Plan #2221
 * E2): ob es in einem fremden System liegt, in welchem, von wann der zuletzt bekannte Stand ist und
 * ob der letzte Abgleich scheiterte. Konto des Kits, Fehlertext, Adresse, Projektpfad und alles zum
 * Zugang stehen bewusst nicht darin; sie bleiben in der {@link VerbindungAnsicht} des Owners.
 *
 * @param imFremdsystem ob das Board des Projekts in einem fremden System liegt
 * @param art das fremde System; leer, wenn das Board in kanban-kit liegt
 * @param standAm Zeitpunkt des zuletzt gelungenen Abgleichs; leer, solange keiner gelang
 * @param abgleichGescheitert ob der letzte Abgleich scheiterte
 */
public record FremdBoardStand(
    boolean imFremdsystem,
    @Nullable FremdBoardArt art,
    @Nullable Instant standAm,
    boolean abgleichGescheitert) {

  /** Die Auskunft für ein Projekt, dessen Board in kanban-kit liegt. */
  public static FremdBoardStand ohne() {
    return new FremdBoardStand(false, null, null, false);
  }
}
