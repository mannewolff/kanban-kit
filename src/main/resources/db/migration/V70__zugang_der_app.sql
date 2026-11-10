-- ---------------------------------------------------------------------------
-- Zugang der Desktop-App (Issue #2102, Plan #2100 E1, E6; Fachliche Quelle #2097)
--
-- art unterscheidet den Zugang der App von einem gewoehnlichen Token. Bestehende Zeilen erhalten
-- ueber den Vorgabewert STANDARD. Ein Zugang der App ist immer an ein Board gebunden.
--
-- Der partielle eindeutige Index haelt "ein wirksamer Zugang je Board" auch bei gleichzeitigem
-- Anlegen (E6). Ein widerrufener Zugang faellt aus dem Index, danach kann sofort ein neuer entstehen.
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V70 aus
-- flyway_schema_history loeschen):
--   DROP INDEX uq_kanban_access_token_app_board;
--   ALTER TABLE kanban_access_token DROP COLUMN art;
-- ---------------------------------------------------------------------------
ALTER TABLE kanban_access_token
    ADD COLUMN art varchar(20) NOT NULL DEFAULT 'STANDARD',
    ADD CONSTRAINT ck_kanban_access_token_art CHECK (art IN ('STANDARD', 'APP')),
    ADD CONSTRAINT ck_kanban_access_token_app_board CHECK (art <> 'APP' OR board_id IS NOT NULL);

CREATE UNIQUE INDEX uq_kanban_access_token_app_board ON kanban_access_token (board_id)
    WHERE art = 'APP' AND revoked = false;
