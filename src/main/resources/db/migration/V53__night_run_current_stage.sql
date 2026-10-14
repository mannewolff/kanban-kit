-- ---------------------------------------------------------------------------
-- Laufende Stufen eines Laufs: current aus POST /api/kanban/night-runs
-- (Issue #1725, Plan #1724 E4, Vertrag V1)
--
-- Der Runner meldet mit jedem Laufbericht, welche Stufen gerade laufen: Karte, Stufe,
-- Beginn, Frist, Teilschritt und ob die Stufe die Umsetzung haelt. Die Meldung ersetzt
-- den Lauf vollstaendig, also auch seine Stufen. Ein beendeter Lauf traegt keine.
-- Eine Tabelle statt einer jsonb-Spalte an night_run, wie V48: Der Bestand nutzt nirgends
-- jsonb, und Tabellen halten ihre Pruefungen per Constraint.
--
-- stage und step ohne CHECK auf eine Werteliste (Plan #1724 E5): Ein neueres Kit mit einem
-- weiteren Code soll seinen Laufbericht nicht verlieren; das Board zeigt den Code woertlich.
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V53 aus
-- flyway_schema_history loeschen):
--   DROP TABLE night_run_current_stage;
-- ---------------------------------------------------------------------------

-- ON DELETE CASCADE, weil eine Stufe ohne ihren Lauf keine Aussage waere; der Ringpuffer
-- nimmt sie beim Verdraengen mit. position haelt die gemeldete Reihenfolge, die Grenze
-- 0 bis 9 entspricht @Size(max = 10) im Request.
CREATE TABLE night_run_current_stage (
    night_run_id         bigint       NOT NULL,
    position             integer      NOT NULL,
    card_number          integer      NOT NULL,
    stage                varchar(40)  NOT NULL,
    started_at           timestamptz  NOT NULL,
    deadline             timestamptz,
    step                 varchar(40)  NOT NULL,
    holds_implementation boolean      NOT NULL,
    CONSTRAINT pk_night_run_current_stage PRIMARY KEY (night_run_id, position),
    CONSTRAINT fk_night_run_current_stage_run FOREIGN KEY (night_run_id)
        REFERENCES night_run (id) ON DELETE CASCADE,
    CONSTRAINT ck_night_run_current_stage_position CHECK (position BETWEEN 0 AND 9)
);
