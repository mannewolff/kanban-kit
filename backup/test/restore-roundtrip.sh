#!/usr/bin/env bash
# restore-roundtrip.sh — die Rueckhol-Probe (Issue #832, Plan #825).
#
# Aufruf:
#   bash backup/test/restore-roundtrip.sh               # muss mit Exitcode 0 enden
#   bash backup/test/restore-roundtrip.sh --sabotage    # Gegenprobe: MUSS mit != 0 enden
#
# Voraussetzungen: Docker mit Compose, curl, node. Die Probe faehrt ihren eigenen, wegwerfbaren
# Stack unter einem eigenen Compose-Projektnamen hoch (MANBAN_PROBE_PROJEKT, Vorgabe
# manban-rueckholprobe) und raeumt ihn samt Volumes wieder ab — auch wenn ein Schritt scheitert
# oder jemand abbricht. Ein laufender Entwicklungs-Stack wird nicht angefasst; Caddy bleibt aus,
# die Anwendung haengt stattdessen auf MANBAN_PROBE_PORT (Vorgabe 18080).
#
# Achtung bei MANBAN_PROBE_PROJEKT: Die Probe verwirft ALLE Volumes dieses Compose-Projekts. Der
# Name muss darum 'rueckholprobe' enthalten, sonst faengt das Skript den Aufruf ab.
#
# Was hier bewiesen wird — AK11 der fachlichen Quelle (Issue #823) verlangt den Nachweis, dass eine
# vollstaendige Rueckholung tatsaechlich funktioniert, nicht bloss, dass die Skripte plausibel
# aussehen:
#
#   1. Eine befuellte Instanz (Projekt, Board, Karte, Anhang) wird gesichert und ausser Haus
#      kopiert — verschluesselt, mit ausschliesslich dem oeffentlichen `age`-Empfaenger (E3).
#   2. Datenbank- und Objektspeicher-Volume werden verworfen: die leere Maschine.
#   3. Aus der verschluesselten Kopie wird auf einen gemerkten Zeitpunkt zurueckgeholt, mit dem
#      privaten Schluessel, der nur fuer diesen einen Aufruf hereingereicht wird (AK8).
#   4. Danach gilt: die Karte von vor dem Zeitpunkt ist da, ihr Anhang ist byteweise derselbe, die
#      danach angelegte Karte fehlt, und der Abgleich des Objektspeichers meldet keine fehlenden
#      Objekte (AK4).
#
# Zwei Gegenproben laufen mit:
#   * E3 — im ganzen Sicherungs-Container liegt kein privater Schluessel, weder in einer Datei noch
#     in der Umgebung; die Sicherung ist trotzdem entstanden.
#   * E5 — ein Rueckholzeitpunkt hinter dem letzten Anhang-Spiegel wird abgewiesen, und es wird
#     dabei nichts eingespielt.
#
# Und eine dritte auf Zuruf: `--sabotage` verfaelscht den Anhang in der Kopie ausser Haus, nachdem
# sie entstanden ist. Die Probe muss dann scheitern — sonst prueft der byteweise Vergleich nichts.
#
# Kein Teil von `mvn verify` (E14): AbstractIntegrationTest teilt Postgres und MinIO als statische
# Singletons ueber die ganze IT-Suite; eine Probe, die genau diese Container verwirft, risse jede
# danach laufende Klasse mit. Sie laeuft von Hand und vor jedem Release, das die Sicherung beruehrt.
set -euo pipefail

PROBE_HEIM=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
WURZEL=$(cd "$PROBE_HEIM/../.." && pwd)

PROJEKT=${MANBAN_PROBE_PROJEKT:-manban-rueckholprobe}
PORT=${MANBAN_PROBE_PORT:-18080}
BASIS_URL="http://127.0.0.1:$PORT"

# Caddy bleibt aus: Es belegte 80 und 443 und brauchte TLS-Namen, die eine Probe nicht hat.
DIENSTE=(postgres minio manban-api manban-backup)

