-- Das Arbeitspaket ueberdauert die Verdraengung seines Laufs (Issue #964, Plan #963).
--
-- Bis hierher hing night_run_item mit ON DELETE CASCADE am Lauf: Verdraengte der Ringpuffer einen
-- Lauf, verschwanden seine Pakete mit ihm, und mit ihnen die Messwerte der Karte. Ab hier loescht
-- die Verdraengung nur noch den Lauf-Kopf; die Pakete bleiben verwaist stehen
-- (night_run_id IS NULL) und tragen Projekt, Startzeitpunkt und Lauf-Art selbst.
--
-- Die drei Spalten werden aus dem Lauf nachgetragen und danach NOT NULL gesetzt. Es gibt vor
-- dieser Migration keine verwaisten Pakete (night_run_id war NOT NULL mit CASCADE), der Backfill
-- trifft also jede Bestandszeile.
--
-- Die Wertelisten ck_night_run_item_error_class und ck_night_run_mode bleiben unangetastet; mode
-- traegt hier bewusst keinen eigenen CHECK: Der Wert stammt stets aus einem Lauf, dessen CHECK ihn
-- schon geprueft hat, und ein zweiter muesste bei jeder Erweiterung mitwandern
-- (NightRunErrorClassSyncTest kennt nur die beiden bestehenden).

ALTER TABLE night_run_item
    ALTER COLUMN night_run_id DROP NOT NULL;

ALTER TABLE night_run_item
    DROP CONSTRAINT fk_night_run_item_run;

ALTER TABLE night_run_item
    ADD CONSTRAINT fk_night_run_item_run FOREIGN KEY (night_run_id)
        REFERENCES night_run (id) ON DELETE SET NULL;

ALTER TABLE night_run_item
    ADD COLUMN project_id bigint,
    ADD COLUMN started_at timestamptz(6),
    ADD COLUMN mode       varchar(20);

UPDATE night_run_item i
   SET project_id = r.project_id,
       started_at = r.started_at,
       mode       = r.mode
  FROM night_run r
 WHERE r.id = i.night_run_id;

ALTER TABLE night_run_item
    ALTER COLUMN project_id SET NOT NULL,
    ALTER COLUMN started_at SET NOT NULL,
    ALTER COLUMN mode       SET NOT NULL;

-- Ohne eigenen Fremdschluessel ueberlebte ein verwaistes Paket das Loeschen seines Projekts: Der
-- Weg ueber night_run ist fuer genau diese Zeilen abgeschnitten.
ALTER TABLE night_run_item
    ADD CONSTRAINT fk_night_run_item_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE;

-- Zugriff auf die Anlaeufe einer Karte ueber Laeufe hinweg (Issue #967).
CREATE INDEX idx_night_run_item_card ON night_run_item (project_id, card_number, started_at);

-- Kappung und Wiedererkennung der verwaisten Pakete (Issues #965, #966). Pakete eines noch
-- aufbewahrten Laufs stehen nicht darin.
CREATE INDEX idx_night_run_item_orphan ON night_run_item (project_id, started_at)
    WHERE night_run_id IS NULL;
