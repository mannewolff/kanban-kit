-- Idempotenter Ingest über externen Schlüssel (Issue #534)
-- ---------------------------------------------------------------------------
-- Automatiken (z. B. der SonarCloud-Sync) liefern Findings wiederholt an den
-- kanbancompat-Ingest. Der externe Schlüssel (z. B. "sonar:<issue-key>") macht die
-- Anlage idempotent: existiert im Projekt bereits eine Karte mit dem Schlüssel —
-- gleich ob Pool, Board, archiviert oder Papierkorb —, wird nichts neu angelegt.
-- Erst endgültiges Löschen (purge) entfernt die Zeile und gibt den Schlüssel frei.
-- Partieller Unique-Index: projekt-scoped, normale Karten (NULL) bleiben unberührt.

ALTER TABLE card ADD COLUMN external_key varchar(100);
CREATE UNIQUE INDEX uq_card_project_external_key
    ON card (project_id, external_key)
    WHERE external_key IS NOT NULL;
