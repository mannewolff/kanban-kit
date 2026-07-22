-- Karte board-optional + projektweiter Bezug (Fundament für den projektweiten Ideen-Pool)
-- ---------------------------------------------------------------------------
-- Bisher hängt jede Karte hart an einem Board. Für board-lose Ideen (Pool) bekommt die Karte einen
-- direkten project_id-Bezug (immer gesetzt, aus dem Board rückverfüllt) und board_id/column_id/number
-- werden optional. Ein notiertes Zielboard (target_board_id) hält den Board-Hinweis, z. B. aus dem
-- Ingest. Bestehende Karten bleiben board-gebunden; dieser Schritt ändert kein Verhalten.

-- project_id: neu, aus dem Board rückverfüllt, danach NOT NULL. Löscht das Projekt, fallen die Karten
-- mit (analog zur bisherigen board->card-Kaskade).
ALTER TABLE card ADD COLUMN project_id bigint REFERENCES project (id) ON DELETE CASCADE;
UPDATE card SET project_id = (SELECT b.project_id FROM board b WHERE b.id = card.board_id);
ALTER TABLE card ALTER COLUMN project_id SET NOT NULL;
CREATE INDEX idx_card_project ON card (project_id);

-- Board-Bindung wird optional (board-lose Pool-Ideen).
ALTER TABLE card ALTER COLUMN board_id DROP NOT NULL;
ALTER TABLE card ALTER COLUMN column_id DROP NOT NULL;
ALTER TABLE card ALTER COLUMN number DROP NOT NULL;

-- Notiertes Zielboard (z. B. aus dem board-gebundenen Ingest). Wird beim Löschen des Boards genullt.
ALTER TABLE card ADD COLUMN target_board_id bigint REFERENCES board (id) ON DELETE SET NULL;

-- Konsistenz: eine board-gebundene Karte muss Spalte UND Nummer haben; board-lose Pool-Ideen nicht.
-- uq_card_number (board_id, number) und uq_card_active_position bleiben gültig — in Postgres
-- kollidieren NULL-Werte in Unique-Constraints nicht, board-lose Ideen belegen also keinen Slot.
ALTER TABLE card ADD CONSTRAINT ck_card_board_consistency
    CHECK (board_id IS NULL OR (column_id IS NOT NULL AND number IS NOT NULL));

-- project_id einer board-gebundenen Karte aus dem Board ableiten, wenn nicht explizit gesetzt.
-- Hält project_id konsistent mit dem Board-Projekt (project_id ist bei board-gebundenen Karten
-- redundant zu board.project_id) und erlaubt board-gebundene Inserts ohne redundante Angabe.
-- Board-lose Pool-Ideen (board_id NULL) müssen project_id selbst setzen (der Anwendungscode tut das).
CREATE FUNCTION card_fill_project_id() RETURNS trigger AS $$
BEGIN
    IF NEW.project_id IS NULL AND NEW.board_id IS NOT NULL THEN
        NEW.project_id = (SELECT project_id FROM board WHERE id = NEW.board_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_card_fill_project_id BEFORE INSERT OR UPDATE ON card
    FOR EACH ROW EXECUTE FUNCTION card_fill_project_id();
