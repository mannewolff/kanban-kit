# Sicherung & Wiederherstellung

kanban-kit sichert zwei Bestände: die **Datenbank** (auf den Zeitpunkt genau, über Basissicherung
und fortlaufendes WAL-Archiv) und die **Anhänge** aus dem Objektspeicher (als Spiegel im Takt von
wenigen Minuten). Beides liegt doppelt — unverschlüsselt auf dem Server für den schnellen Weg
zurück, und verschlüsselt außer Haus für den Fall, dass es den Server nicht mehr gibt.

Die Sicherung ist **ausgeliefert aus**. Sie wird über ein eigenes Compose-Overlay zugeschaltet; wer
nichts einrichtet, sichert nicht — und die Admin-Ansicht sagt das, statt „kaputt" zu melden, was
nie lief.

> ## ⚠️ Ohne den verwahrten privaten Schlüssel ist aus den Sicherungen nichts zu holen
>
> Der Server hält **nur den öffentlichen** Schlüssel. Er kann seine eigenen Sicherungen
> verschlüsseln, aber nicht lesen — und das ist Absicht: Wer den Server übernimmt, übernimmt damit
> nicht die Sicherungen.
>
> Die Kehrseite trägt der Betreiber: **Ist der private Schlüssel weg, sind alle Sicherungen außer
> Haus unwiederbringlich verloren.** Kein Dienstleister, kein Hoster und kein Entwickler kann sie
> öffnen. Den privaten Schlüssel deshalb **außerhalb des Servers** verwahren, mindestens zweifach
> und an getrennten Orten — Einzelheiten im Abschnitt „Schlüsselverwahrung".

## Was die Sicherung tut

| Lauf | Was passiert | Takt |
|---|---|---|
| `basis` | `pg_basebackup` der Datenbank nach `/sicherungen` | `MANBAN_BACKUP_BASE_CRON` (Vorgabe: täglich 3 Uhr) |
| `offsite` | dieselbe Basissicherung verschlüsselt ans Ziel außer Haus | mit jeder Basissicherung |
| `spiegel` | Anhänge aus dem Objektspeicher lokal und verschlüsselt ans Ziel | `MANBAN_BACKUP_MIRROR_INTERVAL` (Vorgabe: alle 5 Minuten) |
| `wal` | neue WAL-Segmente verschlüsselt ans Ziel | wie der Spiegel |

