-- Plattform-Admins ohne Freigabe nachstempeln (Issue #558)
-- ---------------------------------------------------------------------------
-- Wer den Erst-Admin über den dokumentierten Datenbank-Weg eingerichtet hat, trägt in app_user
-- einen Plattform-Admin mit approved_at = NULL. Fachlich ist ein Admin implizit freigegeben; der
-- widersprüchliche Bestand zeigt ihn sonst als „Wartet auf Freigabe". Auf frischen Instanzen ist
-- die Migration ein No-op.
--
-- approved_by bleibt bewusst NULL: die Spalte verweist per Fremdschlüssel auf den freigebenden
-- Admin, und einen solchen gibt es hier nicht.

UPDATE app_user SET approved_at = now() WHERE platform_role = 'ADMIN' AND approved_at IS NULL;
