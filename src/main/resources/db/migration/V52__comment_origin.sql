-- ---------------------------------------------------------------------------
-- Herkunft an Kommentaren (Issue #1620, Plan #1617, fachlich #1612)
--
-- Wie card_activity (V23): origin und token_name prüft der Server selbst (Authority bzw.
-- Token-Bindung), agent ist eine Client-Selbstauskunft per X-Agent-Model-Header. Gestempelt
-- wird beim Anlegen aus derselben Quelle wie der Verlauf (ActorContext); Bearbeiten lässt
-- die Herkunft unverändert.
--
-- token_name folgt seiner Quelle kanban_access_token.display_name varchar(120), nicht
-- V23 mit varchar(100) — dieselbe Begründung wie in V32: Ein Zugangsname, der dort passt,
-- muss auch hier passen, sonst scheitert ausgerechnet der Kommentar mit dem längsten Namen.
-- agent bleibt bei varchar(100), der Header wird auf 100 Zeichen gekappt.
--
-- Kein Backfill (A5, E4): Für Alt-Kommentare ist die Herkunft nicht rekonstruierbar, sie
-- bleiben NULL — unbekannt, nicht geraten (A6).
-- ---------------------------------------------------------------------------

ALTER TABLE comment ADD COLUMN origin     varchar(10);
ALTER TABLE comment ADD COLUMN token_name varchar(120);
ALTER TABLE comment ADD COLUMN agent      varchar(100);

ALTER TABLE comment ADD CONSTRAINT chk_comment_origin
    CHECK (origin IS NULL OR origin IN ('SESSION', 'TOKEN'));
