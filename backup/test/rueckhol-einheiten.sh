#!/usr/bin/env bash
# rueckhol-einheiten.sh — Proben der Rechenteile von restore.sh und retention.sh (Issue #830).
#
# Aufruf:  bash backup/test/rueckhol-einheiten.sh
#
# Dieselbe Schublade wie backup/test/einheiten.sh: eine Sekunde, ohne Docker, ohne Datenbank, ohne
# Netz. Geprueft wird ausschliesslich, was sich ohne laufende Umgebung pruefen laesst und trotzdem
# still kaputtgehen kann — und das ist hier genau das Gefaehrliche:
#
#   * die Vorpruefung aus E5: Ein Rueckholzeitpunkt hinter dem letzten Anhang-Spiegel wird
#     abgewiesen. Faellt diese Pruefung aus, zeigen Karten nach der Rueckholung auf Dateien, die es
#     nicht gibt (AK4)
#   * die Auslegung des Zielzeitpunkts. Er landet woertlich in postgresql.auto.conf — eine
#     Eingabe, die nicht strikt geprueft wird, ist dort eine Einschleusung
#   * die Wahl der Basissicherung: die juengste VOR dem Ziel, nie eine danach
#   * das Verfallen: dass die juengste Basissicherung nie faellt, und dass ein Anhang, der noch an
#     einer Karte haengt, im Spiegel bleibt, egal wie alt er ist (Gegenprobe zu E5)
#
# Was `age`, `rclone`, `mc` und `psql` tun, beweist erst backup/test/restore-roundtrip.sh
# (Issue #832). Hier geht es um die Entscheidungen davor.
#
# Kein Teil von `mvn verify` (E14): Die Sicherung lebt ausserhalb der Anwendung, und ihre Proben
# auch.
set -uo pipefail

# Eigener Name statt HIER: Die geprueften Skripte setzen sich selbst einen solchen Wegweiser, um
# backup.sh zu finden — er waere nach dem ersten Sourcen ueberschrieben.
PROBE_HEIM=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=../restore.sh
. "$PROBE_HEIM/../restore.sh"
# shellcheck source=../retention.sh
. "$PROBE_HEIM/../retention.sh"

fehler=0
melde() {
  if [ "$2" = "$3" ]; then
    printf 'ok    %s -> %s\n' "$1" "$2"
  else
    printf 'NICHT %s -> %s (erwartet %s)\n' "$1" "$2" "$3"
    fehler=1
  fi
}

# --- Auslegung des Zielzeitpunkts ------------------------------------------

stempel() {
  local ist
  ist=$(zeitpunkt_stempel "$1" 2> /dev/null) || ist=FEHLER
  melde "zeitpunkt_stempel '$1'" "$ist" "$2"
}

stempel "2026-09-23T08:00:00Z" 20260923T080000Z
stempel "2026-09-23 08:00:00Z" 20260923T080000Z
# Nur UTC. Ein Ortszeit-Offset waere die zweite Schreibweise derselben Sache, und die Vorpruefung
# aus E5 vergleicht Zeichenketten — ein ungerechneter Offset hoebe sie stillschweigend aus.
stempel "2026-09-23T10:00:00+02:00" FEHLER
stempel "2026-09-23T08:00:00" FEHLER
stempel "2026-09-23" FEHLER
stempel "20260923T080000Z" FEHLER
stempel "2026-13-01T08:00:00Z" FEHLER
stempel "2026-09-32T08:00:00Z" FEHLER
stempel "2026-09-23T24:00:00Z" FEHLER
stempel "2026-09-23T08:60:00Z" FEHLER
# Der Wert landet woertlich zwischen Hochkommata in postgresql.auto.conf.
stempel "2026-09-23T08:00:00Z' -- " FEHLER
stempel "" FEHLER

pg() {
  local ist
  ist=$(zeitpunkt_postgres "$1" 2> /dev/null) || ist=FEHLER
  melde "zeitpunkt_postgres '$1'" "$ist" "$2"
}

pg "2026-09-23T08:00:00Z" "2026-09-23 08:00:00+00"
pg "2026-01-02 03:04:05Z" "2026-01-02 03:04:05+00"
pg "nicht" FEHLER

# --- Die Grenze aus E5 -----------------------------------------------------

grenze() {
  local ist
  if ziel_hinter_spiegel "$1" "$2"; then ist=abweisen; else ist=einspielen; fi
  melde "ziel_hinter_spiegel $1 $2" "$ist" "$3"
}