Jeder Lauf schreibt eine Zeile in die Tabelle `backup_run` — auch ein gescheiterter, dann mit
Grund. Daraus speist sich die Ampel im Admin-Bereich (siehe „Ist die Sicherung gelungen?").

Die Sicherung läuft in einem **eigenen Container**, nicht in der Anwendung: Sie muss funktionieren,
wenn die Anwendung nicht startet — genau dann braucht man sie.

## Einrichtung

Fünf Schritte, in dieser Reihenfolge.

### 1. Schlüsselpaar erzeugen

Mit örtlich installiertem [age](https://github.com/FiloSottile/age)
(`brew install age`, `apt install age`):

```
age-keygen -o manban-backup.key
```

Ohne örtliche Installation genügt ein Einmal-Container:

```
docker run --rm -v "$PWD:/aus" -w /aus alpine:3.20 \
  sh -c "apk add --no-cache age >/dev/null && age-keygen -o manban-backup.key"
```

Die Ausgabe nennt den **öffentlichen** Teil (`Public key: age1…`), die Datei enthält den
**privaten**.

### 2. Den privaten Schlüssel außerhalb des Servers verwahren

`manban-backup.key` **vom Server nehmen** und sicher ablegen (Abschnitt „Schlüsselverwahrung"). Er
gehört nicht nach `backup/`, nicht in die `.env` und nicht ins Abbild. Erst eine Rückholung aus der
Kopie außer Haus bekommt ihn für genau diesen einen Aufruf hereingereicht.

### 3. Den öffentlichen Schlüssel eintragen

In der `.env` neben der `docker-compose.yml`:

```
MANBAN_BACKUP_AGE_RECIPIENT=age1…
```

Das ist der einzige Schlüssel, den der Server je zu sehen bekommt.

### 4. Ziel außer Haus einrichten

Das „wohin" bestimmt [rclone](https://rclone.org). Vorlage kopieren und anpassen:

```
cp backup/rclone.conf.example backup/rclone.conf
```

Der Name des Abschnitts in der Datei ist das Remote, das in `MANBAN_BACKUP_TARGET` vor dem
Doppelpunkt steht:

```
MANBAN_BACKUP_TARGET=nextcloud:manban-sicherung
```

Das Kennwort gehört verschleiert in die Datei (`rclone obscure '<app-passwort>'`), und für
Nextcloud ein eigenes App-Passwort statt des Anmeldekennworts.

**Der Ablageort ist austauschbar.** Was dort ankommt, ist bereits verschlüsselt; das Ziel muss
nichts weiter können, als Dateien zu halten. Nextcloud über WebDAV ist ein rclone-Ziel unter
vielen — S3, Backblaze B2, ein zweiter Server über SFTP oder eine angehängte Platte tun es ebenso.
Ein reiner Pfad (`MANBAN_BACKUP_TARGET=/pfad/zur/ablage`) nutzt das Dateisystem des Containers:
brauchbar für eine Probe, keine Kopie außer Haus.

### 5. Overlay zuschalten

```
docker compose -f docker-compose.yml -f docker-compose.backup.yml up -d --build
```

In Produktion zusätzlich mit dem Prod-Overlay:

```
docker compose -f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.backup.yml up -d --build
```

Das `-f` ist der **einzige Schalter**. `MANBAN_BACKUP_ENABLED` setzt das Overlay selbst; von Hand
wird es nie gesetzt — ein zweiter Schalter in der `.env` könnte vom Zustand der Container
abweichen, und die Anwendung meldete „veraltet", während gar kein Sicherungs-Container läuft.

> **Das Zuschalten startet die Datenbank einmal neu.** Das Overlay schaltet die WAL-Archivierung
> ein (`archive_mode=on`), und die ist nur mit einem Neustart des Datenbankservers zu haben. Der
> Neustart fällt **genau einmal** an, beim Zuschalten — danach nicht mehr. Deshalb stehen diese
> Einstellungen ausschließlich im Overlay: Eine Instanz ohne eingeschaltete Sicherung hätte keinen
> Abnehmer für das Archiv und schriebe ihr Volume voll.

Ob der Dienst läuft, zeigt das Protokoll:

```
docker compose -f docker-compose.yml -f docker-compose.backup.yml logs -f manban-backup
```

Fehlen Ziel oder Empfänger, sagt der Container das beim Start und sichert nichts. Eine erste
Basissicherung von Hand, ohne auf den Takt zu warten:

```
docker compose -f docker-compose.yml -f docker-compose.backup.yml exec manban-backup backup.sh basis
```

## Schlüsselverwahrung

Der private Schlüssel ist der einzige Teil des Werks, den keine Wiederherstellung ersetzen kann.
Alles andere — Server, Volumes, Konfiguration — lässt sich neu bauen.

- **Zweimal an getrennten Orten.** Ein Passwortmanager und ein zweiter Ort, der einen Serverausfall
  überlebt: ausgedruckt im Safe, auf einem Stick im Bankschließfach, beim Notar. Ein Ort allein ist
  kein Ort.
- **Nie auf dem Server**, den er schützt — auch nicht „nur kurz" beim Einrichten. Der Sinn der
  Trennung ist, dass eine Übernahme des Servers die Sicherungen nicht mitnimmt.
- **Nicht ins Repository.** `backup/rclone.conf` steht in der `.gitignore`; der Schlüssel hat dort
  ohnehin nichts zu suchen.
- **Lesbarkeit prüfen**, nicht nur die Existenz: Einmal im Jahr eine Sicherung damit entschlüsseln
  (Abschnitt „Rückholung"). Ein Schlüssel, den nie jemand benutzt hat, ist eine Vermutung.
- **Weitergabe regeln.** Wer soll im Ernstfall herankommen, wenn der Betreiber es nicht kann? Diese
  Frage gehört beantwortet, bevor sie sich stellt.

> **Wer den Server übernimmt, kann die Kopie außer Haus zwar nicht lesen, aber löschen.** Die
> Zugangsdaten des rclone-Ziels liegen auf dem Server; sie müssen dort liegen, sonst käme nichts
> an. Ein Angreifer mit Serverzugriff kann die abgelegten Dateien also **löschen**, auch wenn er sie
> nicht öffnen kann. Deshalb am Ziel **Papierkorb und Versionierung eingeschaltet lassen** (bei
> Nextcloud beides Vorgabe) — oder, wenn das Ziel es kann, ein Konto mit Schreib-, aber ohne
> Löschrecht verwenden.

## Rückholung

Es gibt zwei Wege, und sie unterscheiden sich in Tempo und Voraussetzung.

| | schnell | vollständig |
|---|---|---|
| Quelle | `/sicherungen` auf dem Server | verschlüsselte Kopie außer Haus |
| Aufruf | `--quelle lokal` (Vorgabe) | `--quelle aussenhaus --schluessel <datei>` |
| Braucht den privaten Schlüssel | nein | **ja** |
| Taugt für | verpfuschte Daten, versehentliche Löschung | Serververlust, leere Maschine |

Beide holen auf einen **Zeitpunkt** zurück, nicht nur auf die letzte Sicherung: Die Basissicherung
davor wird eingespielt, und das WAL trägt von dort bis zum gewählten Zeitpunkt nach.

> **Die Grenze nach vorn ist der Anhang-Spiegel.** Zurückgeholt werden kann nie weiter nach vorn als
> der letzte gelungene Anhang-Spiegel (Vorgabe: alle fünf Minuten). Liegt der Zielzeitpunkt dahinter,
> **bricht die Rückholung ab und spielt nichts ein** — eine Datenbank, deren Karten auf Anhänge
> zeigen, die es im Objektspeicher nicht gibt, ist kein wiederhergestellter Stand.

### Der schnelle Weg

Die Datenbank muss dabei stehen, und ihr Datenverzeichnis muss leer sein — es wird nichts
überschrieben.

```
docker compose -f docker-compose.yml -f docker-compose.backup.yml stop postgres manban-api
docker volume rm <stack>_postgres_data && docker volume create <stack>_postgres_data
docker compose -f docker-compose.yml -f docker-compose.backup.yml run --rm \
  -v <stack>_postgres_data:/wiederherstellung/pgdata \
  --entrypoint restore.sh manban-backup 2026-09-23T08:00:00Z
docker compose -f docker-compose.yml -f docker-compose.backup.yml up -d
```

`<stack>` ist der Projektname von Compose (Vorgabe: der Verzeichnisname, also `kanban-kit`). Der
Zeitpunkt wird **ausschließlich in UTC** angegeben, auf die Sekunde genau und mit `Z` — eine Angabe
in Ortszeit wird abgewiesen.

Das Skript startet die Datenbank nicht; es legt ein einspielbares Datenverzeichnis hin. Beim
nächsten Start fährt Postgres das WAL bis zum Zielzeitpunkt nach und wird danach von selbst wieder
schreibend.

### Der vollständige Weg, auch auf eine leere Maschine

Gebraucht werden: Docker, dieses Repository, die `.env`, der Zugang zum rclone-Ziel und der
**verwahrte private Schlüssel**.

```
git clone https://github.com/mannewolff/kanban-kit.git && cd kanban-kit
cp .env.example .env                                 # Zugangsdaten und MANBAN_BACKUP_* eintragen
cp backup/rclone.conf.example backup/rclone.conf     # Ziel eintragen
docker compose -f docker-compose.yml -f docker-compose.backup.yml up -d --build
docker compose -f docker-compose.yml -f docker-compose.backup.yml stop postgres manban-api
docker compose -f docker-compose.yml -f docker-compose.backup.yml run --rm \
  -v kanban-kit_postgres_data:/wiederherstellung/pgdata \
  -v /pfad/zum/verwahrten/manban-backup.key:/schluessel.key:ro \
  --entrypoint restore.sh manban-backup 2026-09-23T08:00:00Z \
  --quelle aussenhaus --schluessel /schluessel.key
docker compose -f docker-compose.yml -f docker-compose.backup.yml up -d
```

Der Schlüssel wird für genau diesen einen Aufruf hereingereicht und bleibt danach wieder draußen.
Die Anhänge schreibt dasselbe Skript in den Objektspeicher zurück.

### Danach prüfen

```
GET /api/admin/storage/reconciliation   → { "orphanedObjects": [...], "missingObjects": [...] }
```

`missingObjects` muss **leer** sein — dann zeigt keine Karte auf einen Anhang, den es nicht gibt.
Verwaiste Objekte dürfen auftauchen: Der Spiegel löscht selbst nie, kann also Anhänge enthalten, die
zum Zielzeitpunkt schon gelöscht waren. Das ist der harmlose Fall.

## Verfallen alter Stände

`MANBAN_BACKUP_RETENTION_DAYS` (Vorgabe: 7) bestimmt die Aufbewahrung. Das Verfallen läuft im
selben Takt wie die Basissicherung, unmittelbar danach; von Hand:

```
docker compose -f docker-compose.yml -f docker-compose.backup.yml exec manban-backup retention.sh
```

Drei Bestände, drei Regeln — und die Unterschiede sind Absicht:

- **Basissicherungen** fallen nach der Frist, lokal und außer Haus je für sich. Die **jüngste fällt
  nie**, auch wenn sie älter ist: Ein Container, der eine Woche lang nicht sichern konnte, räumte
  sonst den letzten Stand weg, den es überhaupt noch gab.
- **WAL-Segmente** fallen nicht nach Datum, sondern nach der **ältesten Basissicherung, die
  bleibt** — ein Segment davor gehört zu keinem vorhandenen Stand mehr. Gibt es keine
  Basissicherung, wird nichts geräumt.
- **Anhänge im Spiegel** fallen nur, wenn sie älter als die Frist sind **und** an keiner Karte mehr
  hängen. Nach Dateialter allein geräumt wäre ein lebender Anhang nach einer Woche weg.

Das Verfallen entschlüsselt zu keinem Zeitpunkt etwas: Außer Haus wird nach Namen gelöscht, und die
stehen dort im Klartext.

**Die Frist ist zugleich das Zeitfenster der Rückholung.** Mit der Vorgabe reicht sie sieben Tage
zurück — was länger her ist, ist nicht mehr da. Wer weiter zurückkönnen will, setzt den Wert höher
und rechnet mit dem entsprechenden Platzbedarf am Ziel.

## Ist die Sicherung gelungen?

Der Plattform-Admin sieht den Stand im Admin-Bereich; dieselbe Auskunft gibt der Endpunkt

```
GET /api/admin/backup/status
```

Vier Zustände, die bewusst auseinandergehalten werden:

| Zustand | Bedeutung |
|---|---|
| `OK` | Jede Laufart hat einen gelungenen Lauf innerhalb ihrer Frist. |
| `VERALTET` | Mindestens eine Art schweigt länger, als ihr Takt erlaubt. |
| `FEHLGESCHLAGEN` | Der jüngste Lauf mindestens einer Art ist gescheitert. |
| `ABGESCHALTET` | Es läuft keine Sicherung — das Overlay ist nicht zugeschaltet. |

Die Warnfristen sind nicht eigens einstellbar: Sie leiten sich aus `MANBAN_BACKUP_BASE_CRON` und
`MANBAN_BACKUP_MIRROR_INTERVAL` ab und können deshalb nicht vom tatsächlichen Takt abweichen.

Zusätzlich alarmiert die Anwendung die Plattform-Admins per Mail, wenn die Sicherung schweigt oder
scheitert.

> **Ohne eingeschalteten Mailversand verpufft der Alarm im Protokoll.** `MANBAN_MAIL_ENABLED=false`
> ist der ausgelieferte Zustand. Dann ist die Ansicht im Admin-Bereich der **einzige verlässliche
> Weg**, von einer ausgefallenen Sicherung zu erfahren — sie will dann regelmäßig angesehen werden.
> Wer sich auf den Alarm verlassen will, schaltet den Mailversand ein (siehe
> [Betrieb & Installation](betrieb.md)).

## Eine Sicherung, die nie zurückgeholt wurde, ist keine

Der Weg zurück will geprobt werden, nicht im Ernstfall zum ersten Mal gegangen. Das Ergebnis der
letzten Probe steht in [Betrieb & Installation](betrieb.md#letzter-wiederherstellungsnachweis) —
mit Datum, Commit und Ausgang.

Den maschinellen Teil dieser Probe fährt ein Skript. Es baut einen eigenen, wegwerfbaren Stack,
befüllt ihn, sichert, verwirft Datenbank und Objektspeicher, holt aus der verschlüsselten Kopie
außer Haus zurück und prüft danach Karte, Anhang und Objektabgleich:

```bash
bash backup/test/restore-roundtrip.sh              # muss mit Exitcode 0 enden
bash backup/test/restore-roundtrip.sh --sabotage   # Gegenprobe: muss scheitern
```

Es braucht Docker, curl und node, dauert einige Minuten und räumt sich selbst wieder ab. Teil von
`mvn verify` ist es bewusst nicht — es verwirft Container, auf denen die übrige Testsuite steht.
Die Einzelheiten stehen im Kommentarkopf des Skripts.

Das Skript ersetzt den Nachweis nicht, es stützt ihn: Ein Lauf auf einer **leeren Maschine** —
frischer Host, nur Docker, nur die Kopie außer Haus und der verwahrte private Schlüssel — bleibt
Handarbeit und gehört in die Tabelle in [Betrieb &
Installation](betrieb.md#letzter-wiederherstellungsnachweis).
