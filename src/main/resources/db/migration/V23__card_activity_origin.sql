-- Aktivitäts-Herkunft (Issue #517, Ideen-Quelle #494)
-- ---------------------------------------------------------------------------
-- Woher kam eine Aktion: interaktive Session oder Access-Token — und bei Token welches.
-- Zwei Verlässlichkeitsklassen: origin/token_name prüft der Server selbst (Authority
-- AUTH_SESSION/AUTH_PAT bzw. Token-Bindung), agent ist eine Client-Selbstauskunft per
-- X-Agent-Model-Header. token_name ist bewusst denormalisiert (kein FK auf access_token):
-- Tokens werden widerrufen und gelöscht, die Historie muss lesbar bleiben.
-- Kein Backfill: Für Alt-Einträge ist die Herkunft nicht rekonstruierbar, sie bleiben NULL
-- und werden unverändert angezeigt (Entscheidung in #494: "Geht dann nur für neue Einträge").

ALTER TABLE card_activity ADD COLUMN origin     varchar(10);
ALTER TABLE card_activity ADD COLUMN token_name varchar(100);
ALTER TABLE card_activity ADD COLUMN agent      varchar(100);
