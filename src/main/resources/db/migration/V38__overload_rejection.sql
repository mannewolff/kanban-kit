-- Abweisungen wegen Überlast, je Person und Stunde aufsummiert (Issue #1000, Plan #995 E14).
-- Eine Zeile je Abweisung machte gerade den Überlastfall zum Schreiblastfall; fortgeschrieben wird
-- deshalb per INSERT … ON CONFLICT DO UPDATE auf den Schlüssel (user_id, hour_bucket), und zwar
-- gepuffert, nicht je Aufruf. Der Primärschlüssel ist dieser Unique-Index: Erst er macht das Rennen
-- zweier gleichzeitiger Erstschreiber derselben Stunde entscheidbar.
CREATE TABLE overload_rejection (
  user_id     bigint         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
  hour_bucket timestamptz(6) NOT NULL,
  rejections  integer        NOT NULL CHECK (rejections > 0),
  PRIMARY KEY (user_id, hour_bucket)
);

-- Der Aufräumjob löscht nach Alter (E23: 90 Tage); ohne Index läse er dafür die ganze Tabelle.
CREATE INDEX idx_overload_rejection_hour ON overload_rejection (hour_bucket);