# Compose bildet die Volumenamen aus Projektname und Schluessel; nach Schritt 4 gibt es sie
# zeitweise nicht mehr, und ein Nachschlagen ginge dann ins Leere. Darum hier aufgeschrieben und
# direkt nach dem Hochfahren gegen die Wirklichkeit geprueft.
VOL_PG="${PROJEKT}_postgres_data"
VOL_MINIO="${PROJEKT}_minio_data"
VOL_WAL="${PROJEKT}_wal_archiv"
VOL_SICHERUNG="${PROJEKT}_backup_data"
VOL_AUSSENHAUS="${PROJEKT}_aussenhaus"

EMAIL=probe@manban.local
KENNWORT=Rueckholprobe-2026
TITEL_VORHER='Karte vor dem Zeitpunkt'
TITEL_NACHHER='Karte nach dem Zeitpunkt'

SABOTAGE=nein
SCHRITT='Vorbereitung'
# Beide leer vorbelegt: Die Abraeum-Falle steht, bevor es sie gibt, und `set -u` liesse sie sonst
# ausgerechnet dann scheitern, wenn frueh etwas schiefgeht.
RAUM=''
ENVDATEI=''
fehler=0

log() {
  printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

# Der Name, der bei einem Fehlschlag genannt wird. Ohne ihn stuende am Ende eines zehnminuetigen
# Laufs eine nackte Fehlermeldung irgendeines Werkzeugs.
schritt() {
  SCHRITT=$1
  log ''
  log "== $SCHRITT"
}

# Eine einzelne Pruefung. Sie bricht nicht sofort ab, sondern merkt sich den Fehlschlag: Am Ende
# einer Pruefgruppe will man alle Abweichungen sehen, nicht nur die erste.
pruefe() {
  if [ "$2" = "$3" ]; then
    log "ok    $1"
  else
    log "NICHT $1: '$2' (erwartet '$3')"
    fehler=1
  fi
}

gruppe_auswerten() {
  [ "$fehler" -eq 0 ] || return 1
}

# ---------------------------------------------------------------------------
# Werkzeuge und Arbeitsraum
# ---------------------------------------------------------------------------

while [ $# -gt 0 ]; do
  case $1 in
    --sabotage)
      SABOTAGE=ja
      shift
      ;;
    -h | --help)
      # Der Kommentarkopf ist die Anleitung: ab Zeile 2 bis zur ersten Zeile, die keine mehr ist.
      awk 'NR == 1 { next } /^#/ { print; next } { exit }' "${BASH_SOURCE[0]}"
      exit 0
      ;;
    *)
      log "FEHLER Unbekannte Angabe '$1'. Erlaubt: --sabotage."
      exit 2
      ;;
  esac
done

for werkzeug in docker curl node; do
  if ! command -v "$werkzeug" > /dev/null 2>&1; then
    log "FEHLER '$werkzeug' fehlt — ohne das laeuft die Probe nicht."
    exit 2
  fi
done
if ! docker compose version > /dev/null 2>&1; then
  log "FEHLER 'docker compose' fehlt — Compose v2 wird gebraucht."
  exit 2
fi

# Die Probe verwirft am Ende ausnahmslos alle Volumes ihres Compose-Projekts. Zeigte
# MANBAN_PROBE_PROJEKT versehentlich auf einen echten Stack, loeschte sie dessen Daten — und zwar
# ohne Rueckfrage, weil sie unbeaufsichtigt laufen soll. Der Name muss deshalb sagen, was er ist.
case $PROJEKT in
  *rueckholprobe*) ;;
  *)
    log "FEHLER MANBAN_PROBE_PROJEKT='$PROJEKT' enthaelt 'rueckholprobe' nicht."
    log '       Die Probe verwirft alle Volumes ihres Compose-Projekts; ein Name, der nach einem'
    log '       echten Stack aussehen koennte, wird abgelehnt.'
    exit 2
    ;;
