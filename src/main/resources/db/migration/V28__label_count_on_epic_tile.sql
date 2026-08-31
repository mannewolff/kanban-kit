-- Label-Eigenschaft "auf der Vorhaben-Kachel zaehlen" (Issue #659)
-- ---------------------------------------------------------------------------
-- Die Vorhaben-Kachel zeigt die Zustaende ihrer Karten als gezaehlte Marken. WELCHE Labels
-- gezaehlt werden, entscheidet der Betreiber je Board (Entscheidung PO, #656 Frage 5) --
-- die Anforderung schreibt keine Namen fest, weil Labels freie Daten je Board sind und ein
-- fremder Betreiber andere hat. Eine Sortierung oder Zaehlung, die auf einem bestimmten
-- Labelnamen rechnete, braeche auf jedem fremden Board.
--
-- Die Einstellung sitzt am Label und nicht am Board (Plan #657, E1): LabelManagerDialog ist
-- eine fertige Verwaltungsoberflaeche, waehrend board heute keine einzige Einstellung traegt
-- -- ein Board-Einstellungsbereich muesste erst erfunden werden.
--
-- DEFAULT false, also "nicht zaehlen" (Plan #657, E2). Eine Vorgabe true liesse auf jedem
-- Bestandsboard sofort alle Labels auf den Kacheln erscheinen: eine stille Aenderung durch
-- eine Migration, die niemand angefordert hat.
--
-- Rein additiv: kein Backfill, keine Datenaenderung, keine generierte Spalte betroffen.

ALTER TABLE label ADD COLUMN count_on_epic_tile boolean NOT NULL DEFAULT false;
