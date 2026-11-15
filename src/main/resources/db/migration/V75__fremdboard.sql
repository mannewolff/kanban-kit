-- ---------------------------------------------------------------------------
-- Board in einem fremden System (Issue #2156, Plan #2152 A1, A10, A15, A18; Fachliche Quelle #2150)
--
-- fremdboard_verbindung: genau eine Verbindung je Projekt. Ob ein Projekt ein fremdes Board hat,
-- sagt allein das Vorhandensein einer Zeile hier (Port project.application.BoardQuelle) — keine
-- zweite Quelle an project.
--   art                     welches System (zuerst GITLAB; weitere kommen ueber den Check dazu)
--   basis_url, projekt_pfad Adresse des Systems und Pfad des angebundenen Projekts dort
--   zugang_verschluesselt   der Lesezugang, verschluesselt (A13); nie im Klartext
--   kit_konto               Benutzername des Kits im fremden System; NULL, wenn keines hinterlegt
--   statuslabels            Labelnamen je Status (backlog, ready, inProgress, inReview)
--   board_id                das Spiegel-Board in kanban-kit (A18), hoechstens eine Verbindung;
--                           ohne ON DELETE: Solange die Verbindung steht, laesst sich das Board
--                           nicht loeschen (sonst fiele die Schreibsperre still weg). Beim
--                           Loeschen des Projekts gehen Board und Verbindung zusammen.
--   angebunden_am           ab hier wird gezaehlt (A4)
--   letzter_abgleich_am, letzter_vollabgleich_am, letzter_fehler  Stand des Abgleichs (A7, A9)
--
-- card.extern_url: Adresse der gespiegelten Karte im fremden System ("In GitLab oeffnen").
-- card_label_event.extern_akteur, card_activity.extern_akteur: Benutzer im fremden System (A11).
--
-- fremdboard_ereignis: jedes uebernommene Ereignis genau einmal je Projekt, Art und Kennung im
-- fremden System — Sperre gegen doppelte Uebernahme (A6).
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V75 aus
-- flyway_schema_history loeschen):
--   DROP TABLE fremdboard_ereignis;
--   DROP TABLE fremdboard_verbindung;
--   ALTER TABLE card_activity DROP COLUMN extern_akteur;
--   ALTER TABLE card_label_event DROP COLUMN extern_akteur;
--   ALTER TABLE card DROP COLUMN extern_url;
-- ---------------------------------------------------------------------------
CREATE TABLE fremdboard_verbindung (
    project_id              bigint        PRIMARY KEY REFERENCES project (id) ON DELETE CASCADE,
    art                     varchar(20)   NOT NULL,
    basis_url               varchar(500)  NOT NULL,
    projekt_pfad            varchar(500)  NOT NULL,
    zugang_verschluesselt   text          NOT NULL,
    kit_konto               varchar(255),
    statuslabels            jsonb         NOT NULL,
    board_id                bigint        NOT NULL REFERENCES board (id),
    angebunden_am           timestamptz   NOT NULL,
    letzter_abgleich_am     timestamptz,
    letzter_vollabgleich_am timestamptz,
    letzter_fehler          varchar(1000),
    CONSTRAINT chk_fremdboard_verbindung_art CHECK (art IN ('GITLAB')),
    CONSTRAINT uq_fremdboard_verbindung_board UNIQUE (board_id)
);

ALTER TABLE card ADD COLUMN extern_url varchar(1000);
ALTER TABLE card_label_event ADD COLUMN extern_akteur varchar(255);
ALTER TABLE card_activity ADD COLUMN extern_akteur varchar(255);

CREATE TABLE fremdboard_ereignis (
    project_id bigint       NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    extern_id  varchar(255) NOT NULL,
    art        varchar(30)  NOT NULL,
    CONSTRAINT pk_fremdboard_ereignis PRIMARY KEY (project_id, art, extern_id)
);