esac

compose() {
  docker compose -p "$PROJEKT" --env-file "$ENVDATEI" \
    -f "$WURZEL/docker-compose.yml" \
    -f "$WURZEL/docker-compose.backup.yml" \
    -f "$PROBE_HEIM/docker-compose.probe.yml" "$@"
}

aufraeumen() {
  log ''
  log "== 7/7 Abraeumen: Stack und Wegwerf-Volumes"
  if [ -n "$ENVDATEI" ]; then
    compose down --volumes --remove-orphans > /dev/null 2>&1 || true
  fi
  # Zusaetzlich namentlich: Ein Volume, das Schritt 5 ueber `run -v` neu anlegen liess, traegt
  # keine Compose-Label und faellt sonst durch `down --volumes` hindurch.
  docker volume rm -f \
    "$VOL_PG" "$VOL_MINIO" "$VOL_WAL" "$VOL_SICHERUNG" "$VOL_AUSSENHAUS" > /dev/null 2>&1 || true
  if [ -n "$RAUM" ]; then
    rm -rf "$RAUM"
  fi
}

beim_ende() {
  local stand=$1
  if [ "$stand" -ne 0 ]; then
    log ''
    log "FEHLGESCHLAGEN im Schritt: $SCHRITT"
  fi
  aufraeumen
  exit "$stand"
}

trap 'beim_ende $?' EXIT
# Ein Abbruch von Hand laeuft ueber denselben Weg hinaus und raeumt damit genauso ab.
trap 'exit 130' INT
trap 'exit 143' TERM

# Der Arbeitsraum liegt ausserhalb des Projektverzeichnisses — er darf keinen unsauberen Working
# Tree hinterlassen. Docker muss den Pfad einhaengen duerfen (der private Schluessel wird von dort
# hereingereicht); ist $TMPDIR auf diesem Rechner nicht freigegeben, hilft
# MANBAN_PROBE_RAUM=$HOME/probe.
RAUM=$(mktemp -d "${MANBAN_PROBE_RAUM:-${TMPDIR:-/tmp}}/manban-rueckholprobe.XXXXXX")
KEKSE="$RAUM/kekse"
ENVDATEI="$RAUM/probe.env"
SCHLUESSEL="$RAUM/manban-backup.key"
ANHANG="$RAUM/anhang.bin"
ZURUECK="$RAUM/anhang-zurueck.bin"

zufall() {
  head -c "${1:-16}" /dev/urandom | od -An -v -tx1 | tr -d ' \n'
}

PG_DB=manban
PG_USER=manban
BOOTSTRAP_TOKEN=$(zufall 16)

# Die Umgebung dieses Laufs. Eigene Datei statt der .env des Projekts: Die Probe soll dasselbe
# Ergebnis liefern, egal wie der Rechner des Pruefenden eingerichtet ist.
cat > "$ENVDATEI" << ENV
POSTGRES_DB=$PG_DB
POSTGRES_USER=$PG_USER
POSTGRES_PASSWORD=$(zufall 12)
MINIO_ROOT_USER=manbanprobe
MINIO_ROOT_PASSWORD=$(zufall 12)
MANBAN_BASE_URL=$BASIS_URL
MANBAN_DEV_MODE=true
MANBAN_SESSION_SECRET=$(zufall 24)
# Die Probe spricht die Anwendung ueber HTTP an; ein Secure-Cookie kaeme nie zurueck.
MANBAN_COOKIE_SECURE=false
MANBAN_MAIL_ENABLED=false
MANBAN_BOOTSTRAP_ADMIN_TOKEN=$BOOTSTRAP_TOKEN
MANBAN_PROBE_PORT=$PORT
MANBAN_BACKUP_TARGET=/aussenhaus
MANBAN_BACKUP_RETENTION_DAYS=7
# Bewusst weit: Die Probe loest jeden Sicherungslauf selbst aus und prueft danach sein Ergebnis.
# Ein Takt, der nebenher laeuft, koennte genau in diesem Moment die Sperre halten — der erzwungene
# Lauf wuerde dann uebersprungen, und die Probe pruefte den Stand eines fremden Laufs.
MANBAN_BACKUP_MIRROR_INTERVAL=PT1H
MANBAN_BACKUP_BASE_CRON=0 0 3 * * *
ENV

