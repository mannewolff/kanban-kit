-- Herkunftsnummer eines endgueltig geloeschten Vorfahren (Issue #1576, Plan #1574 E12)
-- ---------------------------------------------------------------------------
-- Die FK fk_card_derived_from (V26) setzt beim Purge eines Vorfahren die Herkunft seiner
-- Kinder auf NULL; die Nummer des Vorfahren war damit verloren. Die Herkunftskette soll ein
-- endgueltig geloeschtes Glied aber als Luecke MIT Nummer nennen.
--
-- Geschrieben wird die Spalte nur unmittelbar vor einem Purge (CardArchiveService.purge,
-- TrashRetentionService, BoardPurgeCascade), gelesen nur bei derived_from_card_id IS NULL.
-- Ein nur beim Purge geschriebener Wert kann nicht veralten; eine laufend mitgefuehrte
-- Nummer wuerde beim Projektumzug des Vorfahren falsch. Das Neusetzen der Herkunft setzt
-- sie wieder auf NULL (CardRepositoryAdapter.save).
--
-- Rein additiv, kein Backfill: Vor dieser Migration geloeschte Vorfahren bleiben ohne
-- Nummer — die Information existiert nicht mehr.

ALTER TABLE card ADD COLUMN derived_from_lost_number integer;
