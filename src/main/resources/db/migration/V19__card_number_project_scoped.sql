-- Kartennummern projektweit statt board-lokal
-- ---------------------------------------------------------------------------
-- Bisher ist die Nummer nur pro Board eindeutig (uq_card_number (board_id, number)); zwei Boards
-- eines Projekts haben beide #1. Damit Querverweise (#N) beim Verschieben zwischen Boards desselben
-- Projekts stabil bleiben, wird die Nummer projektweit eindeutig: uq_card_number (project_id, number).
--
-- Minimal-invasiv: Nur echte Kollisionen (mehrere board-gebundene Karten eines Projekts mit derselben
-- number) werden umnummeriert. Pro Kollisionsgruppe behält die Karte auf dem zuerst erstellten Board
-- (Tiebreak: kleinste card.id) ihre Nummer; die übrigen bekommen frische Projekt-Nummern
-- (max(project) + fortlaufend). Board-lose Pool-Ideen (number IS NULL) bleiben außen vor — in Postgres
-- kollidieren NULL-Werte im Unique-Constraint ohnehin nicht.
--
-- Strukturierte Abhängigkeits-Verweise (card_dependency.depends_on_card_number) sind board-lokal:
-- ein Verweis #N einer Karte auf Board B meint die Karte mit number N auf demselben Board B. Wird
-- eine solche Zielkarte umnummeriert, werden die Verweise der Karten IHRES Boards mitgezogen. Ein
-- Verweis #N im Markdown-Fließtext kann nicht sicher migriert werden (nur die strukturierten Deps);
-- betroffen sind ausschließlich die wenigen tatsächlich umnummerierten Karten.

DO $$
DECLARE
    rec   RECORD;
    fresh integer;
BEGIN
    FOR rec IN
        WITH ranked AS (
            SELECT c.id,
                   c.project_id,
                   c.number,
                   c.board_id,
                   row_number() OVER (
                       PARTITION BY c.project_id, c.number
                       ORDER BY b.created_at, c.id
                   ) AS rn
            FROM card c
            JOIN board b ON b.id = c.board_id
            WHERE c.number IS NOT NULL
        )
        SELECT id, project_id, number AS old_number, board_id
        FROM ranked
        WHERE rn > 1                 -- rn = 1 ist der Behalter je Kollisionsgruppe
        ORDER BY project_id, old_number, id
    LOOP
        SELECT coalesce(max(number), 0) + 1
        INTO fresh
        FROM card
        WHERE project_id = rec.project_id AND number IS NOT NULL;

        -- Board-lokale Verweise auf die umzunummerierende Karte mitziehen (nur Karten IHRES Boards;
        -- auf einem Board gibt es dank uq (board_id, number) genau eine Karte mit old_number).
        UPDATE card_dependency d
        SET depends_on_card_number = fresh
        WHERE d.depends_on_card_number = rec.old_number
          AND d.card_id IN (SELECT id FROM card WHERE board_id = rec.board_id);

        UPDATE card SET number = fresh WHERE id = rec.id;

        RAISE NOTICE 'V19: card % (project %, board %) umnummeriert: % -> %',
            rec.id, rec.project_id, rec.board_id, rec.old_number, fresh;
    END LOOP;
END $$;

-- Eindeutigkeit von board-scoped auf projekt-scoped umstellen.
ALTER TABLE card DROP CONSTRAINT uq_card_number;
ALTER TABLE card ADD CONSTRAINT uq_card_number UNIQUE (project_id, number);