# ---------------------------------------------------------------------------
# Kleine Helfer
# ---------------------------------------------------------------------------

# Ein Wert aus einer JSON-Antwort (von stdin). Der Pfad ist eine Punktkette, Indizes als Zahl:
#   id      0.id      columns.0.id      missingObjects.length
json_wert() {
  node -e '
    let roh = "";
    process.stdin.on("data", (stueck) => (roh += stueck));
    process.stdin.on("end", () => {
      let wert = JSON.parse(roh);
      for (const teil of process.argv[1].split(".")) {
        if (wert === null || wert === undefined) break;
        wert = wert[teil];
      }
      if (wert === null || wert === undefined) {
        process.stderr.write("Pfad " + process.argv[1] + " fehlt in der Antwort\n");
        process.exit(3);
      }
      process.stdout.write(String(wert));
    });
  ' "$1"
}

# Die Titel aller Karten einer Liste, je Zeile einer.
json_titel() {
  node -e '
    let roh = "";
    process.stdin.on("data", (stueck) => (roh += stueck));
    process.stdin.on("end", () => {
      const liste = JSON.parse(roh);
      process.stdout.write(liste.map((karte) => String(karte.title)).join("\n") + "\n");
    });
  '
}

# Ein API-Aufruf mit Sitzungs-Cookie. --fail-with-body macht aus einem 4xx/5xx einen Fehlschlag und
# zeigt trotzdem die Fehlermeldung der Anwendung — sonst stuende dort nur eine Statuszahl.
api() {
  local methode=$1 pfad=$2 daten=${3-}
  local -a args=(
    --silent --show-error --fail-with-body
    --cookie "$KEKSE" --cookie-jar "$KEKSE"
    --request "$methode" "$BASIS_URL$pfad"
  )
  if [ -n "$daten" ]; then
    args+=(--header 'Content-Type: application/json' --data "$daten")
  fi
  curl "${args[@]}"
}

psql_probe() {
  compose exec -T postgres psql --username="$PG_USER" --dbname="$PG_DB" \
    --no-psqlrc --quiet --tuples-only --no-align --set=ON_ERROR_STOP=1 --command="$1" \
    | tr -d ' \r'
}

# Ein Zeitpunkt aus der Uhr der Datenbank, in der Form, die restore.sh verlangt: UTC, auf die
# Sekunde. Das Argument ist ein Versatz wie '0 second' oder '1 day'.
#
# Aus der Datenbank und nicht vom Rechner, weil es dieselbe Uhr sein muss, die auch die WAL-
# Eintraege stempelt — in einer Container-Umgebung koennen die beiden auseinanderliegen.
db_zeitpunkt() {
  psql_probe "SELECT to_char(date_trunc('second', now() AT TIME ZONE 'UTC')
                               + interval '$1', 'YYYY-MM-DD\"T\"HH24:MI:SS\"Z\"')"
}

# Wie viele Dateien unter einem Unterverzeichnis des Ziels ausser Haus liegen.
ziel_anzahl() {
  compose exec -T manban-backup \
    sh -c "find /aussenhaus/$1 -type f 2>/dev/null | wc -l" | tr -d ' \r'
}

# 'da' oder 'weg' fuer eine einzelne Datei am Ziel ausser Haus.
ziel_datei() {
  compose exec -T manban-backup \
    sh -c "test -f /aussenhaus/$1 && echo da || echo weg" | tr -d ' \r'
}

