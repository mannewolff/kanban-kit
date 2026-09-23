-- Das Verfallen alter Staende als eigene Art im Protokoll (Issue #830, Plan #825)
-- ---------------------------------------------------------------------------
-- backup/retention.sh laeuft im selben Container wie backup.sh und am selben Takt; es raeumt
-- Basissicherungen, WAL-Archive und unreferenzierte Anhaenge. Sein Ausgang gehoert ins selbe
-- Protokoll wie der der Sicherungslaeufe — sonst waere ausgerechnet der Lauf, der etwas LOESCHT,
-- der einzige ohne Spur.
--
-- Warum 'retention' bewusst NICHT in BackupKind aufgenommen wird:
--
-- Der Lesepfad (BackupStatusService) fragt je Art nach dem juengsten Lauf, leitet daraus eine
-- Warnfrist ab und faellt ein Gesamturteil. Aufraeumen ist aber keine Sicherung: Ein gescheiterter
-- Verfall-Lauf faerbte die Ampel rot, waehrend jede Sicherung puenktlich entsteht — die Kachel
-- saegte dann "Sicherung fehlgeschlagen" ueber einen vollstaendig gesicherten Stand. Und weil
-- BackupProperties#takt ueber die Arten erschoepfend entscheidet, brauchte die neue Art dort eine
-- Frist, die es fachlich nicht gibt.
--
-- Der Lesepfad sucht ausschliesslich nach den vier bekannten Arten (findFirstByKind...), eine
-- 'retention'-Zeile trifft er also nie an; BackupKind.valueOf bekommt sie nicht zu sehen. Der
-- Betreiber findet sie im Protokoll des Containers und in dieser Tabelle. Sichtbar zu machen, was
-- geraeumt wurde, ist ein eigener Schnitt — er faengt hier mit der Spur an.
ALTER TABLE backup_run DROP CONSTRAINT ck_backup_run_kind;
ALTER TABLE backup_run ADD CONSTRAINT ck_backup_run_kind
    CHECK (kind IN ('basis', 'wal', 'spiegel', 'offsite', 'retention'));
