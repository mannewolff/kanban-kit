-- Ketten-Lauf und Abbruch am Zeitbudget in den Wertelisten der Nachtlauf-Auswertung (Plan #849)
-- ---------------------------------------------------------------------------
-- Verbindliche Fassung der Wertelisten bleibt der Parser frontend/src/lib/nightRunLog.ts
-- (Plan #718, A13); NightRunErrorClassSyncTest haelt Parser, Java-Typen und die Migrationen gleich
-- und wertet dazu je CHECK die zuletzt definierte Werteliste aus diesem Verzeichnis aus — also
-- diese Datei und nicht mehr V29__night_run.sql, wo die beiden CHECKs angelegt wurden.
--
-- CHAIN ist die Betriebsart des Ketten-Laufs (night.mjs --kette): eine Kette je fachlichem Issue
-- aus Plan, Review, Arbeitspaketen und Abdeckung. TIME_BUDGET_EXCEEDED ist die Fehlerklasse einer
-- Stufe, die am Zeitbudget beendet wurde (Issue #842).
--
-- NIGHTPLAN bleibt bewusst draussen: Dieser Modus entsteht allein im Browser und wird nie
-- eingeliefert (Plan #803, Architektonische Entscheidung 8).
--
-- Keine Spaltenaenderung noetig: mode ist varchar(20) und error_class varchar(40) aus V29;
-- CHAIN misst 5 Zeichen, TIME_BUDGET_EXCEEDED 20. Die Laengenpruefung des Abgleichtests liest
-- deshalb weiterhin V29 — dort steht die CREATE TABLE, und diese Datei ruehrt sie nicht an.
--
-- DROP und ADD statt eines in-place-Umbaus: Postgres kennt kein ALTER CONSTRAINT fuer CHECKs.
-- Beide Tabellen sind auf eine Handvoll aufbewahrter Laeufe je Projekt begrenzt (Ringpuffer aus
-- Plan #718), der erneute Vollscan beim ADD ist damit ohne Gewicht.

ALTER TABLE night_run DROP CONSTRAINT ck_night_run_mode;
ALTER TABLE night_run ADD CONSTRAINT ck_night_run_mode
    CHECK (mode IN ('IMPLEMENTATION', 'REVIEW', 'CHAIN'));

ALTER TABLE night_run_item DROP CONSTRAINT ck_night_run_item_error_class;
ALTER TABLE night_run_item ADD CONSTRAINT ck_night_run_item_error_class
    CHECK (error_class IN (
        'CHECKS_RED', 'CHECKS_NOT_STARTED', 'DEPENDENCY_UNMET', 'UNEXPECTED_STATE',
        'HARD_ABORT', 'AWAITING_DECISION', 'REVIEWER_FAILED', 'TIME_BUDGET_EXCEEDED'));
