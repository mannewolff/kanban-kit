# KI-Leitstand 2.4 — Folieninhalt

Fertiger Inhalt für die Präsentation. Erstellt am 22.09.2026 aus dem Funktionsstand v2.4.0
(Git-Historie, `CHANGELOG.md` ab 1.27.0, `docs/nutzung.md`, `CLAUDE-design.md`, `frontend/src/`).

**Zielgruppe:** Entscheider / Management. **Umfang:** 19 Folien, ca. 25–30 Minuten.
**Erscheinungsbild:** Kupferwarte (siehe `CLAUDE-design.md`).
**Erzeugen mit:** `/folien-web-leitstand` — HTML-Präsentation **und** .pptx aus diesem Inhalt.

## Bildschirmfotos

Vier Plätze. Bis die Bilder da sind, stehen dort beschriftete Platzhalter.

| Folie | Motiv |
|---|---|
| 5 | Plattform-Leitstand, ganze Seite, 1440 × 900, mit mindestens einem aktiven **und** einem beendeten Lauf |
| 8 | Seite „Läufe" eines Projekts, ein **aufgeklappter** Lauf mit den sechs Instrumenten und mindestens einem roten Arbeitspaket |
| 11 | Bereich „Verbrauch", Ansicht **Monat**, die Kachelreihe Eingabe / Ausgabe / Kosten / Gesamt |
| 16 | Leitstand eines Boards, 1440 × 900 — wenn möglich zweimal: hell und dunkel |

---

## 1 — Titel

**Augenbraue:** kanban-kit · Version 2.4.0 · September 2026
**Titel:** KI-Leitstand
**Unterzeile:** Sehen, was die KI in der Nacht getan hat — und was es gekostet hat.
**Fuß:** Manfred Wolff · kanban-kit, quelloffen unter MIT-Lizenz

*Notiz:* kanban-kit gibt es seit dem 9. Juli 2026. Im September ist aus dem Kanban-Board ein
Leitstand für die Arbeit der KI geworden. Stand: Version 2.4.0 vom 21. September.

## 2 — Die Arbeit der KI war nicht zu sehen

Der Nacht-Runner setzt Arbeitspakete um, prüft sie und plant die nächste Nacht. Was dabei geschah,
stand im Terminal des Rechners, der ihn gestartet hat — und nirgends sonst.

Drei Karten:

- **Läuft gerade etwas?** — Nur wer die Konsole offen hatte, wusste es.
- **Ist es gelungen?** — Die Antwort lag als Datei auf einem Laptop und musste von Hand eingelesen werden.
- **Was hat es gekostet?** — Niemand hat gezählt.

*Notiz:* Ausgangspunkt. Die KI hat gearbeitet, aber die Arbeit war nicht beobachtbar.

## 3 — Die Entscheidung (Zitatfolie, helles Erscheinungsbild)

**Augenbraue:** Die Entscheidung, 16. September 2026
**Zitat:** „Die Anwendung ist der KI-Leitstand, kein Kanban-Board mit Zusatzseite."
**Darunter:** Das Board bleibt — es ist der Ort, an dem Arbeit beschrieben wird. Der Kern der
Anwendung ist seither der Leitstand: verdichtete Auskunft über Läufe, Durchsatz, Verbrauch,
Abbrüche und Vorhaben.

*Notiz:* Diese Entscheidung erklärt alles, was danach kam. Kennzahlen wie die Verweildauer je
Spalte sind herausgeflogen, der Leitstand ist Hauptansicht geworden. Quelle: `CLAUDE-design.md`.

## 4 — Drei Fragen, eine Oberfläche

- **01 Läuft es gerade?** — Der Plattform-Leitstand zeigt jeden Lauf, der arbeitet: Projekt, Dauer, pulsierender Melder.
- **02 Ist es gelungen?** — Jeder Lauf trägt seinen Ausgang als Wort, jedes Arbeitspaket einen von vier Zuständen.
- **03 Was hat es gekostet?** — Verbrauch je Nacht, Woche und Monat — und je Stufe der Kette.

*Notiz:* Roter Faden des Vortrags. Jede Frage hat im September eine eigene Antwort bekommen.

## 5 — Der Plattform-Leitstand  · BILDSCHIRMFOTO

**Augenbraue:** Neu seit Version 2.2
**Einleitung:** Die Startseite eines Plattform-Admins. Drei Bereiche, von oben nach unten:

