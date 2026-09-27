-- ---------------------------------------------------------------------------
-- Sonderregeln der Rechte-Matrix bekommen echte Schlüssel (Issue #1165)
--
-- Drei Regeln hingen bisher an einer Projekt-Rolle, ohne in `permission` zu stehen:
-- die Auswertung der Läufe (lesen bzw. Protokoll hineingeben) und das Verschieben
-- einer Karte in ein anderes Projekt. Weil `GET /api/roles/matrix` ausschließlich
-- diese Tabelle liefert, fehlten sie in der Ansicht „Rollen und Rechte" ganz — wer
-- sie als vollständig las, hielt vergebene Rechte für nicht vergeben.
--
-- Die Regel selbst ändert sich NICHT: Alle drei Wege verlangen heute die Projekt-Rolle
-- OWNER, und genau diese Zuordnung wird hier geseedet. Keine Zeile für VIEWER, MEMBER
-- oder ADMIN. Die Durchsetzung bleibt ebenfalls unverändert (`requireNightRunAccess`,
-- `requireOwner` in beiden Projekten); die Schlüssel beschreiben die Regel und ersetzen
-- die Prüfung nicht. Ihr Zweck ist die Schaltbarkeit für künftige, frei konfigurierbare
-- Rollen.
-- ---------------------------------------------------------------------------

INSERT INTO permission (key, description) VALUES
    ('CARD_MOVE_PROJECT',  'Karte in ein anderes Projekt verschieben'),
    ('NIGHT_RUN_READ',     'Auswertung der Läufe lesen'),
    ('NIGHT_RUN_SUBMIT',   'Protokoll eines Laufs hineingeben');

INSERT INTO role_permission (role, permission_id)
SELECT 'OWNER', id FROM permission
WHERE key IN ('CARD_MOVE_PROJECT', 'NIGHT_RUN_READ', 'NIGHT_RUN_SUBMIT');
