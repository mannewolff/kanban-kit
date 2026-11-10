-- ---------------------------------------------------------------------------
-- Person am Label-Ereignis (Issue #2110, Plan #2108 E5; Fachliche Quelle #2101)
--
-- actor_user_id haelt fest, wer ein Label gesetzt hat: bei Herkunft SESSION der Mensch, bei
-- Herkunft TOKEN der Inhaber des Zugangs, als Mensch gilt die Person beim Lesen nur bei SESSION.
-- Gleiche Regel wie card_activity.actor_user_id: Wird der Nutzer geloescht, bleibt das Ereignis
-- stehen und die Person wird leer. Der Name wird beim Lesen aufgeloest, kein Schnappschuss.
--
-- Kein Backfill: Fuer aeltere Ereignisse ist die Person nicht rekonstruierbar, sie erscheinen in
-- der Entscheidungskette als "nicht erfasst".
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V72 aus
-- flyway_schema_history loeschen):
--   ALTER TABLE card_label_event DROP COLUMN actor_user_id;
-- ---------------------------------------------------------------------------
ALTER TABLE card_label_event
    ADD COLUMN actor_user_id bigint REFERENCES app_user (id) ON DELETE SET NULL;
