#!/usr/bin/env bash
# backup.sh — Sicherungsdienst von manban (Issue #829, Plan #825).
#
# Aufrufe:
#   backup.sh dienst    Dauerbetrieb (Vorgabe des Abbilds): Takt der Basissicherung ueber cron,
#                       Anhang-Spiegel und WAL-Versand im Takt von MANBAN_BACKUP_MIRROR_INTERVAL
#   backup.sh basis     eine Basissicherung samt Kopie ausser Haus, sofort
#   backup.sh spiegel   ein Anhang-Spiegel samt Kopie ausser Haus, sofort
#   backup.sh wal       die noch nicht versandten WAL-Segmente ausser Haus bringen, sofort
#
# Von Hand ausloesen:  docker compose exec manban-backup backup.sh basis
#
# Vier Arten von Laeufen, so wie sie `backup_run` kennt (V40__backup_run.sql):
#   basis    pg_basebackup in die oertliche Sicherung        — Takt: MANBAN_BACKUP_BASE_CRON
#   offsite  dieselbe Basissicherung verschluesselt ans Ziel — Takt: MANBAN_BACKUP_BASE_CRON
#   spiegel  Anhaenge aus MinIO oertlich und ans Ziel        — Takt: MANBAN_BACKUP_MIRROR_INTERVAL
#   wal      WAL-Segmente aus dem Archiv ans Ziel            — Takt: MANBAN_BACKUP_MIRROR_INTERVAL
# Die Warnschwellen der Anwendung leiten sich aus genau diesen beiden Takten ab
# (BackupProperties#warnAfter, E13) — deshalb wird jeder Takt protokolliert, auch wenn er nichts zu
# tun fand. Ein stiller Lauf ist ein gelungener Lauf, kein ausgefallener.
#
# Die oertliche Sicherung liegt unverschluesselt, die Kopie ausser Haus verschluesselt. Das ist
# Absicht (E3, AK6, AK8): Der schnelle Weg zurueck braucht keinen Schluessel, und der Server haelt
# nur den oeffentlichen `age`-Empfaenger — seine eigene Kopie ausser Haus kann er nicht lesen.
set -euo pipefail

ARCHIV=/wal-archiv
SICHERUNG=/sicherungen
BASIS_DIR="$SICHERUNG/basis"
SPIEGEL_DIR="$SICHERUNG/spiegel"
VERSANDT_DIR="$SICHERUNG/versandt"
UMGEBUNG="$SICHERUNG/.umgebung"
CRON_DATEI=/etc/cron.d/manban-backup
MC_ALIAS=manban

# Bis wohin der Anhang-Spiegel reicht — ein Stempel wie 20260923T075500Z, geschrieben nach jedem
# gelungenen Spiegel-Lauf und mit derselben Datei ausser Haus abgelegt.
#
# Warum diese Datei sein muss (E5, Issue #830): restore.sh weist einen Rueckholzeitpunkt ab, der
# hinter dem letzten Spiegel liegt. Den Zeitpunkt aus `backup_run` zu lesen, hilft dabei nicht: Die
# Rueckholung auf eine leere Maschine ist der Fall, fuer den die Pruefung gebaut ist, und dort gibt
# es keine Datenbank, die man fragen koennte. Aus den Dateizeiten im Spiegel laesst er sich auch
# nicht ableiten — sie sagen, wann ein Anhang hochgeladen wurde, nicht wann der Spiegel lief.
SPIEGEL_STAND="$SICHERUNG/spiegel-stand"
SPIEGEL_STAND_ZIEL=spiegel-stand.age

# Laengstes Stueck eines Fehlertextes, das in backup_run.detail (varchar(4000)) passt.
DETAIL_MAX=3900

log() {
  printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*"
}

jetzt() {
  date -u +'%Y-%m-%d %H:%M:%S+00'
}

# Das Ende eines Fehlertextes — die Ursache steht bei psql, pg_basebackup und rclone unten. Ein
# Fehlschlag ohne Grund waere im Protokoll nutzlos, deshalb der Ersatztext.
kurzfassung() {
  local datei=$1
  if [ -s "$datei" ]; then
    tail -c "$DETAIL_MAX" "$datei" | tr -d '\000' | tr '\r' ' '
  else
    printf 'ohne Meldung'
  fi
}

# ---------------------------------------------------------------------------
# Umgebung
# ---------------------------------------------------------------------------

