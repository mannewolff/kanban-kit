# Prüfbefund: Dokumentation gegen Funktionsstand 2.4.0

Stand: 22.09.2026 · Geprüft gegen `VERSION` 2.4.0, `CHANGELOG.md` ab 1.27.0, `frontend/src/App.tsx`,
`frontend/src/layout/navItems.ts`, `CLAUDE-design.md`.

Anlass: Zwischen dem 31.08. (v1.26) und dem 21.09. (v2.4.0) sind 366 der insgesamt 1.036 Commits
entstanden — darunter der Plattform-Leitstand, die Umbenennung „Nachtlauf" → „Lauf", die
Verbrauchsauswertung je Stufe der Kette und das Erscheinungsbild „Kupferwarte".

## Erledigt mit diesem Befund

| Datei | Was geändert wurde |
|---|---|
| `README.md` | Einstieg beschreibt jetzt KI-Leitstand **und** Board; Läufe, Plattform-Leitstand und Verbrauch benannt; Doku-Liste um Läufe, Verbrauch, Plattform-Leitstand, Produktions-Deployment und die Designquelle ergänzt |
| `docs/index.md` | Einstieg und Inhaltsverzeichnis auf den Leitstand umgestellt; Plattform-Leitstand und Board-Leitstand verlinkt; Versionsstand und Verweis auf `CLAUDE-design.md` ergänzt |
| `docs/nutzung.md` | § „Dashboard (Kennzahlen)" ersetzt durch § „Leitstand eines Boards" (Anker `#leitstand`), vor die § „Läufe" gezogen — Laufband, vier Kennzahl-Kacheln, Verbrauch, Herkunft, Rumpf und Rechtelage; festgehalten, dass Verweildauer je Spalte und Ausreißer nicht mehr dargestellt werden |

## Offen — nach Gewicht

### 1. Oberflächenbegriff „Nachtlauf" in `docs/betrieb.md`

Issue #1101 hat den Begriff in der Oberfläche durch „Lauf" ersetzt. In der Betriebsdoku steht er
noch an vier Stellen (Z. 257–258, 338–339, 347–348). Die *technischen* Namen
(`manban.nightrun.*`, Tabelle `night_run`) bleiben richtig und sollen nicht mitgeändert werden —
zu ändern ist nur, wo von dem die Rede ist, was ein Mensch auf dem Bildschirm liest.

Ebenso die Überschrift `docs/dogfooding.md` § „7. Nachtlauf-Ergebnis einliefern"; der Inhalt des
Abschnitts stimmt.

### 2. Das Factsheet ist acht Minor-Versionen alt

`praesentationen/README.md` weist `kanban-kit-factsheet.pdf` als „August 2026, Version 1.26.1" aus.
Es kennt weder Plattform-Leitstand noch Verbrauchsauswertung noch Kupferwarte. Die Kennzahlen darin
sind ebenfalls überholt.

**Vorschlag:** Nach der neuen Präsentation aus denselben Zahlen neu erzeugen.

## Geprüft und in Ordnung

- `docs/nutzung.md` §§ „Leitstand eines Boards" (neu), „Läufe", „Verbrauch (Leitstand)", „Plattform-Leitstand" — auf dem Stand
  2.4.0, einschließlich Stillefrist, der drei Ausgangsworte und der Kosten je Stufe der Kette.
- `docs/rollen-und-rechte.md` — Teilnahme am Plattform-Leitstand und die daran hängende
  Leseberechtigung sind eingearbeitet.
- `CLAUDE-design.md` — beschreibt den tatsächlichen Stand, einschließlich der noch offenen
  Ausnahme (Nachtansicht des Verbrauchs).
