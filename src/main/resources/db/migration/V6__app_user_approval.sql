-- Admin-Freigabe von Registrierungen (Issue #0097)
-- ---------------------------------------------------------------------------
-- Ein Benutzer darf sich erst nach Freigabe durch einen Plattform-Admin einloggen.
-- approved_at = NULL bedeutet „wartet auf Freigabe" (pending).

ALTER TABLE app_user ADD COLUMN approved_at timestamptz(6) NULL;
ALTER TABLE app_user ADD COLUMN approved_by bigint NULL REFERENCES app_user (id);

-- Backfill: alle bereits bestehenden Benutzer gelten als freigegeben (kein Aussperren).
UPDATE app_user SET approved_at = created_at WHERE approved_at IS NULL;
