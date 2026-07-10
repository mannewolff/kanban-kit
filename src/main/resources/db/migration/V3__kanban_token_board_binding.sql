-- ---------------------------------------------------------------------------
-- Kanban-Token an Projekt + Board binden (Issue #44)
--
-- Ein PAT kann optional an genau ein Projekt + Board gebunden werden. Die
-- Kanban-Compat-API (#45) leitet daraus ab, welches Board ein tbx.mjs-/board.mjs-
-- Client bedient (der Client sendet nur den Token, keine Board-ID). Beide Spalten
-- sind nullable: bestehende Tokens bleiben ungebunden und weiter nutzbar.
-- ---------------------------------------------------------------------------

ALTER TABLE kanban_access_token
    ADD COLUMN project_id bigint REFERENCES project (id) ON DELETE CASCADE,
    ADD COLUMN board_id   bigint REFERENCES board (id)   ON DELETE CASCADE;
