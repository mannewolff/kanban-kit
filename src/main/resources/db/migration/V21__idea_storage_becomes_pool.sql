-- Ideen-Speicher wird board-los: bestehende Geisterkarten in den Pool holen (Issue #433)
-- ---------------------------------------------------------------------------
-- "In den Ideen-Speicher" legte eine Karte bisher nur als idea_stored=true an — Board, Spalte und
-- Nummer blieben unangetastet. Dieser Zwischenzustand ist in keiner Ansicht sichtbar (Board-,
-- Listen- und Planen-Ansicht blenden idea_stored-Karten aus, der Pool zeigt nur board-lose Ideen)
-- und hat reale Karten unauffindbar gemacht (Issue #428). CardService#moveToIdeaStorage legt eine
-- Karte ab sofort direkt board-los im Pool an; dieser Schritt holt den Bestand nach.
--
-- Jede vorhandene Karte in genau diesem unsichtbaren Zustand wird board-los, das bisherige Board
-- als target_board_id notiert (Vorauswahl beim Einplanen), Nummer und Position bleiben unverändert
-- (keine Neuvergabe, Rückverweise über #N bleiben gültig). ck_card_board_consistency bleibt
-- gültig: board_id wird NULL, der Constraint verlangt dann nichts weiter.
UPDATE card
SET target_board_id = board_id,
    board_id = NULL,
    column_id = NULL
WHERE idea_stored = true
  AND board_id IS NOT NULL;
