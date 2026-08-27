-- Herkunft einer Karte: aus welchem Dokument ist sie entstanden? (Vorhaben-Cluster, Plan #600)
-- ---------------------------------------------------------------------------
-- Der 9-Schritt-Prozess erzeugt eine Kette: fachliche Anforderung -> Plandokument ->
-- Arbeitspaket. Bisher steht sie nur als Text im Beschreibungsfeld; diese Spalte macht
-- sie zu Daten, damit die Vorhaben-Ansicht den Baum berechnen kann.
--
-- Gespeichert wird eine Fremdschluessel-ID und NICHT die projektweite Kartennummer.
-- Der Grund liegt in der Nummernvergabe: Beim Verschieben einer Karte in ein anderes
-- Projekt bekommt sie dort eine NEUE Nummer (CardService.doTransfer). Eine gespeicherte
-- Nummer zeigte danach im Projekt des Kindes auf eine FREMDE Karte — stille
-- Datenverfaelschung ohne sichtbaren Bruch. Die ID bleibt stabil.
--
-- ON DELETE SET NULL wie beim Vorbild parent_id (V2): Das endgueltige Loeschen eines
-- Vorfahren raeumt die Verweise seiner Kinder selbst auf.
--
-- Rein additiv: kein Backfill, keine Datenaenderung, und die Spalte geht in keine
-- generierte Spalte ein — active_position und chk_card_type bleiben unberuehrt.

ALTER TABLE card ADD COLUMN derived_from_card_id bigint;

ALTER TABLE card ADD CONSTRAINT fk_card_derived_from
    FOREIGN KEY (derived_from_card_id) REFERENCES card (id) ON DELETE SET NULL;

CREATE INDEX idx_card_derived_from ON card (derived_from_card_id);