# Die Werte, die ein Lauf braucht. cron startet backup.sh mit fast leerer Umgebung; `dienst` legt
# sie deshalb einmal ab, und jeder andere Aufruf liest sie. Die Datei traegt das Datenbankkennwort
# und bekommt 0600 — sie liegt auf dem Sicherungs-Volume, das nur dieser Container sieht.
UMGEBUNGSFELDER=(
  POSTGRES_HOST POSTGRES_PORT POSTGRES_DB POSTGRES_USER POSTGRES_PASSWORD
  MANBAN_MINIO_ENDPOINT MANBAN_MINIO_BUCKET MINIO_ROOT_USER MINIO_ROOT_PASSWORD
  MANBAN_BACKUP_TARGET MANBAN_BACKUP_AGE_RECIPIENT MANBAN_BACKUP_RETENTION_DAYS
  MANBAN_BACKUP_MIRROR_INTERVAL MANBAN_BACKUP_BASE_CRON
  RCLONE_CONFIG MC_CONFIG_DIR
)

umgebung_sichern() {
  local name
  : > "$UMGEBUNG"
  chmod 0600 "$UMGEBUNG"
  for name in "${UMGEBUNGSFELDER[@]}"; do
    printf 'export %s=%q\n' "$name" "${!name-}" >> "$UMGEBUNG"
  done
}

umgebung_laden() {
  if [ -f "$UMGEBUNG" ]; then
    # shellcheck source=/dev/null
    . "$UMGEBUNG"
  fi
}

vorgaben_setzen() {
  : "${POSTGRES_HOST:=postgres}"
  : "${POSTGRES_PORT:=5432}"
  : "${POSTGRES_DB:=manban}"
  : "${POSTGRES_USER:=manban}"
  : "${MANBAN_MINIO_ENDPOINT:=http://minio:9000}"
  : "${MANBAN_MINIO_BUCKET:=manban}"
  : "${MANBAN_BACKUP_MIRROR_INTERVAL:=PT5M}"
  : "${MANBAN_BACKUP_BASE_CRON:=0 0 3 * * *}"
  : "${MC_CONFIG_DIR:=$SICHERUNG/.mc}"
  export MC_CONFIG_DIR
}

# Fehlt eines der uebergebenen Felder, laeuft der Aufruf nicht — und soll das laut sagen, statt in
# einen Takt zu gehen, der jedes Mal scheitert.
#
# Welche Felder das sind, sagt der Aufrufer: Der Sicherungsdienst braucht alle fuenf, das Verfallen
# in retention.sh kommt ohne MANBAN_BACKUP_AGE_RECIPIENT aus — es verschluesselt nichts und soll
# darum auch nichts davon verlangen (AK7 in Issue #830).
pflichtfelder_pruefen() {
  local name
  local -a fehlend=()
  for name in "$@"; do
    [ -n "${!name-}" ] || fehlend+=("$name")
  done
  if [ ${#fehlend[@]} -gt 0 ]; then
    log "FEHLER Ohne ${fehlend[*]} geht es nicht weiter — siehe .env.example und docs/backup.md."
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Takt
# ---------------------------------------------------------------------------

# ISO-8601-Dauer wie PT5M in Sekunden. Dieselbe Schreibweise wie in application.yml: Rhythmus und
# Warnschwelle lesen denselben Wert, sonst koennte die Anwendung Alarm schlagen, waehrend der
# Container puenktlich ist.
takt_sekunden() {
  local rest=$1 zahl einheit gesamt=0
  case $rest in
    PT* | pt*) rest=${rest:2} ;;
    *) return 1 ;;
  esac
  [ -n "$rest" ] || return 1
  while [ -n "$rest" ]; do
    zahl=${rest%%[HhMmSs]*}
    case $zahl in '' | *[!0-9]*) return 1 ;; esac
    rest=${rest#"$zahl"}
    einheit=${rest:0:1}
    rest=${rest:1}
    case $einheit in
      H | h) gesamt=$((gesamt + zahl * 3600)) ;;
      M | m) gesamt=$((gesamt + zahl * 60)) ;;
      S | s) gesamt=$((gesamt + zahl)) ;;
      *) return 1 ;;
    esac
  done
  [ "$gesamt" -gt 0 ] || return 1
  printf '%s\n' "$gesamt"
}

