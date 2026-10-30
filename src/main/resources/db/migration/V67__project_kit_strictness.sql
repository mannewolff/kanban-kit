-- ---------------------------------------------------------------------------
-- Strenge-Stufe des Kits je Projekt (Issue #2014, aus Idee #2007)
--
-- Das claude-workflow-kit meldet an jeder Anfrage ans Board die Strenge-Stufe, die das Projekt
-- im Kit gewaehlt hat, als Header X-Kit-Strictness (Vertrag V1 aus Plan #2102 des Kits). Hier
-- steht der zuletzt gemeldete Wert; NULL heisst "nie gemeldet". Ein Verlauf ist ausdruecklich
-- nicht gewollt, deshalb eine Spalte statt einer eigenen Tabelle.
--
-- Geschrieben wird die Spalte wie monthly_budget_usd allein ueber einen gezielten Direkt-Update
-- (ProjectRepository.setKitStrictness), nie ueber den allgemeinen Schreibweg.
-- ---------------------------------------------------------------------------
ALTER TABLE project
    ADD COLUMN kit_strictness VARCHAR(16) NULL
        CHECK (kit_strictness IN ('light', 'normal', 'strict'));
