-- ---------------------------------------------------------------------------
-- Ein App-Zugang je Person und Board (Issue #2212, Plan #2206 E4; Fachliche Quelle #2202)
--
-- Bisher hielt ein Board hoechstens einen wirksamen Zugang der App (V70). Kunde und Anbieter
-- haben je ihre eigene App, darum gilt die Grenze jetzt je Person: Der partielle eindeutige Index
-- auf (board_id, user_id) haelt "ein wirksamer Zugang je Person und Board" auch bei gleichzeitigem
-- Anlegen. Ein widerrufener Zugang faellt weiter aus dem Index.
--
-- Bestehende Daten erfuellen den neuen Index immer: Der alte liess je Board nur eine wirksame
-- Zeile zu, also erst recht nur eine je Person und Board.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V80 aus
-- flyway_schema_history loeschen). Er scheitert, solange ein Board mehr als einen wirksamen
-- Zugang der App traegt; die ueberzaehligen sind vorher zu widerrufen:
--   DROP INDEX uq_kanban_access_token_app_person;
--   CREATE UNIQUE INDEX uq_kanban_access_token_app_board ON kanban_access_token (board_id)
--       WHERE art = 'APP' AND revoked = false;
-- ---------------------------------------------------------------------------
DROP INDEX uq_kanban_access_token_app_board;

CREATE UNIQUE INDEX uq_kanban_access_token_app_person ON kanban_access_token (board_id, user_id)
    WHERE art = 'APP' AND revoked = false;