# Spring zaehlt sechs Felder und beginnt bei den Sekunden, cron zaehlt fuenf und beginnt bei den
# Minuten. Das Sekundenfeld wird verworfen: Eine Basissicherung auf die Sekunde genau zu starten,
# ergibt keinen Sinn — dieselbe Variable in beiden Welten lesen zu koennen, schon.
cron_fuenf_felder() {
  local -a felder
  read -r -a felder <<< "$1"
  case ${#felder[@]} in
    6) printf '%s %s %s %s %s\n' "${felder[@]:1}" ;;
    5) printf '%s %s %s %s %s\n' "${felder[@]}" ;;
    *) return 1 ;;
  esac
}

cron_einrichten() {
  local felder
  if ! felder=$(cron_fuenf_felder "$MANBAN_BACKUP_BASE_CRON"); then
    log "FEHLER MANBAN_BACKUP_BASE_CRON='$MANBAN_BACKUP_BASE_CRON' hat weder fuenf noch sechs Felder."
    return 1
  fi
  # Die Ausgabe geht auf die Kanaele von PID 1 und damit in das Protokoll des Containers; sonst
  # schriebe cron sie an eine lokale Mailzustellung, die es hier nicht gibt.
  #
  # Das Verfallen haengt am selben Takt und laeuft unmittelbar nach der Basissicherung — mit
  # Semikolon, nicht mit `&&`: Gerade wenn die Sicherung scheitert, muss weiter geraeumt werden,
  # sonst laeuft das Sicherungs-Volume ausgerechnet waehrend einer Stoerung voll. Ein eigener Takt
  # daneben waere ein zweiter Ort fuer dieselbe Einstellung.
  {
    printf 'SHELL=/bin/bash\n'
    printf 'PATH=/usr/local/bin:/usr/bin:/bin\n'
    printf '%s root { /usr/local/bin/backup.sh basis; /usr/local/bin/retention.sh; }' "$felder"
    printf ' >/proc/1/fd/1 2>/proc/1/fd/2\n'
  } > "$CRON_DATEI"
  chmod 0644 "$CRON_DATEI"
  log "Basissicherung und Verfallen im Takt '$felder' (aus MANBAN_BACKUP_BASE_CRON='$MANBAN_BACKUP_BASE_CRON')."
}

# ---------------------------------------------------------------------------
# Protokoll (E2)
# ---------------------------------------------------------------------------

psql_aufruf() {
  PGPASSWORD="${POSTGRES_PASSWORD-}" psql \
    --host="$POSTGRES_HOST" --port="$POSTGRES_PORT" \
    --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" \
    --no-password --quiet --no-psqlrc --set=ON_ERROR_STOP=1 "$@"
}

