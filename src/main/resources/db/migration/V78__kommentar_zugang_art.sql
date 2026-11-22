-- ---------------------------------------------------------------------------
-- Absender eines Kommentars: Art des Zugangs und Beitrag der KI (Issue #2208, Plan #2206 E6, E7;
-- Fachliche Quelle #2202)
--
-- zugang_art haelt fest, ueber welchen Zugang ein Kommentar angelegt wurde: STANDARD (gewoehnliches
-- Projekt-Token, etwa das des Rechenzentrums) oder APP (Zugang der Desktop-App eines Menschen). Bei
-- einem Kommentar aus der Sitzung bleibt sie leer.
--
-- von_ki ist genau dann wahr, wenn der Kommentar ueber einen STANDARD-Zugang kam, dessen Inhaber
-- beim Schreiben echter OWNER oder ADMIN des Projekts war. Der Server setzt es beim Anlegen; eine
-- Zeile im Text entscheidet das nicht (Kriterium 11).
--
-- Kein Backfill: Altzeilen und gespiegelte Kommentare bleiben leer bzw. false und gelten weder als
-- KI noch als App. Die Nummer V77 aus dem Plan war bei der Umsetzung schon vergeben.
--
-- Rein additiv: Eine aeltere App-Version laeuft gegen dieses Schema unveraendert.
--
-- Rueckweg (Flyway Community kennt kein Undo; von Hand, danach die Zeile von V78 aus
-- flyway_schema_history loeschen):
--   ALTER TABLE comment DROP COLUMN von_ki;
--   ALTER TABLE comment DROP COLUMN zugang_art;
-- ---------------------------------------------------------------------------
ALTER TABLE comment ADD COLUMN zugang_art varchar(10);

ALTER TABLE comment ADD CONSTRAINT chk_comment_zugang_art
    CHECK (zugang_art IS NULL OR zugang_art IN ('STANDARD', 'APP'));

ALTER TABLE comment ADD COLUMN von_ki boolean NOT NULL DEFAULT false;