grenze 20260923T080000Z 20260923T075500Z abweisen
grenze 20260923T075500Z 20260923T075500Z einspielen
grenze 20260923T075000Z 20260923T075500Z einspielen
grenze 20260923T080000Z 20260922T235900Z abweisen

# --- Wahl der Basissicherung -----------------------------------------------

waehlen() {
  local ziel=$1 erwartet=$2 ist
  shift 2
  ist=$(printf '%s\n' "$@" | basis_waehlen "$ziel" 2> /dev/null) || ist=FEHLER
  melde "basis_waehlen $ziel" "$ist" "$erwartet"
}

bestand=(
  basis-20260920T030000Z.tar.gz
  basis-20260921T030000Z.tar.gz
  basis-20260923T030000Z.tar.gz
)
waehlen 20260923T080000Z basis-20260923T030000Z.tar.gz "${bestand[@]}"
waehlen 20260921T120000Z basis-20260921T030000Z.tar.gz "${bestand[@]}"
waehlen 20260921T030000Z basis-20260921T030000Z.tar.gz "${bestand[@]}"
# Vor der aeltesten Sicherung gibt es nichts zurueckzuholen — und eine juengere zu nehmen, waere
# eine Rueckholung auf einen anderen Zeitpunkt als den verlangten.
waehlen 20260919T000000Z FEHLER "${bestand[@]}"
waehlen 20260923T080000Z FEHLER
# Die Kopie ausser Haus traegt dieselben Namen mit .age.
waehlen 20260923T080000Z basis-20260923T030000Z.tar.gz.age \
  basis-20260920T030000Z.tar.gz.age basis-20260923T030000Z.tar.gz.age
# Fremde Dateien am selben Ort entscheiden nicht mit.
waehlen 20260923T080000Z basis-20260920T030000Z.tar.gz \
  basis-20260920T030000Z.tar.gz gelesen.txt basis-unfertig.tar.gz.teil
# Ein Pfad ist so gut wie ein Name.
waehlen 20260923T080000Z /sicherungen/basis/basis-20260922T030000Z.tar.gz \
  /sicherungen/basis/basis-20260922T030000Z.tar.gz

# --- Verfallen der Basissicherungen ----------------------------------------

verwerfen() {
  local grenzwert=$1 erwartet=$2 ist
  shift 2
  ist=$(printf '%s\n' "$@" | basis_zu_verwerfen "$grenzwert" | tr '\n' ',')
  melde "basis_zu_verwerfen $grenzwert" "$ist" "$erwartet"
}

verwerfen 20260922T000000Z "basis-20260920T030000Z.tar.gz,basis-20260921T030000Z.tar.gz," \
  "${bestand[@]}"
verwerfen 20260920T000000Z "" "${bestand[@]}"
# Die juengste faellt nie, auch wenn die Frist laengst abgelaufen ist: Sonst raeumte ein Container,
# der eine Woche lang nicht sichern konnte, den letzten Stand weg, den es noch gab.
verwerfen 20261001T000000Z "basis-20260920T030000Z.tar.gz,basis-20260921T030000Z.tar.gz," \
  "${bestand[@]}"
verwerfen 20261001T000000Z "" basis-20260920T030000Z.tar.gz
verwerfen 20261001T000000Z ""
verwerfen 20261001T000000Z "" gelesen.txt basis-unfertig.tar.gz.teil

# Woran die WAL-Segmente haengen: an der aeltesten Basissicherung, die bleibt. Ein Segment davor
# gehoert zu keinem Stand mehr, eines danach traegt den Weg von dort in die Gegenwart.
aeltesteFrage() {
  local grenzwert=$1 erwartet=$2 ist
  shift 2
  ist=$(printf '%s\n' "$@" | basis_aelteste_behaltene "$grenzwert" 2> /dev/null) || ist=FEHLER
  melde "basis_aelteste_behaltene $grenzwert" "$ist" "$erwartet"
}

aeltesteFrage 20260922T000000Z basis-20260923T030000Z.tar.gz "${bestand[@]}"
aeltesteFrage 20260920T000000Z basis-20260920T030000Z.tar.gz "${bestand[@]}"
aeltesteFrage 20261001T000000Z basis-20260923T030000Z.tar.gz "${bestand[@]}"
aeltesteFrage 20260922T000000Z FEHLER