# Eine Zeile je Lauf, auch bei Fehlschlag und dann mit Grund.
#
# Scheitert das INSERT, ist das eine Warnung und kein Fehlschlag der Sicherung (E2): Auf einer
# frischen Instanz oder mitten in einer Rueckholung laeuft dieser Container, bevor die Anwendung je
# migriert hat. Die Sicherung ist dann trotzdem entstanden — sie als gescheitert zu melden, waere
# schlicht falsch.
protokoll() {
  local art=$1 beginn=$2 ausgang=$3 grund=${4-} bytes=${5-} meldung
  if ! meldung=$(psql_aufruf \
    --set=art="$art" --set=beginn="$beginn" --set=ende="$(jetzt)" \
    --set=ausgang="$ausgang" --set=grund="$grund" --set=bytes="$bytes" \
    --command="INSERT INTO backup_run (kind, started_at, finished_at, outcome, detail, bytes)
               VALUES (:'art', :'beginn'::timestamptz, :'ende'::timestamptz, :'ausgang',
                       NULLIF(:'grund', ''), NULLIF(:'bytes', '')::bigint)" 2>&1); then
    log "WARN Protokollzeile ($art/$ausgang) nicht geschrieben — der Lauf gilt trotzdem als $ausgang: $meldung"
  fi
}

# ---------------------------------------------------------------------------
# Kopie ausser Haus
# ---------------------------------------------------------------------------

ziel_pfad() {
  printf '%s/%s' "${MANBAN_BACKUP_TARGET%/}" "$1"
}

# Verschluesselt eine Datei gegen den oeffentlichen Empfaenger und legt sie am Ziel ab; gibt die
# Groesse der verschluesselten Datei aus. `age --recipient` braucht allein den oeffentlichen
# Schluessel — der private liegt nicht im Abbild und wird hier nie gelesen (E3, AK8).
aussenhaus() {
  local quelle=$1 zielname=$2 zwischen groesse='' stand=0
  # ${BASHPID:-$$}: Im Container laeuft bash 5 und kennt BASHPID. Die Proben laufen auch auf einem
  # Apple-Rechner, und dessen bash 3.2 kennt es nicht — ohne Ersatzwert braechen sie an `set -u`.
  zwischen="$SICHERUNG/.versand.${BASHPID:-$$}"
  age --recipient "$MANBAN_BACKUP_AGE_RECIPIENT" --output "$zwischen" "$quelle" || stand=$?
  if [ "$stand" -eq 0 ]; then
    rclone copyto "$zwischen" "$(ziel_pfad "$zielname")" || stand=$?
  fi
  if [ "$stand" -eq 0 ]; then
    groesse=$(stat -c %s "$zwischen") || stand=$?
  fi
  rm -f "$zwischen"
  [ "$stand" -eq 0 ] || return "$stand"
  printf '%s\n' "$groesse"
}

# Alle Dateien unter einem Verzeichnis, die noch nicht ausser Haus sind, dorthin bringen. Die Marke
# haelt fest, was erledigt ist: Anhaenge und WAL-Segmente sind unveraenderlich, ein Pfad genuegt
# also als Merkmal. Gibt "Anzahl Bytes" aus.
#
# Jeder Schritt wird einzeln geprueft: In einer Funktion, deren Rueckgabewert der Aufrufer testet,
# greift `set -e` nicht — ein gescheitertes `age` liefe sonst stillschweigend weiter und setzte am
# Ende die Marke fuer eine Datei, die nie ankam.
verzeichnis_versenden() {
  local wurzel=$1 markenraum=$2 zielraum=$3
  local datei rel marke groesse anzahl=0 bytes=0
  if [ ! -d "$wurzel" ]; then
    printf '0 0\n'
    return 0
  fi
  while IFS= read -r datei; do
    rel=${datei#"$wurzel"/}
    marke="$VERSANDT_DIR/$markenraum/$rel"
    if [ -f "$marke" ]; then
      continue
    fi
    if ! groesse=$(aussenhaus "$datei" "$zielraum/$rel.age"); then
      return 1
    fi
    mkdir -p "$(dirname "$marke")" || return 1
    : > "$marke" || return 1
    anzahl=$((anzahl + 1))
    bytes=$((bytes + groesse))
  done < <(find "$wurzel" -type f | sort)
  printf '%s %s\n' "$anzahl" "$bytes"
}

# ---------------------------------------------------------------------------
# Die vier Laeufe
# ---------------------------------------------------------------------------

# Der Zeitstempel im Namen einer Basissicherung, oder Rueckgabe 1, wenn der Name keiner ist.
#
# Hier gebildet, weil hier auch der Name entsteht: Rueckholung (restore.sh) und Verfallen
# (retention.sh) lesen ihn beide, und zwei Auslegungen desselben Namens waeren eine Wahrheit zu
# viel. Der Stempel ist lexikografisch sortierbar und damit auch vergleichbar — ohne `date`, das
# ausserhalb des Containers anders rechnet.
#
# Pfad, blosser Name und die Endung .age der Kopie ausser Haus sind alle drei zulaessig.
basis_stempel() {
  local name=${1##*/} stempel
  case $name in
    basis-*) stempel=${name#basis-} ;;
    *) return 1 ;;
  esac
  stempel=${stempel%%.*}
  case $stempel in
    [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]T[0-9][0-9][0-9][0-9][0-9][0-9]Z) ;;
    *) return 1 ;;
  esac
  printf '%s\n' "$stempel"
}

basis_lauf() {
  local beginn ziel stempel groesse fehler="$SICHERUNG/.fehler-basis"
  beginn=$(jetzt)
  mkdir -p "$BASIS_DIR"
  stempel=$(date -u +%Y%m%dT%H%M%SZ)
  ziel="$BASIS_DIR/basis-$stempel.tar.gz"

  # --wal-method=fetch statt none: Die oertliche Sicherung soll fuer sich allein einspielbar sein,
  # ohne auf das WAL-Archiv angewiesen zu sein — das ist der schnelle Weg zurueck aus AK6.
  if ! PGPASSWORD="$POSTGRES_PASSWORD" pg_basebackup \
    --host="$POSTGRES_HOST" --port="$POSTGRES_PORT" --username="$POSTGRES_USER" \
    --no-password --format=tar --gzip --wal-method=fetch --pgdata=- \
    > "$ziel.teil" 2> "$fehler"; then
    rm -f "$ziel.teil"
    log "FEHLER Basissicherung gescheitert."
    protokoll basis "$beginn" fehlschlag "$(kurzfassung "$fehler")"
    return 1
  fi
  mv "$ziel.teil" "$ziel"
  groesse=$(stat -c %s "$ziel")
  log "Basissicherung $ziel ($groesse Bytes)."
  protokoll basis "$beginn" erfolg "$ziel" "$groesse"

  offsite_lauf "$ziel"
}