- **Aktive Läufe** (Melder blau) — jeder Lauf, der gerade arbeitet; „läuft seit" samt bisheriger Dauer.
- **Beendete Läufe** (Melder grün) — jeder beendete Lauf der laufenden Nacht, mit seinem Ausgang als Wort.
- **Störungen** (Melder rot) — jede nicht quittierte Störung, nach Projekt gruppiert.

**Hervorgehoben:** Die Seite frischt sich selbst auf. Was sich ändert, erscheint von allein.

*Notiz:* Blick über alle Projekte hinweg — und ausschließlich über Projekte, die teilnehmen wollen.

## 6 — Drei Worte für den Ausgang eines Laufs

| Ausgang | Was er heißt |
|---|---|
| **gelungen** (grün) | Der Lauf ist durch, nichts steht aus. |
| **mit Vorbehalt** (bernstein) | Durch — das maßgebliche Arbeitspaket wartet aber noch auf einen Menschen oder wurde zurückgestellt. |
| **nicht gelungen** (zinnober) | Gescheitert, ohne Arbeit geblieben oder verstummt. |

**Platte „Die Stillefrist":** Ein unfertiger Lauf, der 90 Minuten lang kein Lebenszeichen gibt,
gilt als nicht gelungen — sonst bliebe ein abgeschossener Runner für immer als „läuft" stehen.
Ein durch Stille beendeter Lauf ist ausdrücklich keine Störung.

*Notiz:* Die Worte sind bewusst Worte und keine Farben allein. Die Stillefrist ist ein Einstellwert
der Plattform (`manban.nightrun.stille-frist`), keine Bedienoption.

## 7 — Störungen quittieren — und wer überhaupt hinsehen darf

**Der Bereich „Störungen":** Jede nicht quittierte Störung steht mit Projekt, Zeitpunkt,
anklickbarer Lauf-Kennung und Grund da. Ein Knopf „Störung löschen" räumt sie weg. Was bleibt, ist
offen — nicht übersehen.

**Die Teilnahme entscheidet das Projekt:** Ein Plattform-Admin sieht Läufe, Verbrauch und Störungen
nur von Projekten, die am Plattform-Leitstand teilnehmen. Den Haken setzt der Owner des Projekts
selbst. Erzwingen kann ihn niemand — auch der Plattform-Admin nicht.

*Notiz:* Vertrauensfrage. „Wer sieht meine Zahlen?" — die Antwort ist eingebaut, nicht verhandelbar.

## 8 — Jeder Lauf eine aufklappbare Platte  · BILDSCHIRMFOTO

**Augenbraue:** Aus „Nachtläufen" sind „Läufe" geworden

- **Zugeklappt:** Art des Laufs, Startzeitpunkt, Dauer, „N bearbeitet, M übergangen", Zustand, Herkunft und Kosten.
- **Aufgeklappt:** sechs Instrumente — Kosten, Eingabe, Ausgabe, Cache-Quote, Dauer, Pakete — und darunter jedes Arbeitspaket mit Befund und Herkunftskette.
- **Verlinkt:** die Kartennummer öffnet die Karte, die Lauf-Kennung ist adressierbar und lässt sich teilen.
- Je Projekt bleiben die letzten **190 Läufe** erhalten — genug für den laufenden Monat und den davor.