# Wie oft eine Zeichenkette im Sicherungs-Container vorkommt — in seinen Dateien und in seiner
# Umgebung. Der Nachweis zu E3/AK8 haengt daran: Dort darf kein privater Schluessel liegen.
container_dateitreffer() {
  compose exec -T manban-backup sh -c \
    "grep -rl '$1' /sicherungen /etc/manban-backup /usr/local/bin 2>/dev/null | wc -l" | tr -d ' \r'
}

container_umgebungstreffer() {
  compose exec -T manban-backup sh -c "env | grep -c '$1' || true" | tr -d ' \r'
}

# Wie viel im Datenverzeichnis der Datenbank liegt — die Gegenprobe zu "es wird nichts eingespielt".
pgdata_anzahl() {
  compose run --rm --no-deps -T \
    --volume "$VOL_PG:/wiederherstellung/pgdata" \
    --entrypoint sh manban-backup \
    -c 'find /wiederherstellung/pgdata -mindepth 1 | wc -l' | tr -d ' \r'
}

rueckholung() {
  compose run --rm --no-deps -T \
    --volume "$VOL_PG:/wiederherstellung/pgdata" \
    --volume "$SCHLUESSEL:/schluessel.key:ro" \
    --entrypoint restore.sh manban-backup \
    "$1" --quelle aussenhaus --schluessel /schluessel.key
}

# ---------------------------------------------------------------------------
# 1/7 — Stack hochfahren und Instanz befuellen
# ---------------------------------------------------------------------------

schritt '1/7 Stack hochfahren und Instanz befuellen'

# Reste eines abgebrochenen frueheren Laufs zuerst weg, sonst erbt diese Probe deren Daten.
compose down --volumes --remove-orphans > /dev/null 2>&1 || true
docker volume rm -f \
  "$VOL_PG" "$VOL_MINIO" "$VOL_WAL" "$VOL_SICHERUNG" "$VOL_AUSSENHAUS" > /dev/null 2>&1 || true

log 'Sicherungs-Abbild bauen …'
compose build manban-backup

# Das Schluesselpaar entsteht hier und nur hier. Der oeffentliche Teil geht in die Umgebung des
# Stacks, der private bleibt im Arbeitsraum ausserhalb — genau die Aufteilung aus AK8.
log 'age-Schluesselpaar erzeugen …'
compose run --rm --no-deps -T --entrypoint age-keygen manban-backup > "$SCHLUESSEL" 2> /dev/null
chmod 0600 "$SCHLUESSEL"
EMPFAENGER=$(grep -o 'age1[0-9a-z]*' "$SCHLUESSEL" | head -1)
if [ -z "$EMPFAENGER" ]; then
  log 'FEHLER age-keygen hat keinen Empfaenger geliefert.'
  exit 1
fi
printf 'MANBAN_BACKUP_AGE_RECIPIENT=%s\n' "$EMPFAENGER" >> "$ENVDATEI"

log 'Stack hochfahren (das erste Mal dauert es, die Anwendung wird gebaut) …'
compose up -d --build --wait --wait-timeout 900 "${DIENSTE[@]}"

for volume in "$VOL_PG" "$VOL_MINIO" "$VOL_WAL" "$VOL_SICHERUNG" "$VOL_AUSSENHAUS"; do
  if ! docker volume inspect "$volume" > /dev/null 2>&1; then
    log "FEHLER Volume '$volume' gibt es nicht — der Projektname '$PROJEKT' passt nicht zu den"
    log '       erwarteten Namen. Ohne sie kann Schritt 4 nichts verwerfen.'
    exit 1
  fi
done

"$WURZEL/scripts/warte-auf-bereitschaft.sh" "$BASIS_URL/" 300

api POST /api/auth/register \
  "{\"email\":\"$EMAIL\",\"password\":\"$KENNWORT\",\"displayName\":\"Rueckholprobe\"}" > /dev/null
