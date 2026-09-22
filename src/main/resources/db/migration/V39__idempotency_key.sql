-- Idempotenz-Schlüssel der anlegenden Compat-Befehle (Issue #1001, Plan #995 E7/E9).
-- Je Projekt und Schlüssel eine Zeile mit Befehl, Status und Antwortrumpf der ersten Ausführung.
-- Der Primärschlüssel (project_id, key) ist der Unique-Index, der das Rennen zweier gleichzeitiger
-- Wiederholungen entscheidet: Die zweite wartet am Index, bis die erste committet oder
-- zurückrollt. response_status bleibt nur innerhalb der Transaktion der ersten Ausführung leer.
CREATE TABLE idempotency_record (
  project_id      bigint         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
  key             varchar(200)   NOT NULL,
  endpoint        varchar(200)   NOT NULL,
  response_status integer,
  response_body   text,
  created_at      timestamptz(6) NOT NULL,
  PRIMARY KEY (project_id, key)
);

-- Der Aufräumjob löscht nach Alter (E9: 24 Stunden).
CREATE INDEX idx_idempotency_record_created ON idempotency_record (created_at);
