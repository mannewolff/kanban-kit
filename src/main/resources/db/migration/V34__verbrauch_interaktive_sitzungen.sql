-- Interaktive Sitzungen teilen sich die Tabellen der Nachtlaeufe (Issue #1009, Plan #1007).
--
-- Seit dieser Migration tragen night_run und night_run_item zwei Gattungen: den Nachtlauf, den sie
-- bisher allein trugen, und die interaktive Sitzung. Eine Sitzung ist strukturell derselbe Satz
-- Daten wie ein Lauf -- Startzeitpunkt, Dauer, Verbrauch, Pakete je Karte -- und erbt damit die
-- gesamte Auswertung, statt sie ein zweites Mal zu bekommen (Plan #1007, E12).
--
-- Die Tabellen behalten ihren Namen (E13). Ein Rename beruehrte rund dreissig Dateien, die
-- Migrationen, das Kit und das Frontend, ohne ein Kriterium zu erfuellen; der Preis ist ein Name,
-- der weniger sagt als der Inhalt, und er steht hier, damit niemand aus "night_run" schliesst, es
-- lebten dort nur Nachtlaeufe.
--
-- kind steht als varchar(n) + CHECK und nicht als PG-Enum -- dem Muster von origin aus V32 folgend
-- (Projektkonvention). Kein Backfill: Bestandszeilen sind Nachtlaeufe und leben vom Vorgabewert.

ALTER TABLE night_run
    ADD COLUMN kind varchar(12) NOT NULL DEFAULT 'NIGHT';

ALTER TABLE night_run
    ADD CONSTRAINT ck_night_run_kind CHECK (kind IN ('NIGHT', 'INTERACTIVE'));

ALTER TABLE night_run_item
    ADD COLUMN kind varchar(12) NOT NULL DEFAULT 'NIGHT';

ALTER TABLE night_run_item
    ADD CONSTRAINT ck_night_run_item_kind CHECK (kind IN ('NIGHT', 'INTERACTIVE'));

-- INTERACTIVE ist die Laufart einer interaktiven Sitzung (E23). Ohne diesen Wert scheiterte ihre
-- Meldung an der Datenbank. DROP und ADD statt eines in-place-Umbaus wie in V30: Postgres kennt
-- kein ALTER CONSTRAINT fuer CHECKs, und beide Tabellen sind auf eine Handvoll aufbewahrter
-- Laeufe je Projekt begrenzt -- der erneute Vollscan beim ADD ist ohne Gewicht.
ALTER TABLE night_run DROP CONSTRAINT ck_night_run_mode;
ALTER TABLE night_run ADD CONSTRAINT ck_night_run_mode
    CHECK (mode IN ('IMPLEMENTATION', 'REVIEW', 'CHAIN', 'INTERACTIVE'));

-- Die beiden Teil-Indizes aus V33 nehmen die Gattung auf, jeder an der Stelle, die seine Abfrage
-- braucht: Die Verdraengung kappt innerhalb einer Gattung (E14) und filtert auf kind -- die Spalte
-- gehoert vor den Zeitpunkt, ueber den sortiert wird. Die Anlaeufe einer Karte filtern nicht nach
-- Gattung, sondern zeigen sie an (E10); dort steht kind am Ende und erspart den Griff in die
-- Tabelle.
DROP INDEX idx_night_run_item_orphan;
CREATE INDEX idx_night_run_item_orphan ON night_run_item (project_id, kind, started_at)
    WHERE night_run_id IS NULL;

DROP INDEX idx_night_run_item_card;
CREATE INDEX idx_night_run_item_card
    ON night_run_item (project_id, card_number, started_at, kind);

-- Der Erfassungsbeginn der interaktiven Sitzungen (E18). Daran unterscheidet die Anzeige spaeter
-- "nicht erfasst" von einer echten 0; aus der aeltesten vorhandenen Sitzung laesst sich das nicht
-- ableiten, weil die mit dem Ringpuffer nach vorn wandert. Nullbar und ohne Vorgabewert: Vor der
-- ersten Meldung eines Projekts gibt es diesen Zeitpunkt nicht.
ALTER TABLE project
    ADD COLUMN interactive_usage_since timestamptz(6);
