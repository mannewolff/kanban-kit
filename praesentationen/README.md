# Präsentationen

Externe Unterlagen zu kanban-kit (Factsheet, Präsentationsmaterial). Kein Produktivcode, nicht Teil
des Builds.

## Factsheet

| Datei | Inhalt |
|---|---|
| `kanban-kit-factsheet.html` | Quelle, aus der das PDF erzeugt wird |
| `kanban-kit-factsheet.pdf` | Aktueller Stand: August 2026, Version 1.26.1 |
| `kanban-kit-factsheet.png` | Bild-Export, 1653 × 2339 (200 dpi) |
| `kanban-kit-factsheet-2026-07-v1.13.0.pdf/.png` | Vorheriger Stand Juli 2026 |

### Neu erzeugen

```bash
weasyprint präsentationen/kanban-kit-factsheet.html präsentationen/kanban-kit-factsheet.pdf
qlmanage -t -s 2339 -o präsentationen präsentationen/kanban-kit-factsheet.pdf
```

Das Layout ist auf genau eine A4-Seite ausgelegt. Nach inhaltlichen Änderungen prüfen, ob das noch
gilt:

```bash
weasyprint --uncompressed-pdf präsentationen/kanban-kit-factsheet.html /tmp/check.pdf && grep -c '/Type /Page[^s]' /tmp/check.pdf
```

Erwartet wird `1`. Bei `2` die Abstände in der HTML-Quelle straffen.

### Woher die Zahlen kommen

| Kennzahl | Quelle |
|---|---|
| Zeilen im Repository | `git ls-files`, ohne Bilder und `package-lock.json` |
| Backend-Tests | `@Test`, `@ParameterizedTest`, `@RepeatedTest` unter `src/test/java` |
| Integrationstests | dieselben Annotationen in `*IT.java` |
| Frontend-Tests | `it()` und `test()` in `frontend/src/**/*.test.ts(x)` |
| Coverage Backend | `target/site/jacoco/jacoco.csv` |
| Coverage Frontend | `frontend/coverage/lcov.info` |
| Mutation Score | `target/pit-reports/index.html` |
| SonarCloud | Projektstatus auf SonarCloud, nicht lokal messbar |

Die Report-Dateien entstehen bei `mvn verify` und `npm test`. Vor einem Update des Factsheets beide
Läufe frisch machen, sonst stammen Coverage und Mutation Score aus unterschiedlichen Ständen.
