#!/usr/bin/env bash
# retention.sh — Verfallen alter Staende (Issue #830, Plan #825).
#
# Aufruf:  retention.sh
#
# Laeuft im selben Container wie backup.sh und am selben Takt: cron ruft nach jeder Basissicherung
# dieses Skript auf (siehe cron_einrichten in backup.sh). Von Hand:
#   docker compose exec manban-backup retention.sh
#
# Drei Bestaende, drei Regeln — und die Unterschiede zwischen ihnen sind der ganze Punkt:
#
#   Basissicherungen  Der Name traegt das Datum (basis-<stempel>.tar.gz). Faellig ist, was aelter
#                     ist als MANBAN_BACKUP_RETENTION_DAYS — oertlich und ausser Haus je fuer sich
#                     entschieden, beides ohne zu entschluesseln. Die JUENGSTE faellt nie: Ein
#                     Container, der eine Woche lang nicht sichern konnte, raeumte sonst den
#                     letzten Stand weg, den es ueberhaupt noch gab.
#
#   WAL-Archiv        Die Namen tragen kein Datum, sie zaehlen. Faellig ist darum, was aelter ist
#                     als die aelteste Basissicherung, die BLEIBT — ein Segment davor gehoert zu
#                     keinem vorhandenen Stand mehr, eines danach traegt den Weg von dort in die
#                     Gegenwart. Damit haengt die Frist an einer einzigen Stelle. Gibt es keine
#                     Basissicherung, wird nichts geraeumt: Dann ist etwas kaputt, und waehrend
#                     einer Stoerung etwas wegzuwerfen, ist die falsche Reihenfolge.
#
#   Anhang-Spiegel    NICHT nach Dateialter (E5). Ein Spiegel ist ein einzelner Zustand, kein
#                     datierter Stand: Ein Anhang, der vor zehn Tagen hochgeladen wurde und noch
#                     an einer Karte haengt, ist dort eine zehn Tage alte Datei — nach Alter
#                     geraeumt waere er nach einer Woche weg, und genau diesen Schaden verhindert
#                     E5. Ein Objekt faellt deshalb nur, wenn es aelter als die Frist ist UND in
#                     attachment_meta nicht mehr referenziert wird.
#
# Dieses Skript entschluesselt zu keinem Zeitpunkt etwas und braucht darum auch keinen Empfaenger:
# `age` kommt hier nicht vor (AK7). Ausser Haus wird nach Namen geloescht, und die Namen stehen
# dort im Klartext.
#
# Geloescht wird immer ausser Haus zuerst, dann die Marke, dann oertlich. Bricht ein Lauf
# dazwischen ab, sieht der naechste die oertliche Datei noch und raeumt zu Ende; andersherum bliebe
# ausser Haus etwas liegen, von dem niemand mehr weiss.

HIER=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck source=backup.sh
. "$HIER/backup.sh"

# ---------------------------------------------------------------------------
# Die Regeln
# ---------------------------------------------------------------------------

# Eine Aufbewahrungsfrist in Tagen ist eine positive ganze Zahl.
#
# Null waere kein kurzer Aufbewahrungszeitraum, sondern sein Gegenteil: alles ausser der juengsten
# Basissicherung sofort zu verwerfen. Wer das will, soll es sagen muessen — nicht durch einen
# leeren Wert dort hineinrutschen.
tage_pruefen() {
  local wert=${1-}
  case $wert in
    '' | *[!0-9]*) return 1 ;;
  esac
  [ "$((10#$wert))" -gt 0 ]
}

# Der Stempel vor <tage> Tagen — die eine Grenze, an der alles gemessen wird.
#
# Zwei Schreibweisen von `date`, weil es zwei gibt: GNU im Container, BSD auf dem Apple-Rechner,
# auf dem die Proben laufen. Gerechnet wird in beiden Faellen dasselbe.
verfallsgrenze() {
  local sekunden
  sekunden=$(($(date -u +%s) - $1 * 86400))
  date -u -d "@$sekunden" +%Y%m%dT%H%M%SZ 2> /dev/null \
    || date -u -r "$sekunden" +%Y%m%dT%H%M%SZ
}

