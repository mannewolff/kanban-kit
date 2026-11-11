-- ---------------------------------------------------------------------------
-- Einstellung des Kits zur Pruefung vor Ready je Projekt (Issue #2112, Plan #2108 E18)
--
-- Das claude-workflow-kit meldet issueReview.requiredBeforeReady im Header
-- X-Kit-Review-Before-Ready, auf demselben Weg wie X-Kit-Strictness. Die Entscheidungskette
-- braucht den Wert, um am Glied Issue-Review "nicht vorgesehen" von "nicht erfasst" zu
-- unterscheiden.
--
-- kit_review_before_ready: zuletzt gemeldeter Wert; NULL heisst "nie gemeldet".
-- kit_review_before_ready_changed_at: wann ein schon gemeldeter Wert zuletzt wechselte; NULL
-- heisst "seit der ersten Meldung unveraendert". Die erste Meldung ist keine Aenderung.
--
-- Geschrieben werden beide Spalten allein ueber den gezielten Direkt-Update
-- ProjectRepository.setKitReviewBeforeReady, nie ueber den allgemeinen Schreibweg.
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V74 aus
-- flyway_schema_history loeschen):
--   ALTER TABLE project DROP COLUMN kit_review_before_ready_changed_at;
--   ALTER TABLE project DROP COLUMN kit_review_before_ready;
-- ---------------------------------------------------------------------------
ALTER TABLE project
    ADD COLUMN kit_review_before_ready boolean,
    ADD COLUMN kit_review_before_ready_changed_at timestamptz;
