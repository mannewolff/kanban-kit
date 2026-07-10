-- ---------------------------------------------------------------------------
-- Granulare CRUD-Rechte-Matrix (Issue #48)
--
-- Ersetzt das grobe Rechte-Set durch eine granulare Matrix (je Ressource/Operation
-- ein Recht). Fundament für konfigurierbare Rollen in 2.0. `permission` und
-- `role_permission` sind statische Seed-Tabellen und werden komplett neu aufgebaut;
-- Projekte/Mitgliedschaften bleiben unberührt.
-- ---------------------------------------------------------------------------

DELETE FROM role_permission;
DELETE FROM permission;

INSERT INTO permission (key, description) VALUES
    ('BOARD_CREATE',       'Boards anlegen'),
    ('BOARD_READ',         'Boards lesen'),
    ('BOARD_UPDATE',       'Boards und Spalten bearbeiten'),
    ('BOARD_DELETE',       'Boards löschen'),
    ('EPIC_CREATE',        'Epics anlegen'),
    ('EPIC_READ',          'Epics lesen'),
    ('EPIC_UPDATE',        'Epics bearbeiten'),
    ('EPIC_DELETE',        'Epics löschen'),
    ('TICKET_CREATE',      'Tickets anlegen'),
    ('TICKET_READ',        'Tickets lesen'),
    ('TICKET_UPDATE',      'Tickets bearbeiten'),
    ('TICKET_DELETE',      'Tickets archivieren/löschen'),
    ('CARD_MOVE',          'Karten verschieben'),
    ('COMMENT_CREATE',     'Kommentare schreiben'),
    ('COMMENT_READ',       'Kommentare lesen'),
    ('COMMENT_UPDATE',     'Eigene Kommentare bearbeiten'),
    ('COMMENT_DELETE',     'Kommentare löschen (Moderation)'),
    ('ATTACHMENT_CREATE',  'Anhänge hochladen'),
    ('ATTACHMENT_READ',    'Anhänge lesen'),
    ('ATTACHMENT_DELETE',  'Anhänge löschen'),
    ('MEMBER_INVITE',      'Mitglieder einladen'),
    ('MEMBER_REMOVE',      'Mitglieder entfernen/Rolle ändern'),
    ('PROJECT_EDIT',       'Projekt bearbeiten'),
    ('PROJECT_DELETE',     'Projekt löschen');

-- VIEWER: nur lesen.
INSERT INTO role_permission (role, permission_id)
SELECT 'VIEWER', id FROM permission
WHERE key IN ('BOARD_READ', 'EPIC_READ', 'TICKET_READ', 'COMMENT_READ', 'ATTACHMENT_READ');

-- MEMBER: lesen + Tickets/Epics C/U/D, Move, Kommentar C/U, Anhang C/D.
INSERT INTO role_permission (role, permission_id)
SELECT 'MEMBER', id FROM permission
WHERE key IN ('BOARD_READ', 'EPIC_READ', 'TICKET_READ', 'COMMENT_READ', 'ATTACHMENT_READ',
              'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_DELETE', 'CARD_MOVE',
              'EPIC_CREATE', 'EPIC_UPDATE', 'EPIC_DELETE',
              'COMMENT_CREATE', 'COMMENT_UPDATE',
              'ATTACHMENT_CREATE', 'ATTACHMENT_DELETE');

-- ADMIN: MEMBER + Board C/U/D, Kommentar löschen (Moderation), Mitgliederverwaltung.
INSERT INTO role_permission (role, permission_id)
SELECT 'ADMIN', id FROM permission
WHERE key IN ('BOARD_READ', 'EPIC_READ', 'TICKET_READ', 'COMMENT_READ', 'ATTACHMENT_READ',
              'TICKET_CREATE', 'TICKET_UPDATE', 'TICKET_DELETE', 'CARD_MOVE',
              'EPIC_CREATE', 'EPIC_UPDATE', 'EPIC_DELETE',
              'COMMENT_CREATE', 'COMMENT_UPDATE',
              'ATTACHMENT_CREATE', 'ATTACHMENT_DELETE',
              'BOARD_CREATE', 'BOARD_UPDATE', 'BOARD_DELETE',
              'COMMENT_DELETE', 'MEMBER_INVITE', 'MEMBER_REMOVE');

-- OWNER: alle Rechte.
INSERT INTO role_permission (role, permission_id)
SELECT 'OWNER', id FROM permission;