# Dieselbe Grenze als Datei, gegen die sich `find ... ! -newer` messen laesst.
#
# WAL-Segmente und Anhaenge tragen ihr Datum nicht im Namen, sie haben nur eine Dateizeit — und
# `find -newermt` versteht auf den beiden `find`-Bauarten verschiedene Zeitangaben. Eine Datei mit
# der richtigen Zeit versteht jede. Der Zeitstempel fuer `touch -t` (YYYYMMDDhhmm) steckt bereits
# im Grenz-Stempel; er wird nur herausgeschnitten, nicht neu gerechnet: Zwei Rechnungen derselben
# Grenze koennten auseinanderlaufen.
grenzdatei_setzen() {
  local stempel=$1 datei=$2
  : > "$datei" || return 1
  touch -t "${stempel:0:8}${stempel:9:4}" "$datei"
}

# Die gueltigen Namen aus stdin als "<stempel> <name>", aufsteigend — also aeltester zuerst.
#
# `mapfile` waere kuerzer, gibt es aber erst ab bash 4; die Proben sollen auch auf einem
# Apple-Rechner laufen, und der bringt bash 3.2 mit.
basis_sortiert() {
  local name stempel eingabe zeile
  eingabe=$(
    while IFS= read -r name; do
      stempel=$(basis_stempel "$name") || continue
      printf '%s %s\n' "$stempel" "$name"
    done | sort
  )
  BASIS_SORTIERT=()
  while IFS= read -r zeile; do
    if [ -n "$zeile" ]; then BASIS_SORTIERT+=("$zeile"); fi
  done <<< "$eingabe"
}

