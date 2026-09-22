#!/usr/bin/env bash
# einheiten.sh — Proben der Rechenteile von backup.sh (Issue #829).
#
# Aufruf:  bash backup/test/einheiten.sh
#
# Laeuft in einer Sekunde, ohne Docker, ohne Datenbank, ohne Netz — und gehoert deshalb NICHT in
# dieselbe Schublade wie backup/test/restore-roundtrip.sh, das einen ganzen Compose-Stack braucht.
# Geprueft wird, was sich ohne laufende Umgebung pruefen laesst und trotzdem still kaputtgehen kann:
#
#   * die Umrechnung von MANBAN_BACKUP_MIRROR_INTERVAL (ISO-8601) in Sekunden
#   * die Umrechnung von MANBAN_BACKUP_BASE_CRON (sechs Felder, Spring) in cron (fuenf Felder)
#   * der Versand ausser Haus: Marken, Wiederholung, und dass eine Datei, deren Verschluesselung
#     scheitert, KEINE Marke bekommt — sonst gaelte sie fuer immer als gesichert, ohne es zu sein
#
# `age`, `rclone` und `stat` werden dabei durch Platzhalter ersetzt. Was die echten Werkzeuge tun,
# beweist erst die Rueckhol-Probe; hier geht es um die Buchfuehrung darum herum.
#
# Kein Teil von `mvn verify` (E14): Die Sicherung lebt ausserhalb der Anwendung, und ihre Proben
# auch.
set -uo pipefail

HIER=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../backup.sh
. "$HIER/../backup.sh"

fehler=0
melde() {
  if [ "$2" = "$3" ]; then
    printf 'ok    %s -> %s\n' "$1" "$2"
  else
    printf 'NICHT %s -> %s (erwartet %s)\n' "$1" "$2" "$3"
    fehler=1
  fi
}

# --- Takte -----------------------------------------------------------------

takt() {
  local ist
  ist=$(takt_sekunden "$1" 2> /dev/null) || ist=FEHLER
  melde "takt_sekunden $1" "$ist" "$2"
}

takt PT5M 300
takt PT1H 3600
takt PT30S 30
takt PT1H30M 5400
# Nicht positiv waere kein Takt, sondern sein Gegenteil: Jeder Lauf gaelte im selben Augenblick als
# ueberfaellig. Dieselbe Begruendung steht in BackupProperties.
takt PT0S FEHLER
takt P1D FEHLER
takt 5M FEHLER
takt PT FEHLER
takt PTxM FEHLER

cron() {
  local ist
  ist=$(cron_fuenf_felder "$1" 2> /dev/null) || ist=FEHLER
  melde "cron_fuenf_felder '$1'" "$ist" "$2"
}

cron "0 0 3 * * *" "0 3 * * *"
cron "0 3 * * *" "0 3 * * *"
cron "0 */15 * * * *" "*/15 * * * *"
cron "0 3 *" FEHLER

# --- Versand ausser Haus ---------------------------------------------------

wurzel=$(mktemp -d "${TMPDIR:-/tmp}/manban-backup-probe.XXXXXX")
trap 'rm -rf "$wurzel"' EXIT

SICHERUNG="$wurzel/sicherungen"
ARCHIV="$wurzel/archiv"
VERSANDT_DIR="$SICHERUNG/versandt"
MANBAN_BACKUP_TARGET="$wurzel/ziel"
MANBAN_BACKUP_AGE_RECIPIENT="age1probe"
mkdir -p "$SICHERUNG" "$ARCHIV/unter" "$VERSANDT_DIR/wal"

age_scheitert=0
age() {
  local ziel=$4 quelle=$5
  [ "$age_scheitert" = 0 ] || return 7
  printf 'AGE:' > "$ziel"
  cat "$quelle" >> "$ziel"
}
rclone() {
  mkdir -p "$(dirname "$3")"
  cp "$2" "$3"
}
# `stat -c` ist GNU; die Probe soll auch auf einem Apple-Rechner laufen.
stat() { wc -c < "$3" | tr -d ' '; }

printf 'eins' > "$ARCHIV/000000010000000000000001"
printf 'zwei!!' > "$ARCHIV/unter/000000010000000000000002"

ergebnis=$(verzeichnis_versenden "$ARCHIV" wal wal)
melde "erster Lauf: Anzahl und Bytes" "$ergebnis" "2 18"
melde "Ziel, flach" "$(cat "$wurzel/ziel/wal/000000010000000000000001.age")" "AGE:eins"
melde "Ziel, verschachtelt" "$(cat "$wurzel/ziel/wal/unter/000000010000000000000002.age")" "AGE:zwei!!"
melde "Marke gesetzt" \
  "$(test -f "$VERSANDT_DIR/wal/unter/000000010000000000000002" && echo ja || echo nein)" ja

melde "zweiter Lauf schickt nichts erneut" "$(verzeichnis_versenden "$ARCHIV" wal wal)" "0 0"

printf 'drei' > "$ARCHIV/000000010000000000000003"
age_scheitert=1
if verzeichnis_versenden "$ARCHIV" wal wal > /dev/null 2>&1; then stand=erfolg; else stand=fehlschlag; fi
melde "gescheiterte Verschluesselung meldet Fehlschlag" "$stand" fehlschlag
melde "keine Marke fuer eine nie angekommene Datei" \
  "$(test -f "$VERSANDT_DIR/wal/000000010000000000000003" && echo ja || echo nein)" nein
melde "keine Zwischendatei zurueckgelassen" \
  "$(find "$SICHERUNG" -maxdepth 1 -name '.versand.*' | wc -l | tr -d ' ')" 0

age_scheitert=0
melde "nach Behebung genau die fehlende Datei" "$(verzeichnis_versenden "$ARCHIV" wal wal)" "1 8"

melde "fehlendes Verzeichnis ist kein Fehler" "$(verzeichnis_versenden "$wurzel/gibtsnicht" wal wal)" "0 0"

if [ "$fehler" -eq 0 ]; then
  printf '\nAlle Proben bestanden.\n'
else
  printf '\nMindestens eine Probe ist gescheitert.\n'
fi
exit "$fehler"