# Der Mailversand ist aus (der ausgelieferte Zustand), der Verifikations-Link kaeme also nirgends
# an. Fuer die Probe zaehlt nicht der Weg dorthin, sondern dass der Nutzer danach wieder da ist.
psql_probe "UPDATE app_user SET email_verified = true WHERE email = '$EMAIL'" > /dev/null
api POST /api/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$KENNWORT\"}" > /dev/null
api POST /api/admin/bootstrap "{\"token\":\"$BOOTSTRAP_TOKEN\"}" > /dev/null

PROJEKT_ID=$(api POST /api/projects \
  "{\"name\":\"Rueckholprobe\",\"ownerEmail\":\"$EMAIL\"}" | json_wert id)
BOARD_ID=$(api GET "/api/projects/$PROJEKT_ID/boards" | json_wert 0.id)
SPALTE_ID=$(api GET "/api/boards/$BOARD_ID" | json_wert columns.0.id)
KARTE_ID=$(api POST "/api/boards/$BOARD_ID/cards" \
  "{\"columnId\":$SPALTE_ID,\"title\":\"$TITEL_VORHER\"}" | json_wert id)

# Bekannter Inhalt heisst hier: zufaellig erzeugt, aber aufgehoben. Gegen eine feste Zeichenkette
# koennte der Vergleich am Ende auch dann zutreffen, wenn irgendetwas anderes zurueckkaeme.
head -c 65536 /dev/urandom > "$ANHANG"
ANHANG_ID=$(curl --silent --show-error --fail-with-body \
  --cookie "$KEKSE" --cookie-jar "$KEKSE" \
  --form "file=@$ANHANG;filename=probe.bin" \
  "$BASIS_URL/api/cards/$KARTE_ID/attachments" | json_wert id)
log "Projekt $PROJEKT_ID, Board $BOARD_ID, Karte $KARTE_ID, Anhang $ANHANG_ID."

# ---------------------------------------------------------------------------
# 2/7 — Zeitpunkt merken, Sicherung erzwingen, Kopie ausser Haus abwarten
# ---------------------------------------------------------------------------

schritt '2/7 Sicherung erzwingen und Zeitpunkt merken'

# Die Basissicherung zuerst, dann der Zeitpunkt: Die Rueckholung waehlt die juengste Basis VOR dem
# Ziel (restore.sh, basis_waehlen). Eine Basis, die nach dem Zielzeitpunkt begonnen hat, kaeme fuer
# ihn nicht in Frage — und eine leere Basis vor dem Befuellen wuerde die Probe um ihren Kern
# bringen, weil dann alles allein aus dem WAL kaeme.
compose exec -T manban-backup backup.sh basis
pruefe 'Basissicherung ausser Haus angekommen' "$(ziel_anzahl basis)" 1
gruppe_auswerten

# Zwei Sekunden Abstand, bevor der Zeitpunkt genommen wird: Er wird auf ganze Sekunden
# abgeschnitten, und alles bisher Angelegte muss sicher davor liegen.
sleep 2
ZEITPUNKT=$(db_zeitpunkt '0 second')
# Der Zeitpunkt fuer die Gegenprobe zu E5: weit hinter jedem Anhang-Spiegel, den dieser Lauf noch
# erzeugen kann.
ZU_SPAET=$(db_zeitpunkt '1 day')
log "Zielzeitpunkt der Rueckholung: $ZEITPUNKT"

# Der Anhang-Spiegel danach: Sein Stand ist die Obergrenze jeder Rueckholung (E5). Liefe er vor dem
# Zeitpunkt, wiese restore.sh den eigenen Zielzeitpunkt der Probe ab.
compose exec -T manban-backup backup.sh spiegel
pruefe 'Anhang ausser Haus angekommen' "$(ziel_anzahl spiegel)" 1
pruefe 'Stand des Spiegels ausser Haus abgelegt' "$(ziel_datei spiegel-stand.age)" da

