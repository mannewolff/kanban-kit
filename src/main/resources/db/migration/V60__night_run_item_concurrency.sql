-- ---------------------------------------------------------------------------
-- Gleichzeitige Umsetzung: drei Angaben je Arbeitspaket an night_run_item
-- (Issue #1877, Plan #1876 E4, E5; fachliche Quelle #1863)
--
-- Das claude-workflow-kit meldet je Paket concurrent, checkWaitMs und deferredAtMerge, wenn die
-- Nacht mit mehr als einem Platz lief. Die drei Wertspalten tragen den gedeuteten Wert; NULL heisst:
-- Feld nicht gefuehrt bzw. nicht gemeldet (ein Wert ausserhalb seines Wertebereichs wird schon bei
-- der Einlieferung zu NULL, E3).
--
-- concurrency_reported ist der Marker „mindestens eines der drei Felder gefuehrt“: Ohne ihn liest
-- Hibernate ein Paket, dessen drei Felder alle kaputt gemeldet wurden, wie eines ohne Angaben
-- (Embeddable aus lauter NULL). Er ist NULL oder true; ohne ihn sind die drei Wertspalten NULL.
--
-- Rein additiv, keine Datenaenderung am Bestand: Bestandspakete tragen in allen vier Spalten NULL
-- und zeigen nichts. Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V60 aus
-- flyway_schema_history loeschen):
--   ALTER TABLE night_run_item
--       DROP CONSTRAINT ck_night_run_item_concurrency_reported,
--       DROP CONSTRAINT ck_night_run_item_concurrency_marker,
--       DROP CONSTRAINT ck_night_run_item_concurrency_check_wait_ms,
--       DROP COLUMN concurrency_reported, DROP COLUMN concurrency_concurrent,
--       DROP COLUMN concurrency_check_wait_ms, DROP COLUMN concurrency_deferred_at_merge;
-- ---------------------------------------------------------------------------

ALTER TABLE night_run_item
    ADD COLUMN concurrency_reported          boolean,
    ADD COLUMN concurrency_concurrent        boolean,
    ADD COLUMN concurrency_check_wait_ms     bigint,
    ADD COLUMN concurrency_deferred_at_merge boolean;

ALTER TABLE night_run_item ADD CONSTRAINT ck_night_run_item_concurrency_reported
    CHECK (concurrency_reported IS NULL OR concurrency_reported);

ALTER TABLE night_run_item ADD CONSTRAINT ck_night_run_item_concurrency_marker
    CHECK (concurrency_reported IS NOT NULL
           OR (concurrency_concurrent IS NULL
               AND concurrency_check_wait_ms IS NULL
               AND concurrency_deferred_at_merge IS NULL));

ALTER TABLE night_run_item ADD CONSTRAINT ck_night_run_item_concurrency_check_wait_ms
    CHECK (concurrency_check_wait_ms >= 0);
