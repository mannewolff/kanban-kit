-- Ideen-Pool-Rückbau: board-loser Kartenzustand wird in der Datenbank ausgeschlossen (Issue #1205)
-- ---------------------------------------------------------------------------
-- Seit V18 erlaubt die Datenbank board-lose Karten: board_id, column_id und number sind nullable,
-- dazu gibt es idea_stored, target_board_id und die Prüfbedingung ck_card_board_consistency.
-- Nachdem die Anwendung den Pool nicht mehr kennt (#1204), schließt dieser Schritt den Zustand auch
-- auf Datenbankebene wieder aus. Bleibt er in der Datenbank möglich, bleibt er erreichbar — und die
-- Guards im Modell müssten stehen bleiben.
--
-- card_activity wird bewusst nicht angefasst (Plan #1199, E17): Ein Umschreiben der Zeilen wäre eine
-- Änderung an Bestandsdaten. Zeilen mit IDEA_STORED und PROMOTED bleiben als Historie lesbar; der
-- Riegel unten sieht sie nicht, weil er allein die Tabelle card prüft.
--
-- project_id, sein Index und der Trigger trg_card_fill_project_id bleiben unberührt — die
-- Rechteprüfung arbeitet projektbasiert.

-- Riegel (E5): Stehen wider Erwarten noch board-lose oder als Idee gekennzeichnete Karten, bricht
-- die Migration mit verständlicher Meldung ab. Kein stilles Verschieben, kein Löschen: Solche Karten
-- sind reale Inhalte, über deren Ziel nur ein Mensch entscheiden kann. Postgres führt Migrationen
-- transaktional aus, der Abbruch lässt den Bestand also unverändert zurück.
DO $$
DECLARE
    betroffen bigint;
BEGIN
    SELECT count(*) INTO betroffen
    FROM card
    WHERE board_id IS NULL OR idea_stored = true;

    IF betroffen > 0 THEN
        RAISE EXCEPTION
            'V44: % Karte(n) sind noch board-los oder als Idee gekennzeichnet. Die Migration '
            'bricht ab und hat nichts geändert. Diesen Karten muss zuerst ein Board, eine Spalte '
            'und eine Nummer zugewiesen werden (SELECT id, title FROM card WHERE board_id IS NULL '
            'OR idea_stored = true), danach kann die Migration erneut laufen.', betroffen;
    END IF;
END $$;

-- active_position ohne den idea_stored-Term neu erzeugen, nach dem Muster aus V15 und V16. Der
-- Schritt steht vor dem Drop von idea_stored: Die generierte Spalte hängt an ihr, ohne diesen Umbau
-- wäre der Drop nur mit CASCADE möglich — und nähme active_position mit.
ALTER TABLE card DROP CONSTRAINT uq_card_active_position;
ALTER TABLE card DROP COLUMN active_position;
ALTER TABLE card
    ADD COLUMN active_position integer
    GENERATED ALWAYS AS (
        CASE WHEN archived OR type = 'EPIC' OR deleted_at IS NOT NULL
             THEN NULL ELSE position_in_column END
    ) STORED;
ALTER TABLE card ADD CONSTRAINT uq_card_active_position UNIQUE (board_id, column_id, active_position);

-- Die Konsistenzbedingung aus V18 hat keinen Gegenstand mehr: board_id ist gleich wieder Pflicht,
-- und damit sind column_id und number es ohnehin.
ALTER TABLE card DROP CONSTRAINT ck_card_board_consistency;

-- Die beiden Pool-Spalten fallen. target_board_id hielt den Board-Hinweis einer board-losen Idee;
-- ohne Pool gibt es nichts mehr zu notieren.
ALTER TABLE card DROP COLUMN idea_stored;
ALTER TABLE card DROP COLUMN target_board_id;

-- Board-Bindung ist wieder Pflicht (Gegenstück zu V18).
ALTER TABLE card ALTER COLUMN board_id SET NOT NULL;
ALTER TABLE card ALTER COLUMN column_id SET NOT NULL;
ALTER TABLE card ALTER COLUMN number SET NOT NULL;
