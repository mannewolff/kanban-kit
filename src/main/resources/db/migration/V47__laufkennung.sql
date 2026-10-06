-- ---------------------------------------------------------------------------
-- Laufkennung an Kartenaktivitäten und Kommentaren (Issue #1426, Plan #1423)
--
-- run_started_at hält den startedAt des Nachtlaufs aus dem Header X-Night-Run fest —
-- gesetzt nur bei Token-Herkunft, eine Selbstauskunft wie agent. status_after ist der
-- Status der Karte nach einer Bewegung; gefüllt erst ab Issue #1427. Die Werteliste
-- der Constraint ist die von chk_card_status (V46). comment.run_started_at nutzt erst
-- das Kommentar-Paket (Issue #1428); die Migration entsteht hier in einem Stück.
--
-- Alle Spalten nullable, ohne Backfill und ohne Index (Review-Funde 7 und 8 zu Plan
-- #1423): Alt-Einträge tragen keine Laufkennung.
-- ---------------------------------------------------------------------------

ALTER TABLE card_activity ADD COLUMN run_started_at timestamptz;

ALTER TABLE card_activity ADD COLUMN status_after varchar(20);

ALTER TABLE card_activity ADD CONSTRAINT chk_card_activity_status_after
    CHECK (status_after IS NULL OR status_after IN ('BACKLOG', 'READY', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'));

ALTER TABLE comment ADD COLUMN run_started_at timestamptz;
