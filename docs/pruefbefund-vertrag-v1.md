# Vertrag für Vorschlags- und Protokollblöcke — Fassung 1

Dieses Dokument legt die Form fest, in der ein einzelner Prüfbefund als **maschinell übernehmbarer
Vorschlag** an einem Ticket abgelegt wird, und die Form, in der die getroffene Entscheidung als
**Protokoll** zurückgeschrieben wird. Es beschreibt einen Datenvertrag, kein Verhalten: Es gibt in
Fassung 1 weder einen Parser noch eine Oberfläche noch einen Endpunkt dafür. Beides folgt in eigenen
Arbeitspaketen und baut auf dieser Festlegung auf (Issue #576, Plan Issue #580, Vertrag Issue #585).

## Warum es diesen Vertrag gibt

Prüfbefunde stehen heute als Fließtext im Kommentar, und ihre Form ist von Lauf zu Lauf verschieden.
Im Prüfungslauf zu Issue #574 lieferten die beiden Prüfer **desselben Laufs** zwei verschiedene
Formate: `codex` nummerierte `1. **BLOCKER – …**`, `fable` schrieb `**WICHTIG 1 — …**`. Ein Zerleger
für Fließtext müsste beide treffen und bräche bei jeder Formulierungsänderung — und zwar **still**,
also ohne dass jemand den Verlust bemerkt.

Der Vertrag löst das, indem er den lesbaren Befundtext unangetastet lässt und die maschinell
verwertbare Fassung **daneben** stellt. Der Prüfer schreibt weiter, wie er schreibt; die Struktur
liegt zusätzlich im selben Kommentar.

## Rahmen: wie ein Block im Kommentar liegt

Ein Vertragsblock ist ein Markdown-Codeblock mit einem reservierten Info-String. Er besteht aus
genau drei Zeilen:

````text
```pruefbefund-vorschlaege
{"formatVersion":1,"runId":"…","proposals":[…]}
```
````

- **Startkennung** — eine Zeile, die exakt `` ```pruefbefund-vorschlaege `` (Vorschlagsblock) oder
  exakt `` ```pruefbefund-protokoll `` (Protokollblock) lautet, ohne Einrückung und ohne weitere
  Zeichen.
- **Rumpf** — genau **eine** Zeile mit einem JSON-Objekt nach RFC 8259.
- **Endkennung** — eine Zeile, die exakt ` ``` ` lautet.

Ein Kommentar darf höchstens einen Vorschlagsblock und höchstens einen Protokollblock enthalten. Der
Block verdrängt den lesbaren Befundtext nicht: Er steht **zusätzlich** im selben Kommentar, üblich
am Ende.

### Warum ein Codeblock und nicht ein HTML-Kommentar

Ein HTML-Kommentar wäre in der Oberfläche unsichtbar und damit hübscher. Er endet aber an der ersten
Zeichenfolge `-->`, und der Ersatztext eines Vorschlags ist beliebiger, von Menschen und Modellen
geschriebener Text. Ein Vorschlag, der `-->` enthält, zerrisse den Block still in der Mitte — genau
der Fehlermodus, den dieser Vertrag abschaffen soll. Ihn durch eine Escaping-Pflicht zu verhindern,
verschöbe das Problem nur auf einen Erzeuger, der sie vergisst.

Der Codeblock hat diese Kollision nicht, **weil der Rumpf genau eine Zeile ist**: JSON verlangt für
alle Steuerzeichen unterhalb `U+0020` — also auch für Zeilenumbrüche — eine Escape-Folge. Ein
einzeiliger JSON-Rumpf bleibt deshalb einzeilig, gleichgültig was in den Texten steht, und keine
Rumpfzeile kann je mit ` ``` ` beginnen. Die Kollision ist damit nicht unwahrscheinlich, sondern
unmöglich. Sichtbarkeit ist der Preis dafür; Korrektheit geht vor visueller Präferenz.

### Erkennung des Protokollblocks ohne Datenbank-Typisierung

Ein Protokollblock ist **allein aus dem Kommentartext** erkennbar — an seiner Startkennung
`` ```pruefbefund-protokoll ``. Es braucht dafür keine Kommentar-Typisierung in der Datenbank und
keinen Metadatensatz daneben. Das ist Absicht: Kommentare werden exportiert, kopiert, über die
kanbancompat-Schnittstelle eingespielt und von Hand nachbearbeitet. Ein Protokoll, dessen Natur nur
in einer Spalte danebensteht, verlöre auf jedem dieser Wege seine Bedeutung.

## Formatversion

Beide Blockarten tragen im Rumpf das Feld `formatVersion`. In Fassung 1 ist der einzige gültige Wert
die Zahl `1`.

Die Version steht **ausschließlich** im Rumpf, nicht im Info-String. Ein Block einer künftigen
Fassung wird dadurch von einem Leser der Fassung 1 als Block **erkannt** und mit
`UNKNOWN_FORMAT_VERSION` abgewiesen, statt unbemerkt als „kein Block" durchzurutschen.

## Lauf-Kennung

Beide Blockarten tragen das Feld `runId`: eine UUID in der kanonischen 36-Zeichen-Form,
ausschließlich in Kleinbuchstaben (`8-4-4-4-12`).

Die `runId` verbindet Vorschläge und Protokoll **desselben Prüfungslaufs**. Sie wird beim Erzeugen
des ersten Vorschlagsblocks eines Laufs vergeben und in jedem Protokollblock desselben Laufs
wiederholt. Ein Lauf darf mehrere Protokollblöcke haben — Entscheidungen dürfen nacheinander fallen.

## Vorschlagsblock

Startkennung `` ```pruefbefund-vorschlaege ``. Rumpf:

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `formatVersion` | Zahl | ja | Fassung des Rumpfes, in Fassung 1 stets `1` |
| `runId` | Text | ja | Lauf-Kennung, siehe oben |
| `proposals` | Liste | ja | mindestens ein Vorschlag |

Je Eintrag in `proposals`:

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `proposalId` | Text | ja | stabile Kennung, `[a-z0-9][a-z0-9-]{0,63}`, je Lauf eindeutig |
| `target` | Objekt | ja | Zieladressierung, siehe unten |
| `expectedText` | Text | ja | erwarteter Ausgangstext am aufgelösten Ziel, nicht leer |
| `replacementText` | Text | ja | Ersatztext; leer bedeutet Streichung |
| `findingRef` | Text | ja | Verweis auf den lesbaren Befund im selben Kommentar, nicht leer |

`findingRef` ist bewusst freier Text (etwa `"BLOCKER 2"` oder `"WICHTIG 1"`). Die Prüfer schreiben
ihre Befunde unterschiedlich; nur die Struktur daneben ist verbindlich, nicht die Prosa.

Beispiel:

````text
Der Abschnitt „Aufgabe" nennt die Rolle zweimal unterschiedlich.

```pruefbefund-vorschlaege
{"formatVersion":1,"runId":"6f1c3a2e-9d4b-4d1f-8e77-2b0a5c9d1e34","proposals":[{"proposalId":"p1","target":{"kind":"TEXT_SPAN"},"expectedText":"der Betreuer","replacementText":"die zuständige Person","findingRef":"WICHTIG 1"}]}
```
````

## Protokollblock

Startkennung `` ```pruefbefund-protokoll ``. Rumpf:

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `formatVersion` | Zahl | ja | Fassung des Rumpfes, in Fassung 1 stets `1` |
| `runId` | Text | ja | Lauf-Kennung des Laufs, dessen Vorschläge entschieden werden |
| `entries` | Liste | ja | mindestens ein Protokolleintrag |

Je Eintrag in `entries`:

| Feld | Typ | Pflicht | Bedeutung |
|---|---|---|---|
| `proposalId` | Text | ja | Kennung eines Vorschlags **desselben Laufs** |
| `decision` | Text | ja | genau `angenommen` oder `abgelehnt` |
| `reason` | Text | bei `abgelehnt` | Begründung, nach Entfernen von Leerraum nicht leer |

Regeln:

- Eine Vorschlagskennung kommt **pro Lauf** höchstens einmal im Protokoll vor — auch über mehrere
  Protokollblöcke hinweg.
- Jede protokollierte Kennung muss in einem Vorschlagsblock **desselben Laufs** existieren.
- Noch nicht entschiedene Vorschläge **fehlen** im Protokoll. Ein fehlender Eintrag ist kein Fehler
  und keine Ablehnung, sondern eine offene Entscheidung.
- Bei `angenommen` ist `reason` erlaubt, aber nicht gefordert.

Beispiel:

````text
```pruefbefund-protokoll
{"formatVersion":1,"runId":"6f1c3a2e-9d4b-4d1f-8e77-2b0a5c9d1e34","entries":[{"proposalId":"p1","decision":"abgelehnt","reason":"Der Begriff ist im Glossar so festgelegt."}]}
```
````

## Zieladressierung

Das Ziel eines Vorschlags ist der **Beschreibungstext der Karte**, gegen den der Prüfungslauf lief.
`target.kind` benennt die Zielart; in Fassung 1 sind genau zwei zulässig.

### `TEXT_SPAN` — ein Ausschnitt im Beschreibungstext

```
"target": { "kind": "TEXT_SPAN" }
```

Auflösung: `expectedText` wird nach der Normalisierung als Teilkette im normalisierten
Beschreibungstext gesucht.

- kein Vorkommen → `TARGET_NOT_FOUND`
- mehr als ein Vorkommen → `TARGET_AMBIGUOUS`
- genau ein Vorkommen → aufgelöst; dieser Ausschnitt wird durch `replacementText` ersetzt

`EXPECTED_TEXT_MISMATCH` tritt bei dieser Zielart **nicht** auf: Die Adressierung geschieht durch den
Ausgangstext selbst, ein geänderter Ausgangstext ist deshalb dasselbe wie ein nicht gefundenes Ziel
und wird als `TARGET_NOT_FOUND` gemeldet. Eine zusätzliche Vorkommens-Nummerierung („das dritte
Vorkommen") gibt es bewusst nicht — sie wäre gegen jede Umstellung des Textes anfällig, ohne dass
der Vorschlag ungültig aussähe.

### `SECTION` — ein ganzer Abschnitt

```
"target": { "kind": "SECTION", "headingPath": ["Aufgabe"] }
```

`headingPath` ist der Überschriftenpfad von der obersten Ebene abwärts, jede Stufe als reiner
Überschriftentext ohne `#` und ohne Nummerierung. Das Ziel ist der **Abschnittsrumpf**: alles
zwischen der Überschrift und der nächsten Überschrift derselben oder einer höheren Ebene, ohne die
Überschriftenzeile selbst.

- kein passender Pfad → `TARGET_NOT_FOUND`
- mehrere Überschriften mit demselben Pfad → `TARGET_AMBIGUOUS`
- genau ein Pfad, aber Rumpf ≠ `expectedText` → `EXPECTED_TEXT_MISMATCH`
- genau ein Pfad und Rumpf = `expectedText` → aufgelöst; der Rumpf wird durch `replacementText`
  ersetzt

Diese eigene Zielart existiert, weil ein Befund, der sich auf einen ganzen Abschnitt bezieht, über
`TEXT_SPAN` nur als riesige, gegen jede Kleinänderung anfällige Textkopie adressierbar wäre.

### Normalisierung

Vor jedem Vergleich werden **beide** Seiten — der Zieltext und `expectedText` — gleich normalisiert:

1. Zeilenenden: `\r\n` und `\r` werden zu `\n`.
2. Leerraum am **Zeilenende** (Leerzeichen, Tabulator) wird entfernt.
3. Bei `SECTION` werden Leerzeilen am Anfang und Ende des Abschnittsrumpfes entfernt.

Weiter greift die Normalisierung **nicht**. Insbesondere findet keine Unicode-Normalisierung statt
und Einrückungen sowie Leerzeilen im Inneren bleiben bedeutungstragend. Wer weniger vergleicht,
trifft mehr — und trifft irgendwann das Falsche.

Der Ersatztext wird **unverändert** eingesetzt, nicht normalisiert. Was der Vorschlag sagt, kommt so
in den Text.

## Geschützte Kennzeichnungszeilen

Kennzeichnungszeilen tragen den Prozesszustand eines Tickets. Ein Vorschlag, dessen aufgelöstes Ziel
eine solche Zeile ganz oder teilweise enthält, wird mit `PROTECTED_MARKER_TARGETED` abgewiesen: Eine
Textverbesserung darf keinen Prozesszustand verändern.

### Normativ geschützt

- `Autor-Modell:` — welches Modell den Body geschrieben hat
- `Plan-Modell:` — welches Modell den zugrunde liegenden Plan geschrieben hat
- `Fachliche Quelle:` — Rückverweis auf das fachliche Issue
- `Pruefung:` — die Prüfvorgabe des Menschen (`1`, `2`, `3` oder `Verzicht`)
- `Fachplan-Review:` — Nachweis der Prüfung auf der fachlichen Stufe
- `Plan-Review:` — Nachweis der Prüfung auf der Plan-Stufe
- `Issue-Review:` — Nachweis der Prüfung auf der Issue-Stufe

Diese Liste ist in Fassung 1 **abschließend**. Sie wurde aus dem claude-workflow-kit abgeleitet, aus
Commit `8a35c4bb9848f0b48e1d74362d3fcfad12a55603` (v1.38.1): `AUTOR_MODELL_ZEILE` und
`PRUEFUNG_ZEILE` in `kit/board.mjs`, `STUFEN_MARKER` in `kit/night.mjs` sowie die Konventionen
`Plan-Modell:` und `Fachliche Quelle:` aus den Skills `plan` und `issues`.

Der externe Bestand ist **kein** Prüfgegenstand dieses Repositories: `.claude/*` ist per
`.gitignore` ausgeschlossen, die Dateien des Kits liegen hier nicht versioniert vor, und ein
automatischer Abgleich gegen eine nicht versionierte Datei wäre nicht reproduzierbar. Geprüft wird
deshalb die Liste selbst, gegen die im Test hinterlegte Referenz.

### Ausdrücklich nicht geschützt

`Pruefung-Stand:` gehört **nicht** zu den geschützten Zeilen. Die Zeile ist kein Prozesszustand,
sondern ein Bezugsstand des Bodys: ein SHA-256 über alles außerhalb des Kontext-Abschnitts, den
`board.mjs` bei jedem Schreiben neu berechnet. Bliebe sie beim Übernehmen eines Vorschlags
unverändert stehen, während der Body sich ändert, sähe eine gesetzte Prüfvorgabe anschließend
**verfallen** aus — die Freigabe des Menschen wäre still entwertet.

## Längengrenze

Die Grenze gilt für den **vollständig serialisierten Kommentar** aus lesbarem Befundtext und
strukturiertem Block, nicht für den Block allein. Ein für sich gültiger Block kann zusammen mit dem
erhaltenen Befundtext trotzdem einen ungültigen Kommentar ergeben.

Gemessen wird wie in `TextLimits.MAX_TEXT` mit `String.length()`, also in **UTF-16-Codeeinheiten**.
Ein Emoji außerhalb der BMP zählt als zwei. 50.000 Einheiten sind gültig, 50.001 liefern
ausschließlich `COMMENT_TOO_LONG` — auch dann, wenn der Kommentar zusätzlich andere Regeln verletzt.

## Rückwärtskompatibilität

- Ein Kommentar ohne **vollständiges** Paar aus Start- und Endkennung ergibt „kein Vertragsblock"
  und bleibt unverändert. Das ist **kein** Fehler: Der gesamte historische Bestand besteht aus
  solchen Kommentaren.
- Eine Kennzeichnungszeile **innerhalb** eines umschließenden Markdown-Codeblocks ist kein
  Vertragsblock. Ein Dokument, das den Vertrag erklärt — dieses hier zum Beispiel —, enthält seine
  eigenen Kennzeichnungen als Beispiel und darf davon nicht getroffen werden.
- Ein **erkannter** Block, also einer mit vollständigem Kennungspaar außerhalb eines umschließenden
  Codeblocks, wird nie still übersprungen. Er ist entweder gültig oder liefert genau einen
  Fehlercode aus der folgenden Tabelle; „kein Block vorhanden" ist für ihn keine zulässige Antwort.

## Fehlertabelle

Diese Tabelle ist normativ. Trifft mehr als eine Bedingung zu, gilt die zuerst genannte — die
Reihenfolge der Zeilen ist die Prüfreihenfolge.

| Code | Blockart | Auslösebedingung | Ergebnis |
|---|---|---|---|
| `COMMENT_TOO_LONG` | beide | Der serialisierte Kommentar aus Befundtext und Block hat mehr als 50.000 UTF-16-Codeeinheiten. | Kommentar wird nicht geschrieben; kein Vorschlag wird angewendet. |
| `MALFORMED_BLOCK` | beide | Kennungspaar vollständig, aber der Rumpf ist kein einzeiliges JSON-Objekt nach RFC 8259. | Block abgewiesen; kein Vorschlag wird angewendet. |
| `UNKNOWN_FORMAT_VERSION` | beide | `formatVersion` fehlt oder ist etwas anderes als die Zahl `1`. | Block abgewiesen; kein Vorschlag wird angewendet. |
| `DUPLICATE_PROPOSAL_ID` | beide | Eine `proposalId` kommt im selben Lauf zweimal in `proposals` oder zweimal in `entries` vor. | Block abgewiesen; kein Vorschlag wird angewendet. |
| `TARGET_MISSING` | Vorschlagsblock | `target` fehlt, ist kein Objekt, oder `target.kind` ist weder `TEXT_SPAN` noch `SECTION`. | Der betroffene Vorschlag wird abgewiesen. |
| `TARGET_NOT_FOUND` | Vorschlagsblock | Die Adressierung löst auf kein Vorkommen auf. | Der betroffene Vorschlag wird abgewiesen. |
| `TARGET_AMBIGUOUS` | Vorschlagsblock | Die Adressierung löst auf mehr als ein Vorkommen auf. | Der betroffene Vorschlag wird abgewiesen. |
| `EXPECTED_TEXT_MISMATCH` | Vorschlagsblock, Zielart `SECTION` | Der Abschnittsrumpf stimmt nach der Normalisierung nicht mit `expectedText` überein. | Der betroffene Vorschlag wird abgewiesen. |
| `PROTECTED_MARKER_TARGETED` | Vorschlagsblock | Das aufgelöste Ziel enthält eine geschützte Kennzeichnungszeile ganz oder teilweise. | Der betroffene Vorschlag wird abgewiesen. |
| `UNKNOWN_PROPOSAL_ID` | Protokollblock | Eine protokollierte `proposalId` existiert in keinem Vorschlagsblock desselben Laufs. | Block abgewiesen; keine Entscheidung wird übernommen. |
| `UNKNOWN_DECISION` | Protokollblock | `decision` fehlt oder ist weder `angenommen` noch `abgelehnt`. | Block abgewiesen; keine Entscheidung wird übernommen. |
| `MISSING_REASON` | Protokollblock | `decision` ist `abgelehnt` und `reason` fehlt oder ist nach Entfernen von Leerraum leer. | Block abgewiesen; keine Entscheidung wird übernommen. |

## Testmaterial

Die kanonischen Testdatensätze liegen unter `src/test/resources/pruefbefund-vertrag/v1/`. Jede Datei
ist ein vollständiger Kommentartext; Zieltexte für die Auflösung liegen als eigene Dateien daneben.
Das Manifest `manifest.json` nennt zu jedem Datensatz die Blockart, den zugehörigen Zieltext und die
Erwartung — einen Fehlercode aus der Tabelle oben, `GUELTIG` oder `KEIN_VERTRAGSBLOCK`.

`PruefbefundVertragV1Test` hält Doku, Manifest und Datensätze aneinander: zu jeder Zeile der
Fehlertabelle mindestens ein Datensatz, keine Datei ohne Manifest-Eintrag, eindeutige Kennungen in
den gültigen Datensätzen, die Markerliste dieser Doku gegen die abgestimmte Referenz und die
Grenzdatensätze exakt an `TextLimits.MAX_TEXT`. Der Test validiert die Grammatik **nicht** — ein
Parser wäre Produktivcode und gehört in ein Folgepaket.