# Aus einer Liste von Namen (stdin) die faelligen; die juengste bleibt in jedem Fall stehen.
#
# `if` statt `[ … ] && printf`: Unter `set -e` ist eine UND-Liste, deren linke Seite falsch ist,
# ein fehlgeschlagener Befehl — die Schleife braeche beim ersten Namen ab, der bleiben darf, und
# gaebe damit stillschweigend eine zu kurze Liste aus.
basis_zu_verwerfen() {
  local grenze=$1 i
  basis_sortiert
  for ((i = 0; i + 1 < ${#BASIS_SORTIERT[@]}; i++)); do
    if [ "${BASIS_SORTIERT[i]%% *}" \< "$grenze" ]; then
      printf '%s\n' "${BASIS_SORTIERT[i]#* }"
    fi
  done
  return 0
}

# Der Name der aeltesten Basissicherung, die nach demselben Massstab bleibt; Rueckgabe 1, wenn es
# gar keine gibt. Daran haengt die Grenze des WAL-Archivs.
basis_aelteste_behaltene() {
  local grenze=$1 i
  basis_sortiert
  [ ${#BASIS_SORTIERT[@]} -gt 0 ] || return 1
  for ((i = 0; i + 1 < ${#BASIS_SORTIERT[@]}; i++)); do
    [ "${BASIS_SORTIERT[i]%% *}" \< "$grenze" ] || break
  done
  printf '%s\n' "${BASIS_SORTIERT[i]#* }"
}

# Aus einer Liste bereits nach Alter vorgefilterter Objektschluessel (stdin) die, auf die keine
# Anhang-Metadaten mehr zeigen.
#
# `grep -Fxq`: fest statt Muster, und auf die ganze Zeile statt auf ein Stueck davon. Ein
# Teiltreffer waere hier die gefaehrlichste Art von Fehler — er liesse ein verwaistes Objekt
# stehen, was harmlos ist, oder loeschte ein lebendes, was es nicht ist.
spiegel_zu_verwerfen() {
  local referenzen=$1 schluessel
  while IFS= read -r schluessel; do
    [ -n "$schluessel" ] || continue
    grep -Fxq -- "$schluessel" "$referenzen" || printf '%s\n' "$schluessel"
  done
  return 0
}

# ---------------------------------------------------------------------------
# Ausfuehrung
# ---------------------------------------------------------------------------

# Die drei Raeum-Funktionen geben "Anzahl Bytes" auf stdout zurueck, wie verzeichnis_versenden in
# backup.sh. Ihre Protokollzeilen muessen deshalb auf stderr — sonst stuenden sie mit in der
# Rueckgabe, und der Aufrufer rechnete mit einer Log-Zeile weiter. Im Container laufen beide
# Kanaele ohnehin in dasselbe Protokoll.
melde_zeile() {
  log "$@" >&2
}

# Eine Datei ausser Haus entfernen. Ein Fehlschlag ist kein Weltuntergang — etwa weil dort schon
# nichts mehr liegt —, aber er gehoert ins Protokoll, damit niemand denkt, es sei geraeumt.
aussenhaus_entfernen() {
  local zielname=$1
  if ! rclone deletefile "$(ziel_pfad "$zielname")" 2> /dev/null; then
    melde_zeile "WARN $(ziel_pfad "$zielname") nicht entfernt."
    return 1
  fi
}

basis_raeumen() {
  local grenze=$1 name entfernt=0 bytes=0 groesse
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    groesse=$(stat -c %s "$name" 2> /dev/null || printf 0)
    aussenhaus_entfernen "basis/$(basename "$name").age" || true
    rm -f "$name"
    entfernt=$((entfernt + 1))
    bytes=$((bytes + groesse))
    melde_zeile "Basissicherung verfallen: $name"
  done < <(find "$BASIS_DIR" -maxdepth 1 -type f -name 'basis-*.tar.gz' 2> /dev/null \
    | basis_zu_verwerfen "$grenze")

  # Ausser Haus wird eigens entschieden und nicht vom oertlichen Bestand abgeleitet: Wer die
  # oertliche Sicherung von Hand geraeumt hat, haette sonst ausser Haus einen Bestand, den nie
  # wieder jemand anfasst. Die Namen dort tragen dasselbe Datum, entschluesselt wird nichts.
  while IFS= read -r name; do
    [ -n "$name" ] || continue
    if aussenhaus_entfernen "basis/$name"; then
      entfernt=$((entfernt + 1))
      melde_zeile "Basissicherung ausser Haus verfallen: $name"
    fi
  done < <(rclone lsf "$(ziel_pfad basis)" 2> /dev/null | basis_zu_verwerfen "$grenze")

  printf '%s %s\n' "$entfernt" "$bytes"
}

wal_raeumen() {
  local aelteste=$1 datei rel entfernt=0 bytes=0 groesse
  [ -d "$ARCHIV" ] || {
    printf '0 0\n'
    return 0
  }
  while IFS= read -r datei; do
    rel=${datei#"$ARCHIV"/}
    groesse=$(stat -c %s "$datei" 2> /dev/null || printf 0)
    aussenhaus_entfernen "wal/$rel.age" || true
    rm -f "$VERSANDT_DIR/wal/$rel"
    rm -f "$datei"
    entfernt=$((entfernt + 1))
    bytes=$((bytes + groesse))
  done < <(find "$ARCHIV" -type f ! -newer "$aelteste" | sort)
  printf '%s %s\n' "$entfernt" "$bytes"
}

# Die Objektschluessel, auf die heute noch Anhaenge zeigen. Scheitert die Abfrage, wird der Spiegel
# NICHT angefasst: Eine leere Liste sieht aus wie "kein Anhang wird mehr gebraucht", und danach zu
# raeumen hiesse, bei gestoerter Datenbank saemtliche Anhaenge zu loeschen.
referenzen_holen() {
  local nach=$1
  psql_aufruf --tuples-only --no-align --command="SELECT object_key FROM attachment_meta" > "$nach"
}

# Die Objektschluessel im Spiegel, die aelter sind als die Grenze — relativ zum Spiegel und damit
# in derselben Form, in der sie auch in attachment_meta stehen.
#
# Die Umwandlung in eine eigene Schleife statt `find -printf '%P'`: Letzteres gibt es nur bei GNU
# find, und die Proben laufen auch dort, wo es das nicht gibt.
spiegel_alte_schluessel() {
  local grenzdatei=$1 datei
  find "$SPIEGEL_DIR" -type f ! -newer "$grenzdatei" 2> /dev/null | while IFS= read -r datei; do
    printf '%s\n' "${datei#"$SPIEGEL_DIR"/}"
  done
}

spiegel_raeumen() {
  local grenzdatei=$1 referenzen=$2 rel entfernt=0 bytes=0 groesse
  [ -d "$SPIEGEL_DIR" ] || {
    printf '0 0\n'
    return 0
  }
  while IFS= read -r rel; do
    [ -n "$rel" ] || continue
    groesse=$(stat -c %s "$SPIEGEL_DIR/$rel" 2> /dev/null || printf 0)
    aussenhaus_entfernen "spiegel/$rel.age" || true
    rm -f "$VERSANDT_DIR/spiegel/$rel"
    rm -f "$SPIEGEL_DIR/$rel"
    entfernt=$((entfernt + 1))
    bytes=$((bytes + groesse))
    melde_zeile "Anhang verfallen und unreferenziert: $rel"
  done < <(spiegel_alte_schluessel "$grenzdatei" | spiegel_zu_verwerfen "$referenzen")
  printf '%s %s\n' "$entfernt" "$bytes"
}

verfall_lauf() {
  local beginn tage grenze aelteste referenzen="$SICHERUNG/.referenzen"
  local grenzdatei="$SICHERUNG/.verfallsgrenze"
  local ergebnis anzahl bytes gesamt=0 gesamtBytes=0
  beginn=$(jetzt)
  tage=${MANBAN_BACKUP_RETENTION_DAYS:-7}
  if ! tage_pruefen "$tage"; then
    log "FEHLER MANBAN_BACKUP_RETENTION_DAYS='$tage' ist keine Anzahl Tage groesser als null."
    protokoll retention "$beginn" fehlschlag "MANBAN_BACKUP_RETENTION_DAYS='$tage'"
    return 1
  fi
  grenze=$(verfallsgrenze "$tage")
  if ! grenzdatei_setzen "$grenze" "$grenzdatei"; then
    log "FEHLER Verfallsgrenze $grenze nicht als Datei festzuhalten."
    protokoll retention "$beginn" fehlschlag "Verfallsgrenze $grenze"
    return 1
  fi
  log "Verfallsgrenze: $grenze (${tage}d)."

  ergebnis=$(basis_raeumen "$grenze")
  read -r anzahl bytes <<< "$ergebnis"
  gesamt=$((gesamt + anzahl))
  gesamtBytes=$((gesamtBytes + bytes))

  if aelteste=$(find "$BASIS_DIR" -maxdepth 1 -type f -name 'basis-*.tar.gz' 2> /dev/null \
    | basis_aelteste_behaltene "$grenze"); then
    ergebnis=$(wal_raeumen "$aelteste")
    read -r anzahl bytes <<< "$ergebnis"
    gesamt=$((gesamt + anzahl))
    gesamtBytes=$((gesamtBytes + bytes))
  else
    log "WARN Keine Basissicherung vorhanden — das WAL-Archiv bleibt unangetastet."
  fi

  if ! referenzen_holen "$referenzen" 2> "$SICHERUNG/.fehler-retention"; then
    log "FEHLER Anhang-Metadaten nicht abgefragt — der Spiegel bleibt unangetastet."
    protokoll retention "$beginn" fehlschlag \
      "$(kurzfassung "$SICHERUNG/.fehler-retention")" "$gesamtBytes"
    return 1
  fi
  ergebnis=$(spiegel_raeumen "$grenzdatei" "$referenzen")
  read -r anzahl bytes <<< "$ergebnis"
  gesamt=$((gesamt + anzahl))
  gesamtBytes=$((gesamtBytes + bytes))
  rm -f "$referenzen" "$grenzdatei"

  log "$gesamt Staende verfallen ($gesamtBytes Bytes oertlich frei)."
  protokoll retention "$beginn" erfolg "$gesamt Staende verfallen (Grenze $grenze)" "$gesamtBytes"
}

verfall_hauptlauf() {
  umgebung_laden
  vorgaben_setzen
  # Ohne Empfaenger, und das ist der Punkt: Verfallen entschluesselt nichts und verschluesselt
  # nichts. Der Lauf gelingt auf einem Container, der nie einen privaten Schluessel gesehen hat.
  pflichtfelder_pruefen POSTGRES_PASSWORD MANBAN_BACKUP_TARGET || return 1
  mit_sperre retention verfall_lauf
}

if [ "${BASH_SOURCE[0]}" = "$0" ]; then
  verfall_hauptlauf "$@"
fi
