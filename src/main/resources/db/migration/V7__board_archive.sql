-- Board-Archivierung statt Hard-Delete (Issue #148)
-- ---------------------------------------------------------------------------
-- Ein Board wird beim „Löschen" zunächst archiviert (reversibel), statt physisch entfernt.
-- archived_at = NULL bedeutet „aktiv"; ein Zeitstempel bedeutet „archiviert". Erst das
-- endgültige Löschen (Purge) entfernt die Zeile physisch samt Cascade (Spalten/Karten).

ALTER TABLE board ADD COLUMN archived_at timestamptz(6) NULL;
