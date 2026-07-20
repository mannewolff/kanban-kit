-- Ideen-Speicher: unsichtbarer Kartenzustand (Backlog-Grooming)
-- ---------------------------------------------------------------------------
-- Analog zum Archiv (archived) und Papierkorb (deleted_at) fällt eine Karte im Ideen-Speicher
-- (idea_stored) aus dem aktiven Positions-Namespace (active_position = NULL): im Board unsichtbar,
-- nur in der Listenansicht (untere Zone) sichtbar, ohne Positionen zu blockieren. Von dort wird sie
-- ins Backlog hochgezogen. Die generierte Spalte active_position wird wie bei V15 gedroppt und mit
-- der zusätzlichen Bedingung neu angelegt.

ALTER TABLE card ADD COLUMN idea_stored boolean NOT NULL DEFAULT false;

ALTER TABLE card DROP CONSTRAINT uq_card_active_position;
ALTER TABLE card DROP COLUMN active_position;
ALTER TABLE card
    ADD COLUMN active_position integer
    GENERATED ALWAYS AS (
        CASE WHEN archived OR type = 'EPIC' OR deleted_at IS NOT NULL OR idea_stored
             THEN NULL ELSE position_in_column END
    ) STORED;
ALTER TABLE card ADD CONSTRAINT uq_card_active_position UNIQUE (board_id, column_id, active_position);