# --- Verfallen im Anhang-Spiegel -------------------------------------------

raum=$(mktemp -d "${TMPDIR:-/tmp}/manban-rueckhol-probe.XXXXXX")
trap 'rm -rf "$raum"' EXIT

cat > "$raum/referenzen" << 'REF'
2026/01/aaa-lebt.pdf
2026/01/bbb-lebt.png
REF

spiegelfrage() {
  local erwartet=$1 ist
  shift
  ist=$(printf '%s\n' "$@" | spiegel_zu_verwerfen "$raum/referenzen" | tr '\n' ',')
  melde "spiegel_zu_verwerfen" "$ist" "$erwartet"
}

# Alles hier ist bereits nach Alter vorgefiltert; entscheidend ist allein die Referenz.
spiegelfrage "2026/01/ccc-geloescht.pdf," \
  2026/01/aaa-lebt.pdf 2026/01/ccc-geloescht.pdf 2026/01/bbb-lebt.png
spiegelfrage "" 2026/01/aaa-lebt.pdf 2026/01/bbb-lebt.png
spiegelfrage "2026/01/ccc-geloescht.pdf,2026/02/ddd-geloescht.pdf," \
  2026/01/ccc-geloescht.pdf 2026/02/ddd-geloescht.pdf
# Teiltreffer sind keine Treffer: 'aaa-lebt.pdf' allein ist ein anderer Schluessel als der volle.
spiegelfrage "aaa-lebt.pdf," aaa-lebt.pdf
spiegelfrage "" ""

# Eine leere Referenzliste ist kein Freibrief: Sie entsteht auch, wenn eine Instanz gar keine
# Anhaenge hat — dann ist jedes Objekt im Spiegel tatsaechlich verwaist. Dass die Liste ueberhaupt
# entstanden ist, prueft retention.sh vor dem Aufruf; hier zaehlt nur, dass die Regel greift.
: > "$raum/leer"
melde "spiegel_zu_verwerfen, leere Referenzliste" \
  "$(printf '%s\n' 2026/01/aaa-lebt.pdf | spiegel_zu_verwerfen "$raum/leer" | tr '\n' ',')" \
  "2026/01/aaa-lebt.pdf,"

# --- Aufbewahrungsfrist ----------------------------------------------------

frist() {
  local ist
  if tage_pruefen "$1" 2> /dev/null; then ist=gueltig; else ist=FEHLER; fi
  melde "tage_pruefen '$1'" "$ist" "$2"
}

frist 7 gueltig
frist 1 gueltig
frist 365 gueltig
# Null Tage hiesse: alles ausser der juengsten Basissicherung sofort verwerfen. Wer das will, soll
# es sagen muessen, statt es sich durch einen leeren Wert einzuhandeln.
frist 0 FEHLER
frist -1 FEHLER
frist "" FEHLER
frist "7 Tage" FEHLER
frist "sieben" FEHLER

# --- Die Abbruchpfade der Rueckholung --------------------------------------
#
# Sie greifen alle, BEVOR irgendetwas eingespielt wird, und brauchen darum weder tar noch mc noch
# rclone. Genau das macht sie hier pruefbar — und sie tragen die beiden Zusagen, auf die es
# ankommt: AK2 (ohne privaten Schluessel kein Weg ausser Haus) und AK3 (ein Zeitpunkt hinter dem
# Spiegel wird abgewiesen, und es wird nichts eingespielt).

MINIO_ROOT_USER=probe
MINIO_ROOT_PASSWORD=probe
MANBAN_BACKUP_TARGET="$raum/ziel"
SICHERUNG="$raum/sicherungen"
BASIS_DIR="$SICHERUNG/basis"
SPIEGEL_DIR="$SICHERUNG/spiegel"
SPIEGEL_STAND="$SICHERUNG/spiegel-stand"
ARBEIT="$SICHERUNG/.rueckholung"
RUECKHOL_PGDATA="$raum/pgdata"
mkdir -p "$BASIS_DIR" "$SPIEGEL_DIR" "$RUECKHOL_PGDATA"

rueckhol() {
  local erwartet=$1 ist=0
  shift
  rueckhol_hauptlauf "$@" > /dev/null 2>&1 || ist=$?
  melde "restore.sh $*" "$ist" "$erwartet"
}

