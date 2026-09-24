-- Der Grund, warum ein Nachtlauf hart abgebrochen ist (Issue #1142, Plan #1139, fachlich #1074).
--
-- Ein Lauf, der abbricht, meldet das heute als complete = false und ohne Grund. Das Board kann
-- daraus weder ablesen, dass der Lauf zu Ende ist, noch warum er es ist. Diese Spalte nimmt den
-- vom Runner gemeldeten Grund entgegen; der Ausgang (Urteil FAILED, Stoerungsliste) folgt in
-- einem eigenen Paket.
--
-- NULL heisst zweierlei, und beides ist kein Befund: "nicht abgebrochen" oder "vor dieser
-- Umstellung eingeliefert". Deshalb kein Backfill (Plan #1139, E11) -- aus einem gespeicherten
-- Lauf laesst sich ein Abbruchgrund nicht nachtraeglich herleiten, und ein erfundener Text
-- faerbte Bestandslaeufe um.
--
-- varchar(4000) wie night_run.unparsed_sample und night_run_item.excerpt (NightRunLimits.
-- EXCERPT_MAX), nicht varchar(300) wie no_work_reason aus V35 (Plan #1139, E4): Ein Grund ohne
-- Arbeit ist ein Satz fuer die Anzeige, ein Abbruchgrund fuehrt Dateilisten und Ausschnitte aus
-- dem Protokoll. Bei 300 Zeichen risse die Meldung eines echten Abbruchs in einen Serverfehler.

ALTER TABLE night_run
    ADD COLUMN abort_reason varchar(4000);
