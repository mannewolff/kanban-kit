# Upgrade

Dies ist der **einzige** Ort, an dem steht, was ein Versionssprung von Hand verlangt. Der
`CHANGELOG.md` sagt, *was* sich geändert hat; diese Seite sagt, *was zu tun ist*.

## Fremde Anbindungen an die API

Ein **verlässlicher** Aufruf der API — in der API-Übersicht der Administration als verlässlich
gekennzeichnet — ändert sich nicht ohne Vorlauf: Er wird zuerst als **abgekündigt** gekennzeichnet
und ändert sich oder entfällt frühestens eine Minor-Version später. Jede solche Änderung steht im
`CHANGELOG.md` als **API-Änderung**. Muss eine fremde Anbindung daraufhin etwas anpassen, steht
der Anpassungshinweis hier, im Abschnitt der Version, mit der die Änderung kommt.

## Der gewöhnliche Weg

Im Verzeichnis neben der `docker-compose.yml`:

1. Den Image-Tag in der Compose-Datei auf die neue Version setzen.
2. `docker compose pull`
3. `docker compose up -d`

Das Datenbank-Schema zieht Flyway beim Start selbst nach.

**Eine Version ohne eigenen Abschnitt verlangt keine Handarbeit.**

Darunter steht je Version mit Handarbeit ein Abschnitt, **absteigend** nach Version. Wer mehrere
Versionen überspringt, arbeitet die betroffenen Abschnitte von unten nach oben ab — also von der
ältesten zur neuesten.

## 2.15.0 — veröffentlichte Abbilder, Betriebs-Overlay, eigenes Datenbankkennwort

### Vom Bau auf das veröffentlichte Abbild

Der Basis-Stack zieht seit dieser Version ein fertiges Abbild von `ghcr.io`, statt bei jedem Start
zu übersetzen. Wer bisher bei jedem Start bauen ließ, fährt jetzt:

```
docker compose pull
docker compose up -d
```

Wer weiterhin **aus dem Quelltext bauen** will (Entwicklung, eigene Änderungen), gibt das
Bau-Overlay mit:

```
docker compose -f docker-compose.yml -f docker-compose.bau.yml up -d --build
```

### Eigenes Datenbankkennwort — in dieser Reihenfolge

Die Anwendung startet seit dieser Version nicht mehr mit dem mitgelieferten Datenbankkennwort
`manban`. Der Wechsel ist für jede bestehende Instanz Pflicht — und er ist die Stelle, an der eine
naive Anleitung eine Instanz zerlegt. Deshalb in **genau dieser Reihenfolge**, bei laufendem Stack:

1. Das Kennwort **in der laufenden Datenbank** ändern:

   ```
   docker compose exec postgres psql -U manban -d manban \
     -c "ALTER ROLE manban PASSWORD '<neues Kennwort>'"
   ```

   Ein neues Kennwort erzeugt man mit `openssl rand -hex 32` — `-hex` liefert nur `0-9a-f`, damit
   ist kein `$`-Escaping in der `.env` nötig.

2. Denselben Wert in die `.env` neben der `docker-compose.yml` eintragen:

   ```
   POSTGRES_PASSWORD=<neues Kennwort>
   ```

3. Die Dienste, die das Kennwort benutzen, neu anlegen — bei zugeschalteter Sicherung auch
   `manban-backup`:

   ```
   docker compose up -d --force-recreate manban-api
   docker compose up -d --force-recreate manban-backup   # nur bei zugeschalteter Sicherung
   ```

Der Postgres-Container selbst muss nicht neu angelegt werden: Er hat das Kennwort in Schritt 1
bereits übernommen.

**Warum die Datenbank zuerst drankommt:** `POSTGRES_PASSWORD` liest das Postgres-Abbild nur beim
Anlegen eines leeren Datenverzeichnisses — ein bereits angelegtes ändert es nicht mehr. Wer nur die
`.env` anfasst, bekommt eine Anwendung, die mit dem neuen Kennwort gegen die alte Rolle anrennt.

### Die fünf Pflichtwerte des Produktivbetriebs

Der Produktivbetrieb läuft seit dieser Version über ein eigenes Overlay und verlangt fünf Werte in
der `.env`:

| Wert | Bedeutung |
|------|-----------|
| `MANBAN_SESSION_SECRET` | Schlüssel zum Signieren der Sitzungs-Cookies (`openssl rand -hex 32`) |
| `MANBAN_BASE_URL` | öffentliche Adresse dieser Instanz für Links in E-Mails |
| `OBJEKTSPEICHER_ROOT_USER` | Benutzername des Objektspeichers — frei wählbar, **nicht** `manban` |
| `OBJEKTSPEICHER_ROOT_PASSWORD` | Geheimnis des Objektspeichers (`openssl rand -hex 32`) |
| `POSTGRES_PASSWORD` | Kennwort der Datenbankrolle — siehe den Abschnitt darüber |

Gestartet wird damit:

```
docker compose -f docker-compose.yml -f docker-compose.betrieb.yml up -d
```

Fehlt einer der fünf Werte, bricht schon `docker compose config` ab und nennt ihn — also bevor
irgendein Container startet.

## 2.12.0 — Objektspeicher von MinIO auf SeaweedFS

Das Upgrade verlangt ein **Wartungsfenster** (Fenster ≤ 30 Minuten, in zwei Releases) und eine
**Änderung der `.env`**: Die vier `MANBAN_MINIO_*`-Werte heißen jetzt `MANBAN_STORAGE_*`,
`MINIO_ROOT_USER` und `MINIO_ROOT_PASSWORD` heißen `OBJEKTSPEICHER_ROOT_USER` und
`OBJEKTSPEICHER_ROOT_PASSWORD`.

An den Anhängen selbst ändert sich nichts, und Sicherungen von vor der Umstellung werden ohne
Umschlüsselung zurückgeholt.

Die vollständige Anleitung samt Rückweg ist ein eigenes Verfahren und bleibt in der
Betriebsdokumentation: Abschnitt [Umstellung des
Objektspeichers](docs/betrieb.md#umstellung-des-objektspeichers).

## 1.44.0 — eigener Sitzungsschlüssel ist Startbedingung

Eine Instanz, die bisher ohne eigenen `MANBAN_SESSION_SECRET` lief, startet nach dem Update erst
wieder, wenn ein eigener Schlüssel gesetzt ist:

```
openssl rand -hex 32
```

Den Wert in die `.env` neben der `docker-compose.yml` eintragen:

```
MANBAN_SESSION_SECRET=<erzeugter Wert>
```

Alle bestehenden Sitzungen sind danach ungültig — die Nutzer melden sich einmal neu an.

Das ist gewollt: Mit dem mitgelieferten Wert kann jeder, der das öffentliche Repository kennt,
gültige Sitzungen für jedes Konto erzeugen — auch für einen Plattform-Administrator.
