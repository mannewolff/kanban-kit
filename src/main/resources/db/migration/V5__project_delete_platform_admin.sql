-- ---------------------------------------------------------------------------
-- Projekt löschen wird System-Admin-Sache (Issue #49)
--
-- Das Anlegen und Löschen von Projekten ist künftig ausschließlich System-Admins
-- (Plattform-Rolle ADMIN) vorbehalten und keine Projekt-Rolle mehr. Das Recht
-- PROJECT_DELETE entfällt daher aus der Matrix; die zugehörigen role_permission-
-- Einträge werden über ON DELETE CASCADE mit entfernt.
-- ---------------------------------------------------------------------------

DELETE FROM permission WHERE key = 'PROJECT_DELETE';
