-- ---------------------------------------------------------------------------
-- Stand des zuletzt gelungenen Abgleichs eines Boards in GitLab (Issue #2222, Plan #2221 E4,
-- fachlich #2203 Kriterien 2 und 8)
--
-- stand_am ist der Zeitpunkt, von dem der gespiegelte Stand eines fremden Boards ist, und die
-- Quelle des Board-Hinweises für jedes Mitglied. Gesetzt wird die Spalte nur von einem gelungenen
-- Abgleich, auf den Zeitpunkt seines Anspruchs; letzter_abgleich_am taugt dafür nicht, weil schon
-- der Anspruch eines Laufs es auf "jetzt" setzt. NULL heißt: noch kein Stand von dort.
--
-- Übernommen wird letzter_abgleich_am nur, wo letzter_vollabgleich_am gesetzt ist: Der erste
-- gelungene Abgleich ist immer ein Vollabgleich, dort ist also mindestens einer gelungen. Eine
-- Verbindung ohne gelungenen Abgleich bleibt bei "noch kein Stand".
-- ---------------------------------------------------------------------------

ALTER TABLE fremdboard_verbindung ADD COLUMN stand_am timestamptz;

UPDATE fremdboard_verbindung SET stand_am = letzter_abgleich_am
 WHERE letzter_vollabgleich_am IS NOT NULL;
