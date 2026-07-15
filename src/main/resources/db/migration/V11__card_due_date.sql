-- Fälligkeitsdatum an Karten (Issue #175)
-- ---------------------------------------------------------------------------
-- Optionales Fälligkeitsdatum je Karte; NULL = kein Termin. Nur an normalen Karten sinnvoll.

ALTER TABLE card ADD COLUMN due_date timestamptz(6) NULL;
