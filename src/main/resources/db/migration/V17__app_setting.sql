-- Globale, zur Laufzeit vom Plattform-Admin änderbare App-Einstellungen (Key-Value).
-- ---------------------------------------------------------------------------
-- Bewusst als generische KV-Tabelle (zukunftssicher für weitere globale Settings). Erste Nutzung:
-- der Done-Retention-Override (Schlüssel done.retention.days). Nicht gesetzter Schlüssel = Env-Default
-- (manban.cleanup.done-retention-days); gesetzter Wert überschreibt, 0 = Auto-Archiv aus.
-- Spaltennamen mit setting_-Präfix, um Postgres-Reservierungen (key/value) zu vermeiden.

CREATE TABLE app_setting (
    setting_key   varchar(100) PRIMARY KEY,
    setting_value varchar(500) NOT NULL
);
