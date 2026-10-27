-- ---------------------------------------------------------------------------
-- Farbschalter am Konto (Issue #1911)
--
-- Jedes Konto traegt seine Wahl des Erscheinungsbilds: SYSTEM folgt dem Farbschema von Browser
-- bzw. Betriebssystem, LIGHT und DARK legen es fest. Bestandskonten bekommen ueber die Vorgabe
-- SYSTEM, also genau das bisherige Verhalten.
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V61 aus
-- flyway_schema_history loeschen):
--   ALTER TABLE app_user DROP CONSTRAINT ck_app_user_color_scheme, DROP COLUMN color_scheme;
-- ---------------------------------------------------------------------------

ALTER TABLE app_user ADD COLUMN color_scheme varchar(16) NOT NULL DEFAULT 'SYSTEM';

ALTER TABLE app_user ADD CONSTRAINT ck_app_user_color_scheme
    CHECK (color_scheme IN ('SYSTEM', 'LIGHT', 'DARK'));
