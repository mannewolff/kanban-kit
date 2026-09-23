#!/usr/bin/env bash
# restore.sh — Rueckholung einer Instanz auf einen Zeitpunkt (Issue #830, Plan #825).
#
# Aufruf:
#   restore.sh <zeitpunkt> [--quelle lokal|aussenhaus] [--schluessel <datei>]
#
#   <zeitpunkt>     UTC in ISO-8601, etwa 2026-09-23T08:00:00Z. Nur UTC — siehe zeitpunkt_stempel.
#   --quelle        lokal (Vorgabe): der schnelle Weg aus /sicherungen des Servers.
#                   aussenhaus: der vollstaendige Weg aus der verschluesselten Kopie am Ziel,
#                   auch auf eine leere Maschine (AK6).
#   --schluessel    privater age-Schluessel; Pflicht bei --quelle aussenhaus, sonst unbenutzt.
#                   Er liegt NICHT im Abbild und wird vom Betreiber ausserhalb des Servers
#                   verwahrt (E3, AK8) — ohne ihn ist aus der Kopie ausser Haus nichts zu holen.
#
# So sieht das von aussen aus — der Postgres-Dienst muss dabei stehen, und sein Datenverzeichnis
# muss leer sein (Volume verworfen oder frisch angelegt):
#
#   docker compose -f docker-compose.yml -f docker-compose.backup.yml stop postgres manban-api
#   docker volume rm <stack>_postgres_data && docker volume create <stack>_postgres_data
#   docker compose -f docker-compose.yml -f docker-compose.backup.yml run --rm \
#     -v <stack>_postgres_data:/wiederherstellung/pgdata \
#     -v /pfad/zum/verwahrten/manban-backup.key:/schluessel.key:ro \
#     --entrypoint restore.sh manban-backup 2026-09-23T08:00:00Z \
#     --quelle aussenhaus --schluessel /schluessel.key
#   docker compose -f docker-compose.yml -f docker-compose.backup.yml up -d
#
# Der Schluessel wird fuer genau diesen einen Aufruf hereingereicht und bleibt danach wieder
# draussen — er gehoert nicht ins Abbild, nicht ins Sicherungs-Volume und nicht nach backup/.
#
# Es gibt bewusst keinen eigenen Compose-Dienst dafuer: Ein Dienst, der das Datenverzeichnis der
# Datenbank schreibbar eingehaengt haelt, liefe im Dauerbetrieb mit — und die Rueckholung ist ein
# Ausnahmevorgang mit stehender Datenbank. `run --rm -v` haengt das Volume genau fuer diesen einen
# Aufruf ein.
#
# Was dieses Skript NICHT tut: die Datenbank starten. Es legt ein einspielbares Datenverzeichnis
# samt recovery.signal hin; das WAL bis zum Zielzeitpunkt faehrt Postgres beim naechsten Start
# selbst nach und wird danach schreibend (recovery_target_action=promote).
#
# Die Grenze nach oben ist der Anhang-Spiegel (E5): Liegt der Zielzeitpunkt dahinter, bricht das
# Skript ab und spielt NICHTS ein. Eine Datenbank, die Karten auf Anhaenge zeigen laesst, die es
# im Objektspeicher nicht gibt, ist kein wiederhergestellter Stand (AK4).

HIER=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=backup.sh
. "$HIER/backup.sh"

# Wohin das wiederhergestellte Datenverzeichnis geschrieben wird. Als Variable, damit die
# Rueckhol-Probe (Issue #832) an denselben Ort zeigen kann, an den der Betreiber sein Volume haengt.
RUECKHOL_PGDATA="${MANBAN_RESTORE_PGDATA:-/wiederherstellung/pgdata}"

# Hier liegt, was unterwegs entschluesselt wird. Auf dem Sicherungs-Volume, das nur dieser
# Container sieht, und mit 0700: Es ist der einzige Ort im ganzen Werk, an dem Klartext aus der
# Kopie ausser Haus entsteht.
ARBEIT="$SICHERUNG/.rueckholung"

RUECKHOL_QUELLE=lokal
RUECKHOL_SCHLUESSEL=''

