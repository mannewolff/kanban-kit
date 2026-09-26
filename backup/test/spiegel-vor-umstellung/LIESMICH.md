# Ein Anhang-Spiegel von vor der Umstellung (Issue #1228, E15)

Dieser Dateibaum ist der Nachweis, dass Sicherungen, die noch mit `mc mirror` entstanden sind,
ohne Formatmigration zurueckgeholt werden koennen.

## Warum er eingecheckt ist

`mc` liegt nach dieser Umstellung nirgends mehr — weder im Sicherungs-Abbild noch als Bezugsquelle
(das MinIO-Abbild auf quay.io ist anonym nicht mehr beziehbar, genau das war der Anlass des
Vorhabens). Ein Alt-Spiegel laesst sich zur Laufzeit also nicht mehr erzeugen. Damit der Nachweis
trotzdem bei jedem Lauf gefuehrt werden kann, liegt er hier als Fixtur.

## Was er enthaelt

* `spiegel/` — der Dateibaum, wie `mc mirror <alias>/<bucket> <verzeichnis>` ihn abgelegt hat: die
  Objektschluessel als Pfade, ein Objekt je Datei, keine Begleit- oder Metadatendatei. Die
  Schluessel folgen der Form, die `AttachmentService` vergibt: `cards/<kartennummer>/<uuid>`.
  Die Inhalte sind klein und bewusst verschieden — eine Datei mit Nullbytes und gesetztem
  hoechsten Bit, ein PDF-Kopf, reiner Text —, damit ein Vergleich, der nur Groessen prueft oder
  unterwegs Zeilenenden umschreibt, auffaellt.
* `spiegel-stand` — der Stempel des letzten gelungenen Spiegel-Laufs, wie `backup.sh` ihn neben den
  Spiegel schreibt (nicht hinein).

## Wie der Nachweis gefuehrt wird

`backup/test/restore-roundtrip.sh` haengt dieses Verzeichnis in den Sicherungs-Container, holt es
mit dem neuen Werkzeug (`rclone`) in den neuen Objektspeicher zurueck, liest es von dort wieder
heraus und vergleicht byteweise gegen die Fixtur. Bleibt der Baum dabei unveraendert, ist gezeigt,
was E15 behauptet: `mc mirror` und `rclone copy` legen denselben Baum ab, und ein alter Spiegel
braucht keinen Uebersetzungsschritt.

**Nicht anfassen.** Eine Fixtur, die mit dem Code mitwandert, beweist nichts mehr.
