-- ---------------------------------------------------------------------------
-- Vorschau und Veröffentlichung an gemeldeten Auslieferungen (Issue #2215, Plan #2206 E10,
-- fachlich #2202 Kriterium 4)
--
-- Eine Meldung kann künftig eine Vorschau oder eine Veröffentlichung sein und eine Adresse tragen.
-- Vorschauen zählen in keiner Kennzahl der Platte „Auslieferung“ und verschieben den zuletzt
-- gemeldeten Stand nicht; eine Meldung ohne Art (alle vorhandenen Zeilen) zählt weiter als
-- Veröffentlichung. Die Adresse ist auf https beschränkt; das prüft die Schnittstelle.
--
-- Rein additiv: Eine ältere App-Version läuft gegen dieses Schema unverändert.
--
-- Rückweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V81 aus
-- flyway_schema_history löschen):
--   ALTER TABLE deployment_attempt DROP COLUMN url, DROP COLUMN art;
-- ---------------------------------------------------------------------------

ALTER TABLE deployment_attempt
    ADD COLUMN art varchar(20),
    ADD COLUMN url varchar(2000),
    ADD CONSTRAINT ck_deployment_attempt_art CHECK (art IN ('vorschau', 'veroeffentlichung'));
