-- Herkunft, Vollstaendigkeit und Verbrauch eines Nachtlaufs (Issue #944, Plan #943).
--
-- Bis hierher wusste night_run weder, woher ein Lauf kam, noch ob er vollstaendig ist, noch was
-- er verbraucht hat. Ohne diese Spalten gibt es nichts, worin eine maschinelle Meldung landen
-- koennte.
--
-- Kein Backfill der Verbrauchswerte: Fuer Altlaeufe sind sie nicht rekonstruierbar. NULL heisst
-- hier "nicht gemessen" und nie Null -- eine 0 behauptete, der Lauf habe nichts verbraucht.
ALTER TABLE night_run
    ADD COLUMN origin              varchar(10) NOT NULL DEFAULT 'UPLOAD',
    ADD COLUMN token_name          varchar(120),
    ADD COLUMN complete            boolean     NOT NULL DEFAULT true,
    ADD COLUMN updated_at          timestamptz(6),
    ADD COLUMN cost_usd            numeric(12,6),
    ADD COLUMN input_tokens        bigint,
    ADD COLUMN output_tokens       bigint,
    ADD COLUMN cached_input_tokens bigint;

-- Die Laenge von token_name folgt ihrer Quelle kanban_access_token.display_name varchar(120) und
-- ausdruecklich NICHT V23__card_activity_origin.sql, das mit varchar(100) hinter seiner Quelle
-- zurueckbleibt: Ein Name, der dort passt, muss auch hier passen, sonst schlaegt die Einlieferung
-- ausgerechnet bei den laengsten Tokennamen fehl.
ALTER TABLE night_run
    ADD CONSTRAINT ck_night_run_origin CHECK (origin IN ('UPLOAD', 'TOKEN'));

-- Dieselben vier Mengen je Arbeitspaket: Die Auswertung braucht sie an der Karte, nicht nur
-- als Lauf-Summe.
ALTER TABLE night_run_item
    ADD COLUMN cost_usd            numeric(12,6),
    ADD COLUMN input_tokens        bigint,
    ADD COLUMN output_tokens       bigint,
    ADD COLUMN cached_input_tokens bigint;
