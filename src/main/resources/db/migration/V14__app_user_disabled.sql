-- Konten sperren/entsperren (Issue #178)
-- ---------------------------------------------------------------------------
-- Sperre eines Kontos getrennt von der Erstfreigabe (approved_at): disabled_at = NULL bedeutet
-- aktiv, ein Zeitstempel bedeutet gesperrt. disabled_by hält den sperrenden Admin fest.

ALTER TABLE app_user ADD COLUMN disabled_at timestamptz(6) NULL;
ALTER TABLE app_user ADD COLUMN disabled_by bigint NULL REFERENCES app_user (id) ON DELETE SET NULL;
