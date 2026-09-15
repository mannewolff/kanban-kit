-- Generationsnummer am Konto als Grundlage fuer das Beenden von Sitzungen (Issue #884, Plan #883)
-- ---------------------------------------------------------------------------
-- Die Spalte zaehlt, wie oft die Sitzungen eines Kontos beendet wurden. Ein Session-Token traegt
-- die Generation seiner Ausstellung; steigt der Wert hier, ist jedes aeltere Token ungueltig.
-- Ein Zaehler und kein Zeitstempel (Plan #883, E1): Ein Zeitstempel haengt an der konfigurierten
-- Sitzungsdauer und wird beim Verkuerzen der TTL zum Sicherheitsloch.
--
-- DEFAULT 0 genuegt fuer Bestandskonten, ein Backfill ist nicht noetig: Ihre umlaufenden Tokens
-- tragen noch das alte, generationslose Format und sind nach E3 ohnehin formatbedingt ungueltig.
-- Jedes neue Konto startet damit ebenfalls bei 0.
--
-- NOT NULL, weil „keine Generation" kein Zustand ist, den die Sitzungspruefung sinnvoll deuten
-- koennte — ein NULL-Wert liesse das Hochzaehlen (session_generation + 1) ins Leere laufen und
-- damit eine Sitzung weiterlaufen, die haette enden muessen.

ALTER TABLE app_user ADD COLUMN session_generation bigint NOT NULL DEFAULT 0;
