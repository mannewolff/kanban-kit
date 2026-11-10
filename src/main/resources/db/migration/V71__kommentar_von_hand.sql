-- ---------------------------------------------------------------------------
-- Aenderung von Hand an Meldungen des Kits (Issue #2109, Plan #2108 E4; Fachliche Quelle #2101)
--
-- von_hand_geaendert_am haelt die letzte Aenderung von Hand an einem Kommentar fest, der mit
-- Herkunft TOKEN angelegt wurde. von_hand_kopfzeile ist die erste nichtleere Zeile der Fassung
-- des Kits, damit das Glied der Entscheidungskette auch dann zugeordnet bleibt, wenn die
-- Aenderung den Kopf entfernt. Ersetzt danach ein Token den Text, werden beide Spalten geleert.
--
-- Kein Backfill: Fuer aeltere Kommentare ist eine Aenderung von Hand nicht rekonstruierbar.
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V71 aus
-- flyway_schema_history loeschen):
--   ALTER TABLE comment DROP COLUMN von_hand_geaendert_am, DROP COLUMN von_hand_kopfzeile;
-- ---------------------------------------------------------------------------
ALTER TABLE comment
    ADD COLUMN von_hand_geaendert_am timestamptz,
    ADD COLUMN von_hand_kopfzeile    text;
