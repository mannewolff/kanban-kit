package org.mwolff.manban.card.application;

import java.util.List;

/**
 * Karten stehen unmittelbar vor dem endgültigen Löschen (Hard-Delete) — publiziert
 * <strong>vor</strong> dem Delete, im selben Transaktions-Scope (Issue #503).
 *
 * <p>Nachgelagerte Module (Anhänge) müssen ihre Aufräum-Aufträge einplanen, solange die Metadaten
 * noch existieren: Die Cascade-Kette {@code card → attachment_meta} nimmt mit der Kartenzeile auch
 * den {@code object_key} mit — danach gäbe es keinen Rekonstruktionspfad zum Blob mehr. Rollt die
 * Transaktion zurück, verschwinden die (über die Outbox vorgemerkten) Aufträge mit.
 *
 * @param cardIds IDs der endgültig zu löschenden Karten
 */
public record CardsPurgedEvent(List<Long> cardIds) {}