offsite_lauf() {
  local quelle=$1 beginn groesse zielname fehler="$SICHERUNG/.fehler-offsite"
  beginn=$(jetzt)
  zielname="basis/$(basename "$quelle").age"
  if ! groesse=$(aussenhaus "$quelle" "$zielname" 2> "$fehler"); then
    log "FEHLER Kopie ausser Haus gescheitert."
    protokoll offsite "$beginn" fehlschlag "$(kurzfassung "$fehler")"
    return 1
  fi
  log "Basissicherung verschluesselt ausser Haus ($groesse Bytes)."
  protokoll offsite "$beginn" erfolg "$(ziel_pfad "$zielname")" "$groesse"
}

spiegel_lauf() {
  local beginn stempel ergebnis anzahl bytes fehler="$SICHERUNG/.fehler-spiegel"
  beginn=$(jetzt)
  # Der Stand wird VOR dem Spiegeln genommen, nicht danach: Erfasst ist sicher alles, was bei
  # Beginn schon da war. Was waehrend des Laufs hochgeladen wurde, kann fehlen — ein Stand aus dem
  # Nachhinein verspraeche der Rueckholung also mehr, als der Spiegel haelt.
  stempel=$(date -u +%Y%m%dT%H%M%SZ)
  mkdir -p "$SPIEGEL_DIR" "$VERSANDT_DIR/spiegel" "$MC_CONFIG_DIR"

  # Bewusst ohne --remove (E5): Anhaenge sind unveraenderlich, aber nicht unloeschbar. Mit --remove
  # fehlte nach einer Rueckholung auf einen Zeitpunkt vor der Loeschung genau dieses Objekt. Was im
  # Spiegel zuviel liegt, raeumt retention.sh — und nur, wenn es niemand mehr referenziert.
  if ! {
    mc alias set "$MC_ALIAS" "$MANBAN_MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" \
      > /dev/null 2> "$fehler" \
      && mc mirror --quiet --overwrite "$MC_ALIAS/$MANBAN_MINIO_BUCKET" "$SPIEGEL_DIR" \
        > /dev/null 2>> "$fehler"
  }; then
    log "FEHLER Anhang-Spiegel gescheitert."
    protokoll spiegel "$beginn" fehlschlag "$(kurzfassung "$fehler")"
    return 1
  fi

  if ! ergebnis=$(verzeichnis_versenden "$SPIEGEL_DIR" spiegel spiegel 2> "$fehler"); then
    log "FEHLER Anhaenge ausser Haus gescheitert."
    protokoll spiegel "$beginn" fehlschlag "$(kurzfassung "$fehler")"
    return 1
  fi

  # Der Stand wird zuletzt festgeschrieben und gilt damit erst, wenn der Spiegel oertlich UND
  # ausser Haus vollstaendig ist. Scheitert der Versand des Standes, gilt der ganze Lauf als
  # gescheitert: Ein Spiegel ausser Haus, zu dem dort kein Stand liegt, ist fuer die Rueckholung
  # blind — restore.sh muesste ihn entweder blind einspielen oder gar nicht.
  if ! {
    printf '%s\n' "$stempel" > "$SPIEGEL_STAND" \
      && aussenhaus "$SPIEGEL_STAND" "$SPIEGEL_STAND_ZIEL" > /dev/null
  } 2> "$fehler"; then
    log "FEHLER Stand des Anhang-Spiegels nicht festgeschrieben."
    protokoll spiegel "$beginn" fehlschlag "$(kurzfassung "$fehler")"
    return 1
  fi

  read -r anzahl bytes <<< "$ergebnis"
  protokoll spiegel "$beginn" erfolg "$anzahl neue Anhaenge ausser Haus, Stand $stempel" "$bytes"
}

