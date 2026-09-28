# Beitragen

Schön, dass du mitmachen willst. Dieses Projekt ist klein und bewusst schlank — damit
dein Beitrag nicht an Formalien hängenbleibt, stehen hier die vier Dinge, auf die es
ankommt. Alles andere klären wir im Issue.

Es gilt der [Verhaltenskodex](CODE_OF_CONDUCT.md); Sicherheitslücken bitte **nicht** als
Issue, sondern nach [SECURITY.md](SECURITY.md) melden.

## 1. Erst ein Issue, dann die Arbeit

Mach vor einer größeren Änderung ein Issue auf und beschreibe kurz, was du vorhast. So
sehen wir früh, ob die Richtung passt — und niemand arbeitet umsonst an etwas, das am
Ende nicht ins Projekt gehört. Für Tippfehler, kaputte Links und Einzeiler brauchst du
das nicht.

## 2. Commit-Titel, wie das Repo sie heute führt

Ein Satz, der sagt, was die Änderung bewirkt, und am Ende `(Issue #N)`:

```
Zählbremse greift auch bei Anfragen ohne Sitzungscookie (Issue #1234)
```

Die Titel wandern unverändert in den Changelog — `scripts/gen-changelog.mjs` übernimmt
sie wörtlich. Deshalb lohnt der eine verständliche Satz.

Ein Präfix nach Conventional Commits (`feat:`, `fix:`, …) ist willkommen, aber keine
Pflicht. Offen gesagt: Das Projekt selbst hält sich nicht durchgehend daran.

## 3. Prüfungen je geändertem Bereich

Fahre die Prüfungen der Bereiche, die deine Änderung berührt — nicht mehr:

| Geänderter Bereich | Prüfungen |
|---|---|
| Backend (`src/`, `pom.xml`, `config/`) oder Doku (`docs/`, `docs-site/`, `README.md`) | `mvn verify` — kompiliert, fährt Unit- und Integrationstests, misst die Abdeckung und lässt die statische Analyse laufen |
| Frontend (`frontend/`) | zuerst `npm --prefix frontend ci`, dann `npm --prefix frontend run build`, `npm --prefix frontend run lint` und `npm --prefix frontend run test:coverage` |
| CLI (`cli/`) | `node --test cli/tbx.test.mjs` |
| Skripte (`scripts/`) | `node --test scripts/*.test.mjs` |

### Voraussetzungen

- **Java 25** und **Maven**
- **Node** in der Version, mit der das Frontend gebaut wird — heute `v22.11.0`, verbindlich
  steht sie als `node.version` in der `pom.xml`
- eine **laufende Docker-Umgebung**: Die Integrationstests starten ihre Datenbank über
  Testcontainers

Unter **Colima** genügt ein laufendes Docker nicht — Testcontainers findet die Laufzeit dort
nicht von allein und braucht zusätzliche Umgebungsvariablen. Welche das sind, steht in
[docs/betrieb.md](docs/betrieb.md), Abschnitt „Testsuite lokal starten“. Bewusst nur dort:
Zwei Orte für dieselben Werte driften auseinander.

> **Hinweis, keine Anforderung an dich:** Das Projekt prüft die Testgüte zusätzlich mit einer
> Mutationsprüfung. Die läuft beim Maintainer vor jedem Veröffentlichen — du musst sie nicht
> selbst fahren. Überlebt ein Mutant in deinem Code, kann daraus die Bitte um einen weiteren
> Test werden.

## 4. `Signed-off-by` ist willkommen

Häng deinen Commits gern einen `Signed-off-by`-Trailer an (`git commit -s`). Damit hältst du
fest, dass du das Recht hast, deinen Beitrag unter der Projektlizenz beizusteuern
([MIT](LICENSE)) — das ist die Developer Certificate of Origin, in einer Zeile.

Pflicht ist er nicht, und es gibt keine Maschine, die ihn einfordert. Auch hier offen gesagt:
Das Projekt setzt ihn bisher selbst nicht durchgehend. Einen CLA gibt es nicht.

## Zum Fokus

Ich behalte mir vor, Pull Requests abzulehnen, die den Projektfokus verwässern.

Das ist keine Drohung, sondern der Grund für Punkt 1: kanban-kit soll eine schlanke,
self-hostbare Kanban-Alternative bleiben. Wenn du früh fragst, sagen wir dir früh, ob deine
Idee dazugehört — und du steckst deine Zeit nicht in etwas, das wir am Ende nicht aufnehmen.