# ---------------------------------------------------------------------------
# Auslegung des Zielzeitpunkts
# ---------------------------------------------------------------------------

# <zeitpunkt> in die Vergleichsform 20260923T080000Z, oder Rueckgabe 1.
#
# Ausschliesslich UTC, und ausschliesslich auf die Sekunde genau. Zwei Gruende, die beide zaehlen:
#
#   * Die Vorpruefung aus E5 vergleicht diesen Stempel mit dem Stand des Anhang-Spiegels als
#     Zeichenkette. Ein Ortszeit-Offset, der nicht umgerechnet wuerde, hoebe die Pruefung
#     stillschweigend aus — und zwar genau in die falsche Richtung, wenn er positiv ist.
#   * Der Wert landet woertlich zwischen Hochkommata in postgresql.auto.conf. Alles, was nicht
#     strikt gegen ein Muster geprueft wird, ist dort eine Einschleusung.
#
# Die Bereiche werden mitgeprueft, damit ein Vertipper wie 2026-09-32 nicht als ferner Zeitpunkt
# durchgeht, den Postgres erst beim Hochfahren ablehnt — nach dem Entpacken, also zu spaet.
zeitpunkt_stempel() {
  local eingabe=${1-} datum zeit
  case $eingabe in
    [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9][T\ ][0-9][0-9]:[0-9][0-9]:[0-9][0-9]Z) ;;
    *) return 1 ;;
  esac
  datum=${eingabe:0:10}
  zeit=${eingabe:11:8}
  ((10#${datum:5:2} >= 1 && 10#${datum:5:2} <= 12)) || return 1
  ((10#${datum:8:2} >= 1 && 10#${datum:8:2} <= 31)) || return 1
  ((10#${zeit:0:2} <= 23 && 10#${zeit:3:2} <= 59 && 10#${zeit:6:2} <= 59)) || return 1
  printf '%s%s%sT%s%s%sZ\n' \
    "${datum:0:4}" "${datum:5:2}" "${datum:8:2}" "${zeit:0:2}" "${zeit:3:2}" "${zeit:6:2}"
}

# Derselbe Zeitpunkt in der Schreibweise von recovery_target_time.
zeitpunkt_postgres() {
  local stempel
  stempel=$(zeitpunkt_stempel "${1-}") || return 1
  printf '%s-%s-%s %s:%s:%s+00\n' \
    "${stempel:0:4}" "${stempel:4:2}" "${stempel:6:2}" \
    "${stempel:9:2}" "${stempel:11:2}" "${stempel:13:2}"
}

# Die Grenze aus E5: liegt der Zielzeitpunkt hinter dem Stand des Anhang-Spiegels?
#
# Gleichstand ist erlaubt — der Spiegel dieses Augenblicks deckt ihn ab. Beide Argumente sind
# Stempel in UTC und damit als Zeichenkette vergleichbar.
ziel_hinter_spiegel() {
  [ "$1" \> "$2" ]
}

# Aus einer Liste von Namen (stdin, je Zeile einer) die juengste Basissicherung, die NICHT nach dem
# Zielzeitpunkt begonnen hat; Rueckgabe 1, wenn es keine gibt.
#
# Eine juengere zu nehmen, waere eine Rueckholung auf einen anderen Zeitpunkt als den verlangten:
# Aus einer Basissicherung fuehrt nur das WAL nach vorn, nie zurueck.
basis_waehlen() {
  local ziel=$1 name stempel treffer='' bester=''
  while IFS= read -r name; do
    stempel=$(basis_stempel "$name") || continue
    # `if`, nicht `[ … ] && continue`: Unter `set -e` waere die falsche Seite einer UND-Liste ein
    # fehlgeschlagener Befehl, und die Schleife braeche beim ersten Namen ab, der noch zu pruefen
    # ist — die Wahl fiele dann auf eine aeltere Sicherung als die richtige.
    if [ "$stempel" \> "$ziel" ]; then continue; fi
    if [ -z "$bester" ] || [ "$stempel" \> "$bester" ]; then
      bester=$stempel
      treffer=$name
    fi
  done
  [ -n "$treffer" ] || return 1
  printf '%s\n' "$treffer"
}

# ---------------------------------------------------------------------------
# Beschaffung
# ---------------------------------------------------------------------------

# Eine Datei ausser Haus holen und entschluesseln. Das ist die einzige Stelle im ganzen
# Sicherungswerk, an der ein privater Schluessel gelesen wird.
entschluesseln() {
  local zielname=$1 nach=$2 roh="$ARBEIT/.age.${BASHPID:-$$}"
  rclone copyto "$(ziel_pfad "$zielname")" "$roh" || return 1
  mkdir -p "$(dirname "$nach")" || return 1
  if ! age --decrypt --identity "$RUECKHOL_SCHLUESSEL" --output "$nach" "$roh"; then
    rm -f "$roh"
    return 1
  fi
  rm -f "$roh"
}

# Bis wohin der Anhang-Spiegel reicht — die Zahl, an der E5 haengt.
spiegelstand_holen() {
  local datei
  if [ "$RUECKHOL_QUELLE" = lokal ]; then
    datei="$SPIEGEL_STAND"
    [ -s "$datei" ] || return 1
  else
    datei="$ARBEIT/spiegel-stand"
    entschluesseln "$SPIEGEL_STAND_ZIEL" "$datei" || return 1
  fi
  tr -d '[:space:]' < "$datei"
}

# Die passende Basissicherung besorgen; gibt ihren oertlichen Pfad aus.
basis_holen() {
  local ziel=$1 name pfad
  if [ "$RUECKHOL_QUELLE" = lokal ]; then
    name=$(find "$BASIS_DIR" -maxdepth 1 -type f -name 'basis-*.tar.gz' | basis_waehlen "$ziel") \
      || return 1
    printf '%s\n' "$name"
    return 0
  fi
  name=$(rclone lsf "$(ziel_pfad basis)" | basis_waehlen "$ziel") || return 1
  pfad="$ARBEIT/$(basename "${name%.age}")"
  entschluesseln "basis/$name" "$pfad" || return 1
  printf '%s\n' "$pfad"
}

# Das WAL-Archiv bereitstellen, aus dem Postgres bis zum Zielzeitpunkt nachfaehrt.
#
# Beim Weg ausser Haus landen die entschluesselten Segmente in genau dem Volume, das der
# Postgres-Dienst als /wal-archiv sieht — restore_command liest spaeter von dort. Auf einer leeren
# Maschine ist es leer; ein bestehendes Archiv wird nicht angetastet, nur ergaenzt.
wal_holen() {
  local roh="$ARBEIT/wal" datei rel
  mkdir -p "$ARCHIV"
  if [ "$RUECKHOL_QUELLE" = lokal ]; then
    log "WAL-Archiv: $ARCHIV ($(find "$ARCHIV" -type f | wc -l | tr -d ' ') Segmente)."
    return 0
  fi
  rm -rf "$roh"
  mkdir -p "$roh"
  rclone copy "$(ziel_pfad wal)" "$roh" || return 1
  while IFS= read -r datei; do
    rel=${datei#"$roh"/}
    mkdir -p "$(dirname "$ARCHIV/${rel%.age}")" || return 1
    age --decrypt --identity "$RUECKHOL_SCHLUESSEL" --output "$ARCHIV/${rel%.age}" "$datei" \
      || return 1
  done < <(find "$roh" -type f -name '*.age' | sort)
  rm -rf "$roh"
  chown -R postgres:postgres "$ARCHIV" 2> /dev/null || true
  log "WAL-Archiv aus der Kopie ausser Haus entschluesselt ($(find "$ARCHIV" -type f | wc -l | tr -d ' ') Segmente)."
}

# ---------------------------------------------------------------------------
# Einspielen
# ---------------------------------------------------------------------------

# Das Datenverzeichnis muss leer sein. Ein bestehendes zu ueberschreiben, waere der eine Fehler,
# den niemand zurueckdrehen kann — und wer zurueckholen will, hat es ohnehin gerade verworfen.
pgdata_pruefen() {
  if [ ! -d "$RUECKHOL_PGDATA" ]; then
    log "FEHLER $RUECKHOL_PGDATA gibt es nicht. Das Datenverzeichnis der Datenbank muss unter"
    log "       diesem Pfad eingehaengt sein — siehe den Kommentarkopf dieses Skripts."
    return 1
  fi
  if [ -n "$(find "$RUECKHOL_PGDATA" -mindepth 1 ! -name 'lost+found' -print -quit)" ]; then
    log "FEHLER $RUECKHOL_PGDATA ist nicht leer. Es wird nichts ueberschrieben."
    log "       Datenbank stoppen und das Volume verwerfen, dann erneut."
    return 1
  fi
}

pgdata_einspielen() {
  local basis=$1 zeitpunkt=$2
  tar -xzf "$basis" -C "$RUECKHOL_PGDATA" || return 1

  # recovery.signal macht aus dem entpackten Stand eine Wiederherstellung: Postgres faehrt das WAL
  # nach, haelt beim Zielzeitpunkt an und wird danach von selbst wieder schreibend. Ohne
  # recovery_target_action=promote bliebe die Instanz in der Pause stehen, und AK1 ("die Instanz
  # startet danach") waere nicht erfuellt.
  : > "$RUECKHOL_PGDATA/recovery.signal" || return 1
  {
    printf "\n# Rueckholung durch backup/restore.sh am %s\n" "$(jetzt)"
    printf "restore_command = 'cp %s/%%f %%p'\n" "$ARCHIV"
    printf "recovery_target_time = '%s'\n" "$zeitpunkt"
    printf "recovery_target_action = 'promote'\n"
  } >> "$RUECKHOL_PGDATA/postgresql.auto.conf" || return 1

  chown -R postgres:postgres "$RUECKHOL_PGDATA" || return 1
  chmod 0700 "$RUECKHOL_PGDATA" || return 1
}

# Die Anhaenge zurueck in den Objektspeicher.
#
# Der Spiegel kann Objekte enthalten, die zum Zielzeitpunkt schon geloescht waren — er loescht
# selbst nie (E5). Die bleiben nach der Rueckholung verwaist liegen; `StorageReconciliationService`
# meldet sie als verwaist, nicht als fehlend, und AK4 verlangt genau das: keine fehlenden.
anhaenge_zurueck() {
  local roh="$ARBEIT/spiegel" datei rel
  mkdir -p "$SPIEGEL_DIR" "$MC_CONFIG_DIR"
  if [ "$RUECKHOL_QUELLE" != lokal ]; then
    rm -rf "$roh"
    mkdir -p "$roh"
    rclone copy "$(ziel_pfad spiegel)" "$roh" || return 1
    while IFS= read -r datei; do
      rel=${datei#"$roh"/}
      mkdir -p "$(dirname "$SPIEGEL_DIR/${rel%.age}")" || return 1
      age --decrypt --identity "$RUECKHOL_SCHLUESSEL" --output "$SPIEGEL_DIR/${rel%.age}" "$datei" \
        || return 1
    done < <(find "$roh" -type f -name '*.age' | sort)
    rm -rf "$roh"
  fi
  mc alias set "$MC_ALIAS" "$MANBAN_MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" \
    > /dev/null || return 1
  mc mb --ignore-existing "$MC_ALIAS/$MANBAN_MINIO_BUCKET" > /dev/null || return 1
  mc mirror --quiet --overwrite "$SPIEGEL_DIR" "$MC_ALIAS/$MANBAN_MINIO_BUCKET" > /dev/null \
    || return 1
  log "Anhaenge zurueckgeschrieben ($(find "$SPIEGEL_DIR" -type f | wc -l | tr -d ' ') Objekte)."
}

# ---------------------------------------------------------------------------

rueckholung() {
  local eingabe=$1 ziel zeitpunkt stand basis

  if ! ziel=$(zeitpunkt_stempel "$eingabe"); then
    log "FEHLER '$eingabe' ist kein Zeitpunkt. Erwartet wird UTC auf die Sekunde,"
    log "       etwa 2026-09-23T08:00:00Z — ausschliesslich mit Z, ohne Ortszeit-Offset."
    return 1
  fi
  zeitpunkt=$(zeitpunkt_postgres "$eingabe")

  local -a pflicht=(MINIO_ROOT_USER MINIO_ROOT_PASSWORD)
  [ "$RUECKHOL_QUELLE" = lokal ] || pflicht+=(MANBAN_BACKUP_TARGET)
  pflichtfelder_pruefen "${pflicht[@]}" || return 1

  mkdir -p "$ARBEIT"
  chmod 0700 "$ARBEIT"

  # Zuerst pruefen, dann anfassen — beide Pruefungen stehen vor dem ersten Schreibzugriff.
  pgdata_pruefen || return 1

  if ! stand=$(spiegelstand_holen); then
    log "FEHLER Der Stand des Anhang-Spiegels ist nicht zu ermitteln."
    log "       Ohne ihn laesst sich nicht sagen, ob die Anhaenge bis $eingabe reichen —"
    log "       es wird nichts eingespielt."
    return 1
  fi
  if ziel_hinter_spiegel "$ziel" "$stand"; then
    log "FEHLER $eingabe liegt hinter dem letzten Anhang-Spiegel ($stand)."
    log "       Karten zeigten danach auf Anhaenge, die es nicht gibt. Es wird nichts eingespielt."
    log "       Einen Zeitpunkt bis $stand waehlen."
    return 1
  fi
  log "Zielzeitpunkt $eingabe, Anhang-Spiegel bis $stand — die Rueckholung ist gedeckt."

  if ! basis=$(basis_holen "$ziel"); then
    log "FEHLER Keine Basissicherung vor $eingabe gefunden ($RUECKHOL_QUELLE)."
    return 1
  fi
  log "Basissicherung: $basis"

  wal_holen || return 1
  pgdata_einspielen "$basis" "$zeitpunkt" || return 1
  anhaenge_zurueck || return 1

  log "Fertig. $RUECKHOL_PGDATA ist auf $eingabe eingerichtet."
  log "Jetzt die Instanz starten — Postgres faehrt das WAL bis dahin nach und wird dann schreibend."
}

rueckhol_hauptlauf() {
  local eingabe=''
  RUECKHOL_QUELLE=lokal
  RUECKHOL_SCHLUESSEL=''
  while [ $# -gt 0 ]; do
    case $1 in
      --quelle)
        RUECKHOL_QUELLE=${2-}
        shift 2 || return 2
        ;;
      --schluessel)
        RUECKHOL_SCHLUESSEL=${2-}
        shift 2 || return 2
        ;;
      -*)
        log "FEHLER Unbekannte Angabe '$1'. Erlaubt: --quelle, --schluessel."
        return 2
        ;;
      *)
        eingabe=$1
        shift
        ;;
    esac
  done

  if [ -z "$eingabe" ]; then
    log "FEHLER Aufruf: restore.sh <zeitpunkt> [--quelle lokal|aussenhaus] [--schluessel <datei>]"
    return 2
  fi
  case $RUECKHOL_QUELLE in
    lokal) ;;
    aussenhaus)
      # Ohne den verwahrten privaten Schluessel ist aus der Kopie ausser Haus nichts zu holen. Das
      # ist keine Panne, sondern die Zusage aus AK8 — der Server kann seine eigenen Sicherungen
      # nicht lesen, und dieses Skript laeuft auf dem Server.
      if [ -z "$RUECKHOL_SCHLUESSEL" ]; then
        log "FEHLER --quelle aussenhaus braucht --schluessel <datei> mit dem privaten age-Schluessel."
        log "       Er liegt nicht auf dem Server; der Betreiber verwahrt ihn ausserhalb (AK8)."
        return 2
      fi
      if [ ! -r "$RUECKHOL_SCHLUESSEL" ]; then
        log "FEHLER Privater Schluessel '$RUECKHOL_SCHLUESSEL' ist nicht lesbar."
        return 2
      fi
      ;;
    *)
      log "FEHLER --quelle '$RUECKHOL_QUELLE' kennt dieses Skript nicht. Erlaubt: lokal, aussenhaus."
      return 2
      ;;
  esac

  vorgaben_setzen
  rueckholung "$eingabe"
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  rueckhol_hauptlauf "$@"
fi