# Gegenprobe E3: Die Sicherung ist entstanden, und im ganzen Container liegt trotzdem kein privater
# Schluessel — weder in einer Datei noch in der Umgebung. Waere es anders, koennte der Server seine
# eigene Kopie ausser Haus lesen, und AK8 waere eine Behauptung.
pruefe 'kein privater Schluessel in den Dateien des Sicherungs-Containers' \
  "$(container_dateitreffer AGE-SECRET-KEY)" 0
pruefe 'kein privater Schluessel in der Umgebung des Sicherungs-Containers' \
  "$(container_umgebungstreffer AGE-SECRET-KEY)" 0
gruppe_auswerten

# ---------------------------------------------------------------------------
# 3/7 — Weitere Karte; sie muss nach der Rueckholung fehlen
# ---------------------------------------------------------------------------

schritt '3/7 Weitere Karte anlegen und das WAL ausser Haus bringen'

sleep 2
api POST "/api/boards/$BOARD_ID/cards" \
  "{\"columnId\":$SPALTE_ID,\"title\":\"$TITEL_NACHHER\"}" > /dev/null

# Ohne diesen Segmentwechsel haengt der juengste WAL-Abschnitt im Datenverzeichnis fest. Die
# Rueckholung faende dann kein WAL, das ueber den Zielzeitpunkt hinausreicht, und Postgres brauchte
# ihn erst gar nicht anzusteuern — die Wiederherstellung endete vor dem Ziel.
psql_probe 'SELECT pg_switch_wal()' > /dev/null
sleep 3
compose exec -T manban-backup backup.sh wal
WAL_ANZAHL=$(ziel_anzahl wal)
if [ "$WAL_ANZAHL" -lt 1 ]; then
  log 'FEHLER Kein WAL-Segment ausser Haus — die Rueckholung koennte den Zeitpunkt nicht erreichen.'
  exit 1
fi
log "$WAL_ANZAHL WAL-Segmente ausser Haus."

if [ "$SABOTAGE" = ja ]; then
  log 'Sabotage: der Anhang in der Kopie ausser Haus wird verfaelscht.'
  compose exec -T manban-backup sh -c '
    set -e
    datei=$(find /aussenhaus/spiegel -type f -name "*.age" | sort | head -1)
    [ -n "$datei" ] || { echo "kein Anhang am Ziel"; exit 1; }
    printf "verfaelscht\n" > /tmp/sabotage
    age --recipient "$MANBAN_BACKUP_AGE_RECIPIENT" --output "$datei.neu" /tmp/sabotage
    mv "$datei.neu" "$datei"
    rm -f /tmp/sabotage
  '
fi

# ---------------------------------------------------------------------------
# 4/7 — Die leere Maschine
# ---------------------------------------------------------------------------

schritt '4/7 Datenbank, Objektspeicher, WAL-Archiv und oertliche Sicherung verwerfen'

# Verworfen wird mehr, als AK4 mindestens verlangt: auch das WAL-Archiv und die oertliche
# Sicherung. Blieben sie stehen, koennte die Rueckholung sich daraus bedienen, und die
# verschluesselte Kopie ausser Haus — der einzige Weg auf eine wirklich leere Maschine — waere
# ungeprueft. Uebrig bleibt allein das Ziel ausser Haus.
compose down --remove-orphans
docker volume rm -f "$VOL_PG" "$VOL_MINIO" "$VOL_WAL" "$VOL_SICHERUNG" > /dev/null
log 'Datenbank-, Objektspeicher-, WAL- und Sicherungs-Volume sind weg.'

# ---------------------------------------------------------------------------
# 5/7 — Rueckholung aus der verschluesselten Kopie
# ---------------------------------------------------------------------------

schritt '5/7 Rueckholung aus der verschluesselten Kopie ausser Haus'

# Der Objektspeicher muss stehen, bevor zurueckgeholt wird: restore.sh schreibt die Anhaenge
# hinein. Die Datenbank bleibt aus — ihr Datenverzeichnis entsteht gerade erst.
compose up -d --wait --wait-timeout 300 minio

