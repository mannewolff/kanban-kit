-- Anforderungskarte eines Vorhabens: aus welcher fachlichen Anforderung ist es eroeffnet worden?
-- ---------------------------------------------------------------------------
-- Drei Relationen liegen jetzt nebeneinander, und sie beantworten drei verschiedene Fragen:
--
--   parent_id            "wozu gehoert diese Karte"      flach, eine Ebene, an der CARD
--   derived_from_card_id "woraus ist sie entstanden"     beliebig tief, an jeder Karte
--   requirement_card_id  "welche Anforderung traegt      genau eine, von Hand gelegt,
--                         dieses Vorhaben"                nur am EPIC
--
-- Warum gespeichert und nicht gerechnet (Plan #637, E1): Die Zugehoerigkeit ALLER Karten
-- wird abgeleitet (Plan #631), weil sie ableitbar ist. Welche der zugeordneten Karten die
-- Anforderung ist, ist es nicht — manuelles Zuordnen bleibt moeglich, ein Vorhaben kann
-- also mehrere Wurzeln haben, und keine Rechnung sagt, welche den Vorgang eroeffnet hat.
--
-- Gespeichert wird die ID und NICHT die projektweite Kartennummer. Begruendung wie in V26:
-- Beim Verschieben in ein anderes Projekt vergibt CardService.doTransfer eine neue Nummer;
-- eine gespeicherte Nummer zeigte danach auf eine fremde Karte. Die ID bleibt stabil.
--
-- ON DELETE SET NULL wie bei parent_id (V2) und derived_from_card_id (V26): Das endgueltige
-- Loeschen der Anforderungskarte raeumt den Verweis selbst auf, statt das Loeschen zu
-- verhindern. Ein Vorhaben ohne Anforderung ist ein gueltiger Zustand (Gruppierung ohne
-- Herkunftskette).
--
-- Rein additiv: kein Backfill, keine Datenaenderung, und die Spalte geht in keine generierte
-- Spalte ein — active_position und chk_card_type bleiben unberuehrt.

ALTER TABLE card ADD COLUMN requirement_card_id bigint;

ALTER TABLE card ADD CONSTRAINT fk_card_requirement
    FOREIGN KEY (requirement_card_id) REFERENCES card (id) ON DELETE SET NULL;

CREATE INDEX idx_card_requirement ON card (requirement_card_id);