wal_lauf() {
  local beginn ergebnis anzahl bytes fehler="$SICHERUNG/.fehler-wal"
  beginn=$(jetzt)
  mkdir -p "$VERSANDT_DIR/wal"
  if ! ergebnis=$(verzeichnis_versenden "$ARCHIV" wal wal 2> "$fehler"); then
    log "FEHLER WAL-Versand gescheitert."
    protokoll wal "$beginn" fehlschlag "$(kurzfassung "$fehler")"
    return 1
  fi
  read -r anzahl bytes <<< "$ergebnis"
  protokoll wal "$beginn" erfolg "$anzahl neue WAL-Segmente ausser Haus" "$bytes"
}

# ---------------------------------------------------------------------------
# Dienst
# ---------------------------------------------------------------------------

# Zwei Laeufe derselben Art duerfen sich nicht ueberholen: Eine Basissicherung kann laenger dauern
# als der Spiegel-Takt, und cron kennt den laufenden Takt nicht.
mit_sperre() {
  local name=$1
  shift
  (
    if ! flock -n 9; then
      log "WARN '$name' laeuft noch — dieser Takt wird uebersprungen."
      exit 0
    fi
    "$@"
  ) 9> "$SICHERUNG/.sperre-$name"
}

# Das WAL-Archiv ist ein gemeinsames Volume: Postgres schreibt hinein, dieser Container liest und
# raeumt. Ein frisches Volume gehoert root, und der Postgres-Dienst laeuft als postgres — sein
# archive_command scheiterte dann dauerhaft. Beide Abbilder stammen von postgres:16 und teilen
# damit dieselbe Kennung; einmal uebereignen genuegt. Postgres wiederholt ein gescheitertes
# archive_command von sich aus, das kurze Fenster bis hierher heilt also von selbst.
archiv_uebereignen() {
  if ! chown postgres:postgres "$ARCHIV" 2> /dev/null || ! chmod 0750 "$ARCHIV" 2> /dev/null; then
    log "WARN $ARCHIV konnte nicht an postgres uebereignet werden — archive_command kann scheitern."
  fi
}

dienst() {
  local takt
  pflichtfelder_pruefen POSTGRES_PASSWORD MINIO_ROOT_USER MINIO_ROOT_PASSWORD \
    MANBAN_BACKUP_TARGET MANBAN_BACKUP_AGE_RECIPIENT
  if ! takt=$(takt_sekunden "$MANBAN_BACKUP_MIRROR_INTERVAL"); then
    log "FEHLER MANBAN_BACKUP_MIRROR_INTERVAL='$MANBAN_BACKUP_MIRROR_INTERVAL' ist keine Dauer wie PT5M."
    return 1
  fi
  mkdir -p "$BASIS_DIR" "$SPIEGEL_DIR" "$VERSANDT_DIR" "$MC_CONFIG_DIR"
  archiv_uebereignen
  umgebung_sichern
  cron_einrichten
  /usr/sbin/cron
  log "Anhang-Spiegel und WAL-Versand alle ${takt}s (aus MANBAN_BACKUP_MIRROR_INTERVAL='$MANBAN_BACKUP_MIRROR_INTERVAL')."
  log "Ziel ausser Haus: $MANBAN_BACKUP_TARGET"
  while :; do
    mit_sperre spiegel spiegel_lauf || true
    mit_sperre wal wal_lauf || true
    sleep "$takt"
  done
}

# ---------------------------------------------------------------------------

hauptlauf() {
  local befehl=${1:-dienst}
  case $befehl in
    dienst) ;;
    basis | spiegel | wal) umgebung_laden ;;
    *)
      log "FEHLER Unbekannter Aufruf '$befehl'. Erlaubt: dienst, basis, spiegel, wal."
      return 2
      ;;
  esac
  vorgaben_setzen
  case $befehl in
    dienst) dienst ;;
    basis) mit_sperre basis basis_lauf ;;
    spiegel) mit_sperre spiegel spiegel_lauf ;;
    wal) mit_sperre wal wal_lauf ;;
  esac
}

# Nur beim Aufruf laufen, nicht beim Einlesen: So laesst sich das Skript aus einer Pruefung heraus
# laden und einzelne Funktionen fuer sich pruefen (etwa die Umrechnung der Takte), ohne dass dabei
# eine Sicherung anlaeuft.
if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  hauptlauf "$@"
fi