# Gegenprobe E5, vor der echten Rueckholung und mit leerem Datenverzeichnis: Ein Zeitpunkt hinter
# dem letzten Anhang-Spiegel wird abgewiesen, und es wird nichts eingespielt.
e5_stand=0
rueckholung "$ZU_SPAET" > "$RAUM/e5.log" 2>&1 || e5_stand=$?
if [ "$e5_stand" -ne 0 ]; then e5_ergebnis=abgewiesen; else e5_ergebnis=eingespielt; fi
pruefe 'Zeitpunkt hinter dem Anhang-Spiegel wird abgewiesen' "$e5_ergebnis" abgewiesen
pruefe 'dabei wird nichts eingespielt' "$(pgdata_anzahl)" 0
gruppe_auswerten

rueckholung "$ZEITPUNKT"

compose up -d --wait --wait-timeout 300 postgres

# Postgres faehrt jetzt das WAL bis zum Zielzeitpunkt nach und wird danach von selbst schreibend
# (recovery_target_action=promote). Solange das laeuft, weist es Verbindungen ab oder antwortet
# lesend — erst danach steht der Stand, den die Probe prueft.
log 'Warten, bis die Wiederherstellung durch ist und die Datenbank schreibend wird …'
schreibend=nein
for _ in $(seq 1 90); do
  if [ "$(psql_probe 'SELECT pg_is_in_recovery()' 2> /dev/null)" = f ]; then
    schreibend=ja
    break
  fi
  sleep 2
done
pruefe 'Datenbank ist nach der Wiederherstellung schreibend' "$schreibend" ja
gruppe_auswerten

compose up -d manban-api
"$WURZEL/scripts/warte-auf-bereitschaft.sh" "$BASIS_URL/" 300

# ---------------------------------------------------------------------------
# 6/7 — Was danach da sein muss, und was nicht
# ---------------------------------------------------------------------------

schritt '6/7 Stand nach der Rueckholung pruefen'

rm -f "$KEKSE"
api POST /api/auth/login "{\"email\":\"$EMAIL\",\"password\":\"$KENNWORT\"}" > /dev/null

titel=$(api GET "/api/boards/$BOARD_ID/cards" | json_titel)
pruefe 'Karte von vor dem Zeitpunkt ist wieder da' \
  "$(printf '%s\n' "$titel" | grep -c -x "$TITEL_VORHER" || true)" 1
pruefe 'die danach angelegte Karte fehlt' \
  "$(printf '%s\n' "$titel" | grep -c -x "$TITEL_NACHHER" || true)" 0

# Der Download darf scheitern, ohne den Lauf hier abzubrechen: Ein verfaelschter Anhang kann schon
# beim Holen auffallen (die Groesse aus den Metadaten passt dann nicht zum Objekt). Beides ist ein
# Fehlschlag dieser Gruppe, und beides soll man sehen.
geholt=nein
if curl --silent --show-error --fail-with-body --cookie "$KEKSE" --cookie-jar "$KEKSE" \
  --output "$ZURUECK" "$BASIS_URL/api/attachments/$ANHANG_ID" > /dev/null 2>&1; then
  geholt=ja
fi
pruefe 'Anhang laesst sich herunterladen' "$geholt" ja
if [ "$geholt" = ja ] && cmp -s "$ANHANG" "$ZURUECK"; then
  gleich=ja
else
  gleich=nein
fi
pruefe 'Anhang ist byteweise derselbe' "$gleich" ja

fehlende=$(api GET /api/admin/storage/reconciliation | json_wert missingObjects.length)
pruefe 'Abgleich meldet keine fehlenden Objekte' "$fehlende" 0
gruppe_auswerten

log ''
log 'Alle Pruefungen bestanden — die Rueckholung auf einen Zeitpunkt funktioniert.'
log "Zielzeitpunkt war $ZEITPUNKT."
