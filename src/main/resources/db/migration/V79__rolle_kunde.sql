-- ---------------------------------------------------------------------------
-- Rolle Kunde und Recht auf Störungen (Issue #2209, Plan #2206 E1, E2)
--
-- CUSTOMER ist die Projektrolle für Kunden eines Anbieters: Sie erhält genau die
-- Rechte von MEMBER. Das neue Recht REPAIR_READ (Störungen beim Anbieter sehen)
-- erhalten VIEWER, MEMBER, ADMIN und OWNER — nicht CUSTOMER. Weil REPAIR_READ erst
-- nach dem Kopieren der MEMBER-Rechte an MEMBER geht, bleibt es dem Kunden fern.
-- ---------------------------------------------------------------------------

ALTER TABLE project_membership DROP CONSTRAINT ck_project_membership_role;
ALTER TABLE project_membership ADD CONSTRAINT ck_project_membership_role
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER', 'CUSTOMER'));

ALTER TABLE project_invitation DROP CONSTRAINT ck_project_invitation_role;
ALTER TABLE project_invitation ADD CONSTRAINT ck_project_invitation_role
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER', 'CUSTOMER'));

ALTER TABLE role_permission DROP CONSTRAINT ck_role_permission_role;
ALTER TABLE role_permission ADD CONSTRAINT ck_role_permission_role
    CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER', 'CUSTOMER'));

INSERT INTO role_permission (role, permission_id)
SELECT 'CUSTOMER', permission_id FROM role_permission WHERE role = 'MEMBER';

INSERT INTO permission (key, description) VALUES
    ('REPAIR_READ', 'Störungen beim Anbieter sehen');

INSERT INTO role_permission (role, permission_id)
SELECT r.role, p.id
FROM permission p
CROSS JOIN (VALUES ('VIEWER'), ('MEMBER'), ('ADMIN'), ('OWNER')) AS r(role)
WHERE p.key = 'REPAIR_READ';