# Ohne den verwahrten privaten Schluessel gibt es keinen Weg aus der Kopie ausser Haus (AK2, AK8).
rueckhol 2 2026-09-23T08:00:00Z --quelle aussenhaus
rueckhol 2 2026-09-23T08:00:00Z --quelle aussenhaus --schluessel "$raum/gibtsnicht.key"
rueckhol 2 2026-09-23T08:00:00Z --quelle erfunden
rueckhol 2 2026-09-23T08:00:00Z --was-denn
rueckhol 2 --quelle lokal
rueckhol 1 "irgendwann"

printf '20260923T075500Z\n' > "$SPIEGEL_STAND"

# AK3: hinter dem Spiegel wird abgewiesen — und das Datenverzeichnis bleibt unberuehrt.
rueckhol 1 2026-09-23T08:00:00Z
melde "nichts eingespielt" \
  "$(find "$RUECKHOL_PGDATA" -mindepth 1 | wc -l | tr -d ' ')" 0

# Vor dem Spiegel ist gedeckt — hier scheitert es erst an der fehlenden Basissicherung, und das
# ist der Beleg, dass die Pruefung aus E5 den Weg freigegeben hat.
rueckhol 1 2026-09-23T07:00:00Z
printf 'nicht wirklich ein Archiv\n' > "$BASIS_DIR/basis-20260923T030000Z.tar.gz"

# Ein Datenverzeichnis mit Inhalt wird nie ueberschrieben.
printf 'bestand\n' > "$RUECKHOL_PGDATA/PG_VERSION"
rueckhol 1 2026-09-23T07:00:00Z
melde "bestehendes Datenverzeichnis unveraendert" "$(cat "$RUECKHOL_PGDATA/PG_VERSION")" bestand
rm -f "$RUECKHOL_PGDATA/PG_VERSION"

# Ohne Stand des Spiegels wird gar nicht erst angefangen: Ob die Anhaenge bis zum Ziel reichen,
# waere dann eine Vermutung.
rm -f "$SPIEGEL_STAND"
rueckhol 1 2026-09-23T07:00:00Z

# --- Verfallen: ein ganzer Lauf --------------------------------------------
#
# Mit Platzhaltern fuer rclone und die Datenbankabfrage, aber mit echten Dateien. `age` bekommt
# einen Platzhalter, der die Probe scheitern laesst, sobald er gerufen wird — AK7 sagt, dass hier
# nichts entschluesselt wird, und eine Zusage, die niemand prueft, ist eine Hoffnung.

age_gerufen=nein
age() {
  age_gerufen=ja
  return 1
}

geloescht="$raum/geloescht-ausser-haus"
: > "$geloescht"
# MANBAN_BACKUP_TARGET ist hier ein blosser Pfad — rclone kann das auch wirklich, siehe
# backup/rclone.conf.example. Der Platzhalter tut darum genau das, was rclone dann taete.
rclone() {
  case ${1-} in
    deletefile)
      [ -f "$2" ] || return 1
      printf '%s\n' "$2" >> "$geloescht"
      rm -f "$2"
      ;;
    lsf)
      find "$2" -maxdepth 1 -type f -exec basename {} \; 2> /dev/null
      ;;
    *) return 1 ;;
  esac
}

referenzen_holen() {
  printf '%s\n' 2026/01/aaa-lebt.pdf > "$1"
}

