-- Der Grund, warum ein Nachtlauf nichts abgearbeitet hat (Issue #1068, Plan #1067, fachlich #1060).
--
-- Ein beendeter Lauf ohne Arbeit erschien bisher mit gruenem Melder wie ein gelungener Lauf. Der
-- Grund dafuer lag hier: night_run fuehrte keine Auskunft darueber, dass nichts abzuarbeiten war,
-- und ohne sie kann keine Anzeige sie zeigen.
--
-- Eine eigene Spalte statt einer Ableitung aus processed_count = 0 in der Anzeige (Plan #1067, E1):
-- Eine Ableitung wirkte rueckwirkend auf jeden gespeicherten Lauf und faerbte Bestandslaeufe um.
-- Deshalb kein Backfill -- NULL heisst entweder "vor dieser Umstellung eingeliefert" oder "der
-- Lauf hat gearbeitet". Beide Faelle sind kein Befund, und genau das soll NULL sagen.
--
-- varchar(300) wie night_run_item.title (V29__night_run.sql:51): Der Wert ist ein Satz fuer die
-- Anzeige, kein Protokoll. Der Auszug fuer lange Texte heisst unparsed_sample und hat seine eigene
-- Spalte.

ALTER TABLE night_run
    ADD COLUMN no_work_reason varchar(300);