*Notiz:* Der Begriff ist bewusst geändert worden (#1101): Ein Lauf muss nicht nachts laufen.
190 = zwei Läufe je Nacht, drei Monate Rückschau.

## 9 — Vier Zustände je Arbeitspaket

- **Erfolg** (grün) — Das Arbeitspaket ist durchgelaufen.
- **Erfolg, Prüfung rot** (bernstein) — Umgesetzt, aber eine Prüfung schlug fehl.
- **Gescheitert** (zinnober) — Der Lauf kam an diesem Paket nicht durch.
- **Nicht bearbeitet** (grau) — Übergangen, etwa weil eine Abhängigkeit offen war.

**Schlusszeile:** Ein Lauf, der nichts zu tun fand, ist grau und keine Störung. Nichts zu tun zu
haben ist kein Fehler.

*Notiz:* Jeder Zustand trägt neben der Farbe auch sein Wort. Die Schlusszeile ist Issue #1121:
Vorher löste ein leerer Lauf Alarm aus, wo nichts war.

## 10 — Vom Befund zur Behebung

**Der Übernahmetext:** Unter jedem gelben und roten Arbeitspaket steht ein fertiger Text für die
eigene Entwicklungssitzung — Karte, Zustand, Fehlerklasse, Auszug. Er steht vollständig sichtbar
da, bevor „Kopieren" ihn in die Zwischenablage legt. Was in die eigene Sitzung wandert, soll man
vorher gesehen haben.

**Ausrutscher oder Muster?** An jedem gelben und roten Befund steht, in wie vielen der aufbewahrten
Läufe dieselbe Fehlerklasse vorkam — z. B. „Prüfungen rot: 4 von 30 aufbewahrten Läufen".

*Notiz:* Unterschied zwischen Monitoring und Steuerung. Eine Zahl allein sagt nicht, ob man handeln
muss. Die Häufigkeit sagt es.

## 11 — Was die KI-Arbeit gekostet hat  · BILDSCHIRMFOTO

**Augenbraue:** Nacht · Woche · Monat

- **Eingabe-Token** mit einem Balken, der „Cache gelesen" von „frisch" trennt.
- **Ausgabe-Token** mit dem Verlauf über die Nächte des Zeitraums.
- **Kosten** mit dem Vergleich zum Vorzeitraum — Richtung und Unterschied in Dollar.
- **Gesamt über die Laufzeit**, unabhängig vom gewählten Zeitraum, mit der Angabe, ab wann überhaupt gemessen wurde.

*Notiz:* Hier wird aus einem Werkzeug ein Steuerungsinstrument. Der Vergleich zum Vorzeitraum
beantwortet die Frage, die im Management wirklich gestellt wird: Wird es teurer oder günstiger?

## 12 — Zwei Gattungen, eine Summe

- **Lauf** — ein Lauf des Nacht-Runners. Die Maschine arbeitet allein.
- **Interaktive Sitzung** — eine Arbeitssitzung am Rechner eines Menschen.

**Platte:** Unter jeder Summe stehen beide Anteile: „aus Läufen" und „aus interaktiven Sitzungen".
Die Summe ist genau ihre Addition — kein Eintrag zählt in beiden.
Davon unabhängig steht die **Herkunft**: ob ein Eintrag von Hand eingelesen oder maschinell
gemeldet wurde. Gattung und Herkunft stehen nebeneinander, keine ersetzt die andere.

*Notiz:* „Kein Eintrag zählt in beiden" macht die Zahlen addierbar und damit belastbar.

## 13 — Kosten je Stufe der Kette

**Augenbraue:** Neu in Version 2.4
**Einleitung:** Ein Kettenlauf durchläuft vier Stufen. Die Auswertung zeigt je Stufe die Kosten im
Zeitraum, wie viele Vorgänge sie durchlaufen haben, und einen Balken im Verhältnis zur teuersten
Stufe.

**Stufenband:** Plan → Prüfung → Pakete → Abdeckung

**Schlusszeile:** Es gibt keine Zeile „ohne Stufe": Sie trüge bei einem gewöhnlichen Lauf den
Verbrauch einer ganzen Nacht, und die Aufstellung handelt von der Kette.

*Notiz:* Feinste Auflösung, die es derzeit gibt — nicht nur, was eine Nacht gekostet hat, sondern
welcher Schritt der Kette. Damit lässt sich der Prozess selbst optimieren.

## 14 — Akzentfolie (Kupfergrund)

**Titel:** „Nicht gemessen" ist keine Null
**Unterzeile:** Wo eine Zahl fehlt, steht ein Strich und der Grund. Eine Null behauptete eine
Messung, die es nicht gab.

Drei Karten:

- **nicht erfasst** — Der Zeitraum liegt ganz vor dem Beginn der Messung.
- **teilweise erfasst** — Der Zeitraum schneidet den Beginn; die Zahl ist zu klein.
- **nicht gemessen** — Ein bekannter Eintrag ohne Zahl.

*Notiz:* Die eine Folie zum Merken. Eine Leitstand-Zahl ist nur so viel wert wie das Vertrauen in
sie. Drei verschiedene Lücken werden drei verschiedene Worte, statt alle zu einer Null zu werden.

## 15 — Wie die Zahlen ans Board kommen

**Normalweg — der Runner liefert selbst ein** (grün gerahmt): Er meldet den Lauf beim Start über
ein projektgebundenes Zugriffstoken und schreibt ihn fort, bis er abgeschlossen ist. Ohne jeden
Handgriff.

**Rückfall — Protokoll einlesen:** Kam ein Lauf nicht an, liest ein Knopf die Ergebnisdatei. Sie
wird im Browser ausgewertet und nicht hochgeladen — an den Server geht allein die verdichtete
Auswertung.

**Platte „Der Stand am Board gewinnt":** Wird die Datei eines bereits eingelieferten Laufs
eingelesen, entsteht kein zweiter Lauf und kein Wert ändert sich. Doppelte Zählung ist
ausgeschlossen.

*Notiz:* Datenschutz-Punkt. Die Protokolldatei trägt Pfade und Inhalte aus der
Entwicklungssitzung. Sie verlässt den Rechner nicht.

## 16 — Kupferwarte  · BILDSCHIRMFOTO (helles Erscheinungsbild)

**Augenbraue:** Ein neues Erscheinungsbild

- Eine verbindliche Designquelle für die ganze Anwendung: Palette, Schriften, Radien, Tiefe und Rahmen — jede Ansicht folgt derselben Vorlage.
- Zwei Erscheinungsbilder, hell und dunkel. Ohne Schalter: Die Anwendung folgt dem, was das System einstellt.
- Leitfarbe ist Kupfer. Die Melderfarben tragen ausschließlich Zustände und werden nie als Akzent verwendet.
- Farbfelder: Kupfer `#A85F2C`, Grün `#2F8F4E`, Bernstein `#B07C15`, Zinnober `#C8393E`, Stahl `#2F6FC9`, Grau `#8A929E`

*Notiz:* Abgenommen wird visuell: je Ansicht ein Bildschirmfoto neben der Vorlage, hell und dunkel.
Tests sichern Werte, Kontrast und Verhalten — ob eine Ansicht aussieht wie die Vorlage, sagen sie nicht.

## 17 — Qualität, die der Build erzwingt

- **100 %** Zeilen und Zweige, Backend — JaCoCo-Schwelle im Maven-Build
- **100 %** Mutationsabdeckung — PIT-Schwelle; Tests, die nichts prüfen, fallen auf
- **100 %** Frontend — Zeilen, Zweige, Funktionen, Anweisungen (Vitest-Schwelle)

**Schlusszeile:** Rund **2.450** Backend-Tests, **100** Integrationstest-Klassen und **2.600**
Frontend-Tests. Reißt eine Schwelle, bricht der Build — es gibt keinen Schalter, der sie übergeht.

*Notiz:* Der Punkt für Entscheider: Diese Anwendung ist weitgehend von KI geschrieben worden.
Genau deshalb sind die Schwellen so hoch und im Build verankert.
Belege: `pom.xml` (JaCoCo `minimum` 1.00 für LINE und BRANCH, PIT `mutationThreshold` 100),
`frontend/vite.config.ts` (`thresholds` 100/100/100/100).

## 18 — Wo wir stehen

Vier Zahlenkacheln:

- **2.4.0** — Stand vom 21.09.2026
- **1.036** — Commits seit dem 9. Juli
- **366** — davon in den letzten drei Wochen
- **176k** — Zeilen in 1.152 Dateien

**Platte „Technik":** Spring Boot auf Java 21, Postgres und MinIO im Backend; React und Vite im
Frontend; alles hinter Caddy mit automatischem TLS, betrieben über Docker Compose. Selbst zu
hosten, quelloffen unter MIT-Lizenz.

*Notiz:* 366 von 1.036 Commits in drei Wochen — der Leitstand ist in kurzer Zeit entstanden. Das ist
zugleich der Grund, warum die Dokumentation an einigen Stellen hinterherlief.

## 19 — Was als Nächstes kommt (helles Erscheinungsbild)

- **01 Durchsatz und Lastgrenzen** — Personenbezogene Durchsatzbremse, dimensionierter Verbindungspool, Lastprofil und Messwerkzeug für den Durchsatznachweis; in Arbeit (#996–#999).
- **02 Der Rest in Kupferwarte** — Die Nachtansicht des Verbrauchs zieht noch ihre eigene, ältere Vorlage. Sie geht als Letztes im neuen Erscheinungsbild auf.
- **03 Die Doku nachziehen** — README, Doku-Startseite und der Abschnitt über den Board-Leitstand sind am 22.09. nachgezogen; offen bleiben der Oberflächenbegriff „Nachtlauf" in der Betriebsdoku und das Factsheet.

**Schlusszeile:** Fragen, Widerspruch, Wünsche — gern jetzt.
**Fuß:** Manfred Wolff · github.com/mannewolff/kanban-kit

*Notiz:* Offen schließen. Was noch aussteht, steht auf der Folie — das ist glaubwürdiger als ein
Schlussbild ohne offene Punkte.
