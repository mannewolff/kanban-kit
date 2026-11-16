-- ---------------------------------------------------------------------------
-- Gespiegelte Kommentare eines fremden Boards (Issue #2162, Plan #2152 A1, A11, A19; Fachliche
-- Quelle #2150)
--
-- extern_kennung ist die Kennung des Kommentars im fremden System (bei GitLab die Note-ID). Ueber
-- sie findet der Spiegel einen schon uebernommenen Kommentar wieder, um einen in GitLab
-- geaenderten Text zu uebernehmen oder eine dort geloeschte Meldung des Kits zu kennzeichnen. Je
-- Karte hoechstens einmal; eigene Kommentare tragen keine (NULL).
--
-- Kein Backfill: Vor dem Spiegel gab es keine gespiegelten Kommentare.
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V76 aus
-- flyway_schema_history loeschen):
--   DROP INDEX uq_comment_extern_kennung;
--   ALTER TABLE comment DROP COLUMN extern_kennung;
-- ---------------------------------------------------------------------------
ALTER TABLE comment ADD COLUMN extern_kennung varchar(255);

CREATE UNIQUE INDEX uq_comment_extern_kennung
    ON comment (card_id, extern_kennung)
    WHERE extern_kennung IS NOT NULL;
