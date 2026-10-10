-- ---------------------------------------------------------------------------
-- Festgefahrene Arbeitspakete: Fehlerklasse STUCK und ihre Angaben an night_run_item
-- (Issue #1548, Plan #1547 E1, E8; fachliche Quelle #1546)
--
-- Die Bremse des claude-workflow-kit beendet ein Paket frueh, das sich an derselben Pruefung immer
-- wieder auf dieselbe Weise festfaehrt. Das Kit meldet den Ausgang als Zustand RED mit der
-- Fehlerklasse STUCK; die Bindung an RED steht bewusst nicht als CHECK hier (E3, wie V29).
--
-- Die fuenf Spalten tragen, was das Kit dazu meldet: Pruefung, wiederkehrender Fehler, Zahl der
-- Versuche, Zeitgrenze der Sitzung und Sitzungskennung. Jede ist nullbar -- NULL heisst „nicht
-- gemeldet" (E8). Die Laengen sind die Feldgrenzen aus E8; die Einlieferung prueft sie, damit eine
-- ueberlange Meldung als benannte 400 endet statt an der Spalte.
--
-- Rein additiv, keine Datenaenderung am Bestand: Bestandspakete tragen in allen fuenf Spalten NULL.
-- Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V50 aus
-- flyway_schema_history loeschen; vorher jede Zeile mit error_class = 'STUCK' umschreiben):
--   ALTER TABLE night_run_item
--       DROP COLUMN stuck_check, DROP COLUMN stuck_error, DROP COLUMN stuck_attempts,
--       DROP COLUMN stuck_session_limit_ms, DROP COLUMN stuck_session_id;
--   und ck_night_run_item_error_class in der Fassung aus V30 neu setzen.
-- ---------------------------------------------------------------------------

ALTER TABLE night_run_item DROP CONSTRAINT ck_night_run_item_error_class;
ALTER TABLE night_run_item ADD CONSTRAINT ck_night_run_item_error_class
    CHECK (error_class IN (
        'CHECKS_RED', 'CHECKS_NOT_STARTED', 'DEPENDENCY_UNMET', 'UNEXPECTED_STATE',
        'HARD_ABORT', 'AWAITING_DECISION', 'REVIEWER_FAILED', 'TIME_BUDGET_EXCEEDED',
        'STUCK'));

ALTER TABLE night_run_item
    ADD COLUMN stuck_check            varchar(300),
    ADD COLUMN stuck_error            varchar(1000),
    ADD COLUMN stuck_attempts         integer,
    ADD COLUMN stuck_session_limit_ms bigint,
    ADD COLUMN stuck_session_id       varchar(100);
