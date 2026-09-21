#!/bin/sh
# Wartet, bis eine URL mit HTTP 200 antwortet (Issue #1100).
#
# Gebrauch: scripts/warte-auf-bereitschaft.sh <url> <sekunden>
#
# Zweck: `docker compose up -d --build` gilt als gelungen, sobald die Container
# gestartet sind — auch wenn die Anwendung danach beim Hochfahren scheitert (Flyway,
# fehlende Umgebungsvariable). Spring Boot nimmt HTTP erst an, wenn der Kontext steht;
# antwortet eine oeffentliche Seite mit 200, ist die Anwendung wirklich hochgefahren.
#
# POSIX-sh wie .githooks/pre-commit, weil auf dem Deploy-Server kein Node
# vorausgesetzt ist. Eigene Datei statt einer Schleife im Workflow-YAML, damit sie
# sich lokal in beide Richtungen pruefen laesst.
#
# Exit 0  = bereit.
# Exit 1  = Frist verstrichen oder keine 200 (eine Zeile auf stderr mit URL,
#           Wartezeit und letzter Antwort bzw. letztem Fehler).
# Exit 2  = fehlende oder ungueltige Argumente (Gebrauchszeile auf stderr).
set -u

# Abstand zwischen zwei Versuchen. Klein genug, dass ein kurzer Deploy nicht
# unnoetig wartet, gross genug, dass die Anwendung nicht im Sekundentakt
# angeklopft wird.
ABSTAND=3

gebrauch() {
  echo "Gebrauch: $0 <url> <sekunden> — wartet, bis <url> mit HTTP 200 antwortet." >&2
  exit 2
}

[ "$#" -eq 2 ] || gebrauch

url="$1"
frist="$2"

case "$url" in
  http://?* | https://?*) ;;
  *) gebrauch ;;
esac

# Nur ganze Sekunden > 0. Der case faengt Leerstring und alles Nichtnumerische ab,
# bevor [ -gt ] mit einem Syntaxfehler statt der Gebrauchszeile endet.
case "$frist" in
  '' | *[!0-9]*) gebrauch ;;
esac
[ "$frist" -gt 0 ] || gebrauch

beginn=$(date +%s)
letzte="kein Versuch ausgefuehrt"

while :; do
  # -f laesst curl bei jedem Status ausser 2xx mit ungleich 0 enden, -w liefert den
  # Status trotzdem. stderr wandert mit in die Variable: Verbindungsfehler und
  # HTTP-Status landen so in derselben Meldung. tr macht daraus eine Zeile.
  antwort=$(curl -fsS --max-time 5 -o /dev/null -w 'HTTP %{http_code}' "$url" 2>&1)
  rc=$?
  antwort=$(printf '%s' "$antwort" | tr '\n' ' ')

  if [ "$rc" -eq 0 ] && [ "$antwort" = "HTTP 200" ]; then
    echo "Bereit: $url antwortet mit HTTP 200 nach $(( $(date +%s) - beginn ))s."
    exit 0
  fi
  letzte="$antwort"

  verstrichen=$(( $(date +%s) - beginn ))
  if [ "$verstrichen" -ge "$frist" ]; then
    echo "Nicht bereit: $url lieferte innerhalb von ${frist}s kein HTTP 200 (gewartet ${verstrichen}s); letzte Antwort: ${letzte}" >&2
    exit 1
  fi
  sleep "$ABSTAND"
done
