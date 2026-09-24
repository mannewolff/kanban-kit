-- Ein haengender Lauf, den ein Plattform-Admin von Hand als beendet gekennzeichnet hat (Issue
-- #1197).
--
-- Der Plattform-Leitstand zeigt Laeufe als aktiv, deren Prozess laengst weg ist: Sie melden sich
-- nie ab und bleiben damit fuer immer "laeuft". Warum sie sich nicht abmelden, ist ein eigenes
-- Problem; diese Spalten nehmen nur die Kennzeichnung entgegen, mit der ein Admin so eine Zeile
-- wegraeumt. Den Prozess beruehrt das nicht -- er ist ohnehin weg.
--
-- Zwei Spalten am Lauf und keine eigene Tabelle wie night_run_disruption_ack: Die Quittung sagt
-- etwas ueber die *Sichtung* einer Stoerung und laesst den Ausgang des Laufs unberuehrt; die
-- Kennzeichnung sagt etwas ueber den *Lauf selbst* -- sie ersetzt die ausgebliebene Abmeldung und
-- entscheidet damit seinen Ausgang (Verdict CLOSED). Was den Ausgang bestimmt, gehoert an den Lauf.
--
-- NULL heisst "nicht von Hand beendet" -- der Normalfall jedes Laufs, deshalb kein Backfill und
-- kein Default.
--
-- ON DELETE SET NULL wie bei night_run_disruption_ack.acknowledged_by: Wer gekennzeichnet hat, ist
-- eine Auskunft ueber die Kennzeichnung; ein geloeschtes Konto darf sie nicht mitnehmen.

ALTER TABLE night_run
    ADD COLUMN closed_at timestamptz(6),
    ADD COLUMN closed_by bigint REFERENCES app_user (id) ON DELETE SET NULL;
