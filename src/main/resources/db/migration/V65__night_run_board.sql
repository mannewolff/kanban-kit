-- ---------------------------------------------------------------------------
-- Board eines Laufs (Issue #1993, Plan #1989, Fachliche Quelle #1961 AK 12)
--
-- Ein Lauf gehoert zu dem Board, ueber dessen Zugangsschluessel er gemeldet wird. NULL heisst
-- "kein Board erkennbar": jeder hochgeladene Lauf und jeder vor V65 gemeldete. Gesetzt wird die
-- Spalte bei der ersten Meldung eines Laufs; Folgemeldungen lassen sie stehen. Wird das Board
-- geloescht, bleibt der Lauf und verliert nur seine Zuordnung (ON DELETE SET NULL).
-- ---------------------------------------------------------------------------
ALTER TABLE night_run
    ADD COLUMN board_id BIGINT NULL REFERENCES board (id) ON DELETE SET NULL;

CREATE INDEX idx_night_run_board ON night_run (board_id);
