-- ---------------------------------------------------------------------------
-- Das Passwort entfaellt (Issue #1817, Plan #1801 A11, A14, fachlich #1781 AK 2, 11, 13)
--
-- Angemeldet wird nur noch per Einmalcode (V57). Gespeicherte Passwort-Hashes werden geloescht,
-- mit ihnen die Token-Tabellen von Passwort-Reset und E-Mail-Verifikation, deren Ablaeufe
-- entfallen sind (Issue #1816).
--
-- Offene Outbox-Eintraege der weggefallenen Mailtypen werden geloescht (A14): Ihre Handler gibt es
-- nicht mehr; offen gelassen wuerden sie bei jedem Durchlauf als Fehlversuch verbucht und
-- enthielten Links, die nichts mehr bewirken. Erledigte und gescheiterte Eintraege bleiben fuer
-- die Nachvollziehbarkeit, bis die Outbox-Aufbewahrung sie raeumt.
--
-- Alle laufenden Sitzungen enden einmal ueber die Sitzungs-Generation (A11, V31): Jedes Konto
-- meldet sich danach einmal per Code an. Zugriffstokens haengen nicht an der Generation und
-- bleiben gueltig (AK 11).
--
-- Nicht additiv: Eine aeltere App-Version, die password_hash noch mappt, startet gegen dieses
-- Schema nicht mehr. Ein Rueckweg stellt die Hashes nicht wieder her — sie sind geloescht.
-- ---------------------------------------------------------------------------

ALTER TABLE app_user DROP COLUMN password_hash;

DROP TABLE password_reset_token;
DROP TABLE email_verification_token;

DELETE FROM outbox_entry
 WHERE status = 'PENDING'
   AND event_type IN ('mail.password-reset', 'mail.verification');

UPDATE app_user SET session_generation = session_generation + 1;
