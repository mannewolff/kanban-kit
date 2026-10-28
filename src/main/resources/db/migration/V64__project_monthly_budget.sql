-- ---------------------------------------------------------------------------
-- Monatsbudget je Projekt (Issue #1964, Plan #1962 E3, Fachliche Quelle #1957)
--
-- Ein Betrag in USD je Projekt und Kalendermonat; NULL heisst "kein Budget". Ein festgelegtes
-- Budget ist groesser als 0 (CHECK). Die Breite NUMERIC(12,2) traegt die Obergrenze
-- 9 999 999 999,99 USD (E3) und hoechstens zwei Nachkommastellen.
--
-- Geschrieben wird die Spalte wie dashboard_participation allein ueber einen gezielten
-- Direkt-Update (ProjectRepository.setMonthlyBudget), nie ueber den allgemeinen Schreibweg.
-- ---------------------------------------------------------------------------
ALTER TABLE project
    ADD COLUMN monthly_budget_usd NUMERIC(12, 2) NULL CHECK (monthly_budget_usd > 0);
