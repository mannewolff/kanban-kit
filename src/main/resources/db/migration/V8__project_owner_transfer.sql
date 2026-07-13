-- Eigentümer-Transfer als eigenes Recht (Issue #163)
-- ---------------------------------------------------------------------------
-- Das Recht, die Projekt-Eigentümerschaft zu übertragen, hat ausschließlich der amtierende
-- OWNER (bewusst NICHT ADMIN, anders als die generische Rollenänderung MEMBER_REMOVE).

INSERT INTO permission (key, description) VALUES
  ('PROJECT_OWNER_TRANSFER', 'Projekt-Eigentümerschaft an ein anderes Mitglied übertragen');

INSERT INTO role_permission (role, permission_id)
SELECT 'OWNER', id FROM permission WHERE key = 'PROJECT_OWNER_TRANSFER';
