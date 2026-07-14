-- Papierkorb: Soft-Delete für Karten (Issue #179)
-- ---------------------------------------------------------------------------
-- Karten werden beim „Löschen" zunächst in den Papierkorb verschoben (deleted_at gesetzt,
-- reversibel), getrennt vom fachlichen Archiv (archived). Erst das endgültige Löschen (Purge)
-- bzw. der Retention-Job entfernt die Zeile physisch. Gelöschte Karten fallen — wie archivierte
-- und Epics — aus dem aktiven Positions-Namespace (active_position = NULL), damit sie keine
-- Positionen blockieren und nicht mit dem Unique-Constraint kollidieren.

ALTER TABLE card ADD COLUMN deleted_at timestamptz(6) NULL;

ALTER TABLE card DROP CONSTRAINT uq_card_active_position;
ALTER TABLE card DROP COLUMN active_position;
ALTER TABLE card
    ADD COLUMN active_position integer
    GENERATED ALWAYS AS (
        CASE WHEN archived OR type = 'EPIC' OR deleted_at IS NOT NULL
             THEN NULL ELSE position_in_column END
    ) STORED;
ALTER TABLE card ADD CONSTRAINT uq_card_active_position UNIQUE (board_id, column_id, active_position);

CREATE INDEX idx_card_deleted ON card (deleted_at);
