-- ---------------------------------------------------------------------------
-- Festgehaltene Kosten je Vorhaben (Issue #2004, Plan #1990 E8 bis E10, Fachliche Quelle #1954 AK 8)
--
-- Eine Zeile je Vorhaben. Ein geplanter Job (VorhabenKostenJob) schreibt nur Uebergaenge:
--   anforderung_*  die Voraussage aus der Anforderung, beim ersten Paket festgehalten;
--   pakete_*       die Voraussage aus den Paketen, sobald nichts mehr ungeschnitten offen ist;
--   abgeschlossen_at, tatsaechlich_*, kosten_karten  der letzte Abschluss (E10) mit den
--                  tatsaechlichen Kosten bis dahin und den Kostenkarten, ueber die spaete
--                  Meldungen nachgerechnet werden (E9).
-- Ein *_stand von NULL heisst "noch nicht festgehalten", KEINE heisst "keine Voraussage"; dann
-- bleiben Art und Betraege NULL. Die Betraege tragen dieselbe Genauigkeit wie cost_usd (V32).
--
-- vorhaben_kosten_einfuehrung vermerkt je Projekt den ersten Lauf des Jobs (E8): Bei ihm ergeben
-- schon vergangene Zeitpunkte "keine Voraussage".
--
-- Lebenslauf: Mit der Vorhaben-Karte bzw. dem Projekt entfallen die Zeilen (ON DELETE CASCADE).
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V68 aus
-- flyway_schema_history loeschen):
--   DROP TABLE vorhaben_kosten_einfuehrung;
--   DROP TABLE vorhaben_kosten;
-- ---------------------------------------------------------------------------

CREATE TABLE vorhaben_kosten (
    epic_id                      bigint        PRIMARY KEY REFERENCES card (id) ON DELETE CASCADE,
    project_id                   bigint        NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    anforderung_stand            varchar(16)   CHECK (anforderung_stand IN ('BEZIFFERT', 'KEINE')),
    anforderung_art              varchar(32),
    anforderung_typisch_usd      numeric(12,6),
    anforderung_unten_usd        numeric(12,6),
    anforderung_oben_usd         numeric(12,6),
    anforderung_festgehalten_at  timestamptz,
    pakete_stand                 varchar(16)   CHECK (pakete_stand IN ('BEZIFFERT', 'KEINE')),
    pakete_art                   varchar(32),
    pakete_typisch_usd           numeric(12,6),
    pakete_unten_usd             numeric(12,6),
    pakete_oben_usd              numeric(12,6),
    pakete_festgehalten_at       timestamptz,
    abgeschlossen_at             timestamptz,
    tatsaechlich_usd             numeric(12,6),
    tatsaechlich_unvollstaendig  boolean       NOT NULL DEFAULT false,
    kosten_karten                integer[]     NOT NULL DEFAULT '{}'
);

CREATE INDEX idx_vorhaben_kosten_project ON vorhaben_kosten (project_id);

CREATE TABLE vorhaben_kosten_einfuehrung (
    project_id      bigint       PRIMARY KEY REFERENCES project (id) ON DELETE CASCADE,
    eingefuehrt_at  timestamptz  NOT NULL
);
