-- Epics: card bekommt einen Typ (CARD | EPIC), eine optionale selbstreferenzierende
-- Epic-Zuordnung (parent_id) und ein optionales Kürzel. Epics nehmen nicht am
-- Spalten-Workflow teil (siehe active_position unten) und erscheinen nicht auf dem Board.
--
-- Bestandsdaten: alle vorhandenen Zeilen sind normale Karten -> type='CARD' (Default),
-- parent_id=NULL, shortcode=NULL. Kein UPDATE nötig.

ALTER TABLE card
    ADD COLUMN type      varchar(20) NOT NULL DEFAULT 'CARD',
    ADD COLUMN parent_id bigint      REFERENCES card (id) ON DELETE SET NULL,
    ADD COLUMN shortcode varchar(16);

ALTER TABLE card ADD CONSTRAINT chk_card_type CHECK (type IN ('CARD', 'EPIC'));

CREATE INDEX idx_card_parent ON card (parent_id);

-- Wird ein Epic gelöscht, verlieren seine Kinder nur die Zuordnung (ON DELETE SET NULL);
-- sie bleiben als eigenständige Karten erhalten.

-- Positions-Namespace um Epics bereinigen: Epics halten keine aktive Position, sonst
-- kollidierten sie mit den Karten ihrer Spalte. Generierte Spalte neu definieren
-- (archiviert ODER Epic -> NULL, fällt aus dem Unique-Index). In Postgres erfordert das
-- Drop + Re-Add der Spalte (und damit des daran hängenden Unique-Constraints).
ALTER TABLE card DROP CONSTRAINT uq_card_active_position;
ALTER TABLE card DROP COLUMN active_position;
ALTER TABLE card
    ADD COLUMN active_position integer
    GENERATED ALWAYS AS (CASE WHEN archived OR type = 'EPIC' THEN NULL ELSE position_in_column END) STORED;
ALTER TABLE card ADD CONSTRAINT uq_card_active_position UNIQUE (board_id, column_id, active_position);
