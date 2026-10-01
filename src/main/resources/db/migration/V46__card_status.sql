-- ---------------------------------------------------------------------------
-- Arbeitspakete tragen einen eigenen Status (Issue #1298, Plan #1294 E1, E19)
--
-- Der Prozesszustand eines Arbeitspakets wird eine eigene Eigenschaft der Karte statt
-- einer Nebenwirkung der Spalte. NULL heißt „kein eigener Status" (E2): Vorhaben und
-- die drei Dokumentarten [Idee], [Fachlich], [Plan] behalten die Ableitung aus der
-- Spalte. Der gespeicherte Wert ist der Konstantenname von CardStatus.
--
-- Backfill in einem Zug: Arbeitspakete bekommen den kanonischen Status ihrer Spalte
-- (dieselbe Regel wie Arbeitspaket.statusVonSpalte und KanbanCompatService.canonicalKey),
-- in eigenen Spalten BACKLOG. Der Präfixausschluss nutzt dieselbe Regex wie
-- Arbeitspaket.istArbeitspaket — \s* statt btrim, weil btrim nur Leerzeichen entfernt
-- und ein Titel mit führendem Tabulator sonst hier ein Arbeitspaket, zur Laufzeit aber
-- ein Dokument wäre.
-- ---------------------------------------------------------------------------

ALTER TABLE card ADD COLUMN status varchar(20);

ALTER TABLE card ADD CONSTRAINT chk_card_status
    CHECK (status IS NULL OR status IN ('BACKLOG', 'READY', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'));

UPDATE card c
SET status = CASE regexp_replace(lower(bc.name), '[^a-z]', '', 'g')
                 WHEN 'backlog'    THEN 'BACKLOG'
                 WHEN 'ready'      THEN 'READY'
                 WHEN 'inprogress' THEN 'IN_PROGRESS'
                 WHEN 'inreview'   THEN 'IN_REVIEW'
                 WHEN 'done'       THEN 'DONE'
                 ELSE 'BACKLOG'
             END
FROM board_column bc
WHERE bc.id = c.column_id
  AND c.type = 'CARD'
  AND c.title !~* '^\s*\[(idee|fachlich|plan)\]';
