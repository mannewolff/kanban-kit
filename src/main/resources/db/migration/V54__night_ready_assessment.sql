-- ---------------------------------------------------------------------------
-- Bewertung der Ready-Karten je Projekt: POST /api/kanban/night-runs/ready-assessment
-- (Issue #1726, Plan #1724 E7, Vertrag V2)
--
-- Der Runner meldet, wie er die Ready-Karten bewertet: je Karte die Gruende, aus denen er
-- sie zurueckstellt oder nicht zieht, und fuer die startbaren ihren Platz. Das Board bildet
-- die Regel nicht nach, es zeigt diese Bewertung (eine Wahrheit, #191). Je Projekt gilt nur
-- die juengste; eine neue ersetzt die alte vollstaendig.
--
-- Drei Tabellen statt jsonb, wie V48 und V53: Der Bestand nutzt nirgends jsonb, und Tabellen
-- halten ihre Pruefungen per Constraint. code und kind ohne CHECK auf eine Werteliste: Ein
-- neueres Kit mit einem weiteren Grund soll seine Bewertung nicht verlieren.
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, in dieser Reihenfolge, danach die
-- Zeile von V54 aus flyway_schema_history loeschen):
--   DROP TABLE night_ready_assessment_reason;
--   DROP TABLE night_ready_assessment_entry;
--   DROP TABLE night_ready_assessment;
-- ---------------------------------------------------------------------------

-- Eine Zeile je Projekt: project_id ist zugleich Primaerschluessel. ON DELETE CASCADE, weil
-- eine Bewertung ohne ihr Projekt keine Aussage waere.
CREATE TABLE night_ready_assessment (
    project_id     bigint        PRIMARY KEY,
    assessed_at    timestamptz   NOT NULL,
    run_started_at timestamptz,
    label          varchar(100)  NOT NULL,
    CONSTRAINT fk_night_ready_assessment_project FOREIGN KEY (project_id)
        REFERENCES project (id) ON DELETE CASCADE
);

-- Je bewerteter Karte eine Zeile. ordinal haelt die gemeldete Reihenfolge; position ist der
-- Platz unter den startbaren Karten und fehlt bei einer Karte mit Grund.
CREATE TABLE night_ready_assessment_entry (
    project_id  bigint   NOT NULL,
    card_number integer  NOT NULL,
    ordinal     integer  NOT NULL,
    position    integer,
    CONSTRAINT pk_night_ready_assessment_entry PRIMARY KEY (project_id, card_number),
    CONSTRAINT fk_night_ready_assessment_entry_assessment FOREIGN KEY (project_id)
        REFERENCES night_ready_assessment (project_id) ON DELETE CASCADE,
    CONSTRAINT ck_night_ready_assessment_entry_position CHECK (position >= 1),
    CONSTRAINT uq_night_ready_assessment_entry_ordinal UNIQUE (project_id, ordinal)
);

-- Je Grund einer Karte eine Zeile, in der gemeldeten Reihenfolge. card_numbers traegt die
-- Kartennummern kommagetrennt, paths die Pfade zeilenweise (ein Pfad darf ein Komma
-- enthalten). Laengen wie die Grenzen im Request (NightRunNowController).
CREATE TABLE night_ready_assessment_reason (
    project_id   bigint        NOT NULL,
    card_number  integer       NOT NULL,
    ordinal      integer       NOT NULL,
    code         varchar(40)   NOT NULL,
    kind         varchar(40),
    card_numbers text,
    paths        text,
    text         varchar(500)  NOT NULL,
    CONSTRAINT pk_night_ready_assessment_reason PRIMARY KEY (project_id, card_number, ordinal),
    CONSTRAINT fk_night_ready_assessment_reason_entry FOREIGN KEY (project_id, card_number)
        REFERENCES night_ready_assessment_entry (project_id, card_number) ON DELETE CASCADE
);