ARCHIV="$raum/archiv"
VERSANDT_DIR="$SICHERUNG/versandt"
MANBAN_BACKUP_RETENTION_DAYS=7
POSTGRES_PASSWORD=probe
# Der Bestand des Rueckhol-Abschnitts waere hier die juengste Basissicherung und verschoebe jede
# Erwartung; das Verfallen faengt mit einem eigenen Bestand an.
rm -f "$BASIS_DIR"/*
mkdir -p "$ARCHIV" "$VERSANDT_DIR/wal" "$VERSANDT_DIR/spiegel/2026/01" \
  "$MANBAN_BACKUP_TARGET/basis" "$MANBAN_BACKUP_TARGET/spiegel/2026/01" "$SPIEGEL_DIR/2026/01"
: > "$MANBAN_BACKUP_TARGET/spiegel/2026/01/ccc-geloescht.pdf.age"

alt=202001010300
: > "$BASIS_DIR/basis-20200101T030000Z.tar.gz"
: > "$BASIS_DIR/basis-20200102T030000Z.tar.gz"
: > "$MANBAN_BACKUP_TARGET/basis/basis-20200101T030000Z.tar.gz.age"
: > "$MANBAN_BACKUP_TARGET/basis/basis-20200102T030000Z.tar.gz.age"
: > "$ARCHIV/000000010000000000000001"
: > "$VERSANDT_DIR/wal/000000010000000000000001"
printf 'lebt\n' > "$SPIEGEL_DIR/2026/01/aaa-lebt.pdf"
printf 'weg\n' > "$SPIEGEL_DIR/2026/01/ccc-geloescht.pdf"
: > "$VERSANDT_DIR/spiegel/2026/01/ccc-geloescht.pdf"
touch -t "$alt" \
  "$BASIS_DIR/basis-20200101T030000Z.tar.gz" "$BASIS_DIR/basis-20200102T030000Z.tar.gz" \
  "$ARCHIV/000000010000000000000001" \
  "$SPIEGEL_DIR/2026/01/aaa-lebt.pdf" "$SPIEGEL_DIR/2026/01/ccc-geloescht.pdf"

verfall_stand=0
verfall_lauf > /dev/null 2>&1 || verfall_stand=$?
melde "verfall_lauf gelingt" "$verfall_stand" 0

# AK7: kein Entschluesseln, zu keinem Zeitpunkt.
melde "age nicht gerufen" "$age_gerufen" nein

# Die aelteste faellt, die juengste bleibt — oertlich wie ausser Haus.
melde "alte Basissicherung oertlich verfallen" \
  "$(test -f "$BASIS_DIR/basis-20200101T030000Z.tar.gz" && echo da || echo weg)" weg
melde "juengste Basissicherung bleibt" \
  "$(test -f "$BASIS_DIR/basis-20200102T030000Z.tar.gz" && echo da || echo weg)" da
melde "alte Basissicherung ausser Haus verfallen" \
  "$(test -f "$MANBAN_BACKUP_TARGET/basis/basis-20200101T030000Z.tar.gz.age" && echo da || echo weg)" weg
melde "juengste Basissicherung ausser Haus bleibt" \
  "$(test -f "$MANBAN_BACKUP_TARGET/basis/basis-20200102T030000Z.tar.gz.age" && echo da || echo weg)" da

# Das WAL-Segment ist aelter als die aelteste Basissicherung, die bleibt — es gehoert zu keinem
# vorhandenen Stand mehr.
melde "altes WAL-Segment verfallen" \
  "$(test -f "$ARCHIV/000000010000000000000001" && echo da || echo weg)" weg
melde "Marke des WAL-Segments mit entfernt" \
  "$(test -f "$VERSANDT_DIR/wal/000000010000000000000001" && echo da || echo weg)" weg

# Die Gegenprobe zu E5: alt, aber noch an einer Karte — bleibt.
melde "referenzierter Anhang bleibt trotz Alter" \
  "$(test -f "$SPIEGEL_DIR/2026/01/aaa-lebt.pdf" && echo da || echo weg)" da
melde "unreferenzierter alter Anhang verfaellt" \
  "$(test -f "$SPIEGEL_DIR/2026/01/ccc-geloescht.pdf" && echo da || echo weg)" weg
melde "Marke des Anhangs mit entfernt" \
  "$(test -f "$VERSANDT_DIR/spiegel/2026/01/ccc-geloescht.pdf" && echo da || echo weg)" weg
melde "Anhang auch ausser Haus angewiesen" \
  "$(grep -c 'spiegel/2026/01/ccc-geloescht.pdf.age' "$geloescht" | tr -d ' ')" 1

# Ohne Anhang-Metadaten wird der Spiegel nicht angefasst: Eine leere Liste sieht aus wie "kein
# Anhang wird mehr gebraucht", und danach zu raeumen hiesse, bei gestoerter Datenbank saemtliche
# Anhaenge zu loeschen.
referenzen_holen() { return 1; }
verfall_stand=0
verfall_lauf > /dev/null 2>&1 || verfall_stand=$?
melde "verfall_lauf meldet die gescheiterte Abfrage" "$verfall_stand" 1
melde "Spiegel bei gestoerter Abfrage unangetastet" \
  "$(test -f "$SPIEGEL_DIR/2026/01/aaa-lebt.pdf" && echo da || echo weg)" da

if [ "$fehler" -eq 0 ]; then
  printf '\nAlle Proben bestanden.\n'
else
  printf '\nMindestens eine Probe ist gescheitert.\n'
fi
exit "$fehler"
