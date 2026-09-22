# Lastnachweis (`perf/`)

Hier liegt das Messwerkzeug für den Durchsatznachweis der Kanban-Compat-API — die Skripte, mit
denen die Zusage aus der fachlichen Quelle [Issue #970](https://kanban.mwolff.org) belegt wird:

> 50 gleichzeitig aktive Personen mit je 60 Befehlen pro Minute (davon 10 gleichzeitig) über
> 30 Minuten, ohne dass ein fachlich zulässiger Befehl an der Last scheitert. Die Weboberfläche
> bleibt von der Bremse unberührt. Wer die eigene Grenze überschreitet, bekommt eine als
> „ausgelastet" erkennbare Antwort mit Wartehinweis.

Das Verzeichnis liegt **außerhalb** von `mvn verify` und außerhalb des Frontend-Builds
(Plan [#995](https://kanban.mwolff.org), E12). Ein Maven-Modul fiele unter die Pflicht zu
100 Prozent Zeilen-, Zweig- und Mutationsabdeckung aus `CLAUDE-java.md`; für ein Lastskript ist
das sinnlos. Die Skripte werden also nie automatisch ausgeführt — ein Lauf ist immer ein
bewusster Aufruf von Hand.

| Datei | Zweck |
|---|---|
| `lastprofil.js` | 50 Personen × 60 Befehle/min über alle elf Befehle der Kanban-Compat-API |
| `weblast.js` | 10 Personen auf den Lesepfaden der Weboberfläche, parallel dazu |
| `ueberzieher.js` | eine Person, die dauerhaft über ihre Grenze schickt |
| `ergebnisse/` | Ablage der Protokolle je Lauf, eingecheckt |

---

## 1. Die Nachweis-Instanz

**Der Lauf geht nie gegen `kanban.mwolff.org`.** Diese Instanz trägt die laufende Entwicklung
dieses Projekts mit echten Karten; 30 Minuten mit 3.000 Befehlen pro Minute daraufzugeben hieße,
den eigenen Prozess anzuhalten und echte Daten mit Testkarten zu vermengen (Plan #995, E13).

Gemessen wird gegen eine **eigens hochgezogene, baugleiche Instanz**: dieselbe Ausstattung wie die
gehostete (CPU, RAM, Datenträger), derselbe Aufbau aus `docker-compose.yml` (Postgres 16, MinIO,
Caddy, App), dieselbe Version. Die Beschaffung dieser zweiten, zeitweiligen Instanz liegt beim
Menschen. Die Zusage gilt danach für die gehostete Instanz.

Für das Protokoll festhalten (siehe Abschnitt 6): Anbieter, Instanztyp, vCPU, RAM, Datenträger,
Netzanbindung, App-Version (`git rev-parse HEAD`), Java- und Postgres-Version.

Die Last-Maschine (die, auf der k6 läuft) steht **nicht** auf derselben Maschine wie die
Nachweis-Instanz. Sonst konkurrieren Lasterzeuger und Messobjekt um dieselben Kerne, und die
gemessenen Antwortzeiten gehören zur Hälfte dem Messwerkzeug.

---

## 2. k6 installieren

```bash
# macOS
brew install k6

# Debian/Ubuntu
sudo gpg --no-default-keyring \
  --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 \
  --recv-keys C5AD17C747E3415A3642D57D77C6C491D6AC1D69
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6

# ohne Installation, über Docker
docker run --rm -i grafana/k6 version
```

Prüfen, dass die Skripte fehlerfrei sind, ohne sie laufen zu lassen:

```bash
k6 archive perf/lastprofil.js  -O /dev/null
k6 archive perf/weblast.js     -O /dev/null
k6 archive perf/ueberzieher.js -O /dev/null
```

`k6 archive` führt den Init-Kontext der Skripte aus und packt sie. Exit 0 heißt: Syntax, Importe
und `options` sind in Ordnung. Deshalb wirft **keines** der Skripte im Init-Kontext, auch nicht
bei fehlenden Umgebungsvariablen — geprüft wird erst in `setup()`, und das läuft nur beim echten
Lauf.

---

## 3. Testdaten anlegen

Alles auf der Nachweis-Instanz, nicht auf der produktiven.

1. **Plattform-Admin** über das Bootstrap-Token anlegen (siehe `README.md` des Projekts).
2. **Ein Projekt und ein Board** anlegen, mit den üblichen Spalten (Backlog, Ready, In progress,
   In review, Done).
3. **Labels anlegen**, die die Skripte setzen und entfernen: `last-a`, `last-b`, `last-c`.
   Das ist Pflicht — `POST /items/{id}/labels` verlangt ein auf dem Board **bereits definiertes**
   Label und antwortet sonst mit 404. Über die Oberfläche oder:
   ```bash
   curl -sS -X POST "$MANBAN_BASIS/api/boards/$BOARD/labels" \
     -H 'Content-Type: application/json' -b cookies.txt \
     -d '{"name":"last-a","color":"#888888"}'
   ```
4. **51 Personen** anlegen (50 für das Lastprofil, eine für den Überzieher) und alle als Mitglied
   mit mindestens der Rolle MEMBER in das Projekt aufnehmen. Die Grenze hängt an der **Person**
   (Plan #995, E1) — 50 Tokens derselben Person messen nicht 50 Personen, sondern eine.
5. **Je ein Access-Token** je Person, an Projekt **und** Board gebunden:
   ```bash
   curl -sS -X POST "$MANBAN_BASIS/api/access-tokens" \
     -H 'Content-Type: application/json' -b cookies.txt \
     -d '{"name":"lasttest","projectId":1,"boardId":1}'
   ```
   Ohne Bindung antwortet die Kanban-Compat-API mit 409. Die Tokens werden nur bei der Anlage
   ausgegeben — beim Einsammeln gleich in die Aufrufzeile schreiben.
6. **Web-Konten**: Für `weblast.js` genügen zehn der Personen aus Schritt 4, mit Passwort.

**Der Bestand wächst.** Das Lastprofil legt über 30 Minuten in der Größenordnung von 9.000 Karten
an (10 Prozent von 90.000 Befehlen). Die Instanz ist danach nicht mehr für einen zweiten,
vergleichbaren Ausgangslauf zu gebrauchen — vor jedem Lauf den Datenbestand auf denselben Stand
zurücksetzen (Volume neu anlegen, Flyway läuft erneut), sonst misst der zweite Lauf einen
anderen Bestand als der erste.

---

## 4. Umgebungsvariablen

Gemeinsam:

| Variable | Vorgabe | Bedeutung |
|---|---|---|
| `MANBAN_BASIS` | `https://localhost` | Wurzel der Nachweis-Instanz, ohne `/api` |
| `MANBAN_RAMPE` | `2m` | Anlauf vor der Messstrecke |
| `MANBAN_DAUER` | `30m` | Messstrecke |
| `MANBAN_TLS_UNGEPRUEFT` | leer (= aus) | gesetzt: Zertifikat nicht prüfen (nur bei selbst signiertem Caddy-Zertifikat) |

`lastprofil.js`:

| Variable | Vorgabe | Bedeutung |
|---|---|---|
| `MANBAN_TOKENS` | — (Pflicht) | Access-Tokens, kommagetrennt, eines je Person |
| `MANBAN_LABELS` | `last-a,last-b,last-c` | auf dem Board definierte Labelnamen |
| `MANBAN_PERSONEN` | `50` | Zahl der virtuellen Personen |
| `MANBAN_BEFEHLE_PRO_MINUTE` | `60` | Befehle je Person und Minute |
| `MANBAN_GLEICHZEITIG` | `10` | gleichzeitige Befehle je Person |
| `MANBAN_WIEDERHOLUNGEN` | `4` | Wiederholungsrunden nach einer 429-Abweisung |
| `MANBAN_WARTE_MAX_S` | `30` | Deckel für eine einzelne Wartezeit |
| `MANBAN_POOL_MAX` | `50` | Karten je Person im Vorrat für die Schreibbefehle |

`weblast.js`:

| Variable | Vorgabe | Bedeutung |
|---|---|---|
| `MANBAN_WEB_KONTEN` | — (Pflicht) | `mail:passwort`-Paare, kommagetrennt |
| `MANBAN_WEB_NUTZER` | `10` | gleichzeitige Nutzer der Oberfläche |
| `MANBAN_WEB_DENKPAUSE_S` | `5` | Pause zwischen zwei Leserunden |

`ueberzieher.js`:

| Variable | Vorgabe | Bedeutung |
|---|---|---|
| `MANBAN_UEBERZIEHER_TOKEN` | — (Pflicht) | Token **einer weiteren** Person, nicht aus `MANBAN_TOKENS` |
| `MANBAN_UEBERZIEHER_GLEICHZEITIG` | `20` | gleichzeitige Befehle — bewusst über der Grenze von 10 |
| `MANBAN_UEBERLAST_TYP` | `urn:manban:overload` | erwarteter `type` im Problem-Detail einer Abweisung |

---

## 5. Lauf

Die drei Skripte laufen **gleichzeitig**, sonst belegt keines von ihnen etwas über die beiden
anderen: dass die Oberfläche unter Volllast antwortet, und dass ein Überzieher die übrigen nicht
stört.

```bash
export MANBAN_BASIS="https://nachweis.example.org"
export MANBAN_TOKENS="tok1,tok2,…,tok50"
export MANBAN_WEB_KONTEN="a@example.org:geheim,b@example.org:geheim,…"
export MANBAN_UEBERZIEHER_TOKEN="tok51"

STAND="$(date +%Y-%m-%d-%H%M)"
k6 run perf/lastprofil.js  2>&1 | tee "perf/ergebnisse/$STAND-lastprofil.txt"  &
k6 run perf/weblast.js     2>&1 | tee "perf/ergebnisse/$STAND-weblast.txt"     &
k6 run perf/ueberzieher.js 2>&1 | tee "perf/ergebnisse/$STAND-ueberzieher.txt" &
wait
```

Für eine Probe vor dem echten Lauf reichen kurze Werte — sie kostet drei Minuten und findet die
Hälfte aller Konfigurationsfehler:

```bash
MANBAN_RAMPE=10s MANBAN_DAUER=1m MANBAN_PERSONEN=5 k6 run perf/lastprofil.js
```

---

## 6. Ergebnisse ablegen

Je Lauf kommen die drei Protokolle nach `perf/ergebnisse/` und werden **eingecheckt** — ein
Nachweis, der nur auf einer Laster-Maschine lag, ist nach deren Abschalten keiner mehr.
Namensschema `YYYY-MM-DD-HHMM-<skript>.txt`.

Dazu gehört eine Datei `YYYY-MM-DD-HHMM-umgebung.md` mit den Angaben aus Abschnitt 1
(Ausstattung, Versionen, Datenbestand zu Beginn) und dem, was der Lauf belegen sollte. Ohne sie
sind die Zahlen in einem halben Jahr nicht mehr einzuordnen.

---

## 7. Die Kennzahlen lesen

Über die eingebauten k6-Kennzahlen (`http_req_duration`, `http_req_failed`, `iterations`) hinaus
weisen die Skripte eigene aus. Die wichtigste Trennung: **Erstversuche und Wiederholungen stehen
getrennt.** Eine Gesamtzahl allein ließe einen Lauf, der jeden zweiten Befehl wiederholen musste,
wie einen glatten Lauf aussehen.

`lastprofil.js`:

| Kennzahl | Bedeutung |
|---|---|
| `befehle_erstversuch` | Befehle, die das Profil abgesetzt hat — die Zahl, gegen die alles andere zu lesen ist |
| `befehle_wiederholung` | zusätzliche Versuche nach einer 429-Abweisung, **nicht** in `befehle_erstversuch` enthalten |
| `befehle_abgewiesen_429` | Abweisungen insgesamt, über alle Runden |
| `befehle_aufgegeben` | Befehle, die auch nach der letzten Wiederholung abgewiesen blieben |
| `befehl_wurde_wiederholt` | Anteil der Befehle, die mindestens eine Wiederholung brauchten |
| `befehl_fehlgeschlagen` | Anteil der Befehle, die am Ende keine 2xx-Antwort hatten |
| `befehl_dauer_erstversuch` | Antwortzeit des Servers — hieran hängt die 5-Sekunden-Zusage |
| `befehl_dauer_gesamt` | Zeit bis zum Ergebnis inklusive Wartezeit nach 429 — was der Nutzer spürt |

Jede Kennzahl trägt den Tag `name` mit dem Befehlsmuster (`PUT /items/{id}/move`), sodass sich die
Zahlen je Befehl auseinanderziehen lassen:

```bash
k6 run --out json=perf/ergebnisse/$STAND-lastprofil.json perf/lastprofil.js
```

`weblast.js`: `web_abgewiesen_429` (muss 0 bleiben — die Oberfläche wird nie wegen Last
abgewiesen), `web_seite_dauer`, `web_seite_fehlgeschlagen`, `web_anmeldungen`.

`ueberzieher.js`: `ueberzieher_angenommen` gegen `ueberzieher_abgewiesen_429`,
`ueberzieher_abweisung_mit_wartehinweis` (jede Abweisung nennt `Retry-After`) und
`ueberzieher_abweisung_als_ueberlast_erkennbar` (jede Abweisung trägt `type: urn:manban:overload`).
Dass der Überzieher die übrigen nicht stört, steht **nicht** in diesen Zahlen, sondern im
Vergleich: `befehl_dauer_erstversuch` und `web_seite_dauer` des parallelen Laufs gegen einen Lauf
ohne Überzieher.

### Rote Schwellwerte beim Ausgangslauf

Die `thresholds` der Skripte sind die **Zusage**, nicht der Stand. Beim Ausgangslauf — vor jeder
Änderung am Bestand — dürfen sie rot sein; das ist dann das Messergebnis und kein Mangel des
Skripts. k6 endet in dem Fall mit Exit 99. Genau diese Abweichung ist der Grund, aus dem die
folgenden Arbeitspakete des Plans #995 entstanden sind, und der spätere Lauf misst sich an ihr.
