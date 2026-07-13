-- Karten-Zuweisung an ein oder mehrere Mitglieder (Issue #174)
-- ---------------------------------------------------------------------------
-- Reine Zuordnung ohne eigene ID (zusammengesetzter Schlüssel), analog card_dependency.
-- Zugewiesen werden dürfen nur Projektmitglieder (fachlich in der Anwendungsschicht geprüft).

CREATE TABLE card_assignee (
    card_id bigint NOT NULL REFERENCES card (id) ON DELETE CASCADE,
    user_id bigint NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    CONSTRAINT pk_card_assignee PRIMARY KEY (card_id, user_id)
);
CREATE INDEX idx_card_assignee_user ON card_assignee (user_id);
