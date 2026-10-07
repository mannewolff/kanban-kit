-- ---------------------------------------------------------------------------
-- Abschlussart eines abgebrochenen Nachtlaufs: abortKind aus POST /api/kanban/night-runs
-- (Issue #1500, Plan #1498 E2, E5)
--
-- Der Abbruchgrund aus V42 sagt nicht, wie der Lauf zu seinem Abschluss kam: Einen Grund
-- traegt auch der Lauf, den der Waechter des Kits nachtraeglich als verstummt abschliesst.
-- REPORTED heisst „Abbruch selbst gemeldet", SILENCED „vom Waechter abgeschlossen". Nur ein
-- selbst gemeldeter Abbruch ohne angefasstes Paket ist „nicht angelaufen" und keine Stoerung.
--
-- NULL heisst: nicht abgebrochen, von einer aelteren Kit-Kopie gemeldet oder Bestand vor dieser
-- Migration. Der Befund liest NULL wie bisher -- ein Abbruch ohne Art bleibt eine Stoerung
-- (Plan #1498 A4).
--
-- Rein additiv und ohne Backfill (E5): Ein Backfill aus dem Grundtext faerbte Bestandslaeufe
-- um (V42-Praezedenz). varchar(n) + CHECK statt PG-Enum, dem Muster von budget_origin aus V37
-- folgend. Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V49 aus
-- flyway_schema_history loeschen):
--   ALTER TABLE night_run DROP COLUMN abort_kind;
-- ---------------------------------------------------------------------------

ALTER TABLE night_run
    ADD COLUMN abort_kind varchar(16),
    ADD CONSTRAINT ck_night_run_abort_kind CHECK (abort_kind IN ('REPORTED', 'SILENCED'));
