# Repository-Grundausstattung: Kanban-Board als Open Source

Stand: 09. Juli 2026

Was ein Repository braucht, damit "Open Source" mehr ist als "Quelltext im Netz".
Ergänzt das Runbook `runbook-showcase-server-strato.md`, das die Serverseite
behandelt.

---

## 01 Die Positionierung, bevor irgendeine Datei entsteht

Als generisches Kanban-Board konkurriere ich mit Vikunja, Planka, Wekan, Kanboard,
Focalboard und Taiga. Alle self-hostable, alle Open Source, alle älter. Diesen
Vergleich gewinne ich nicht, und der erste Kommentar unter dem LinkedIn-Post
lautet "warum nicht einfach Vikunja".

Die Positionierung, die trägt: **Das Board zum claude-workflow-kit.** Kein
generischer Issue-Tracker, sondern einer, dessen Datenmodell die neun Schritte und
die drei Kontrollpunkte kennt. Die Spalten heißen nicht "Todo, Doing, Done",
sondern bilden den Zustand ab, in dem sich ein Issue im Prozess befindet. Ein
Issue vor dem GO-Gate ist ein anderer Zustand als eines zwischen Push und Merge,
und das Board weiß das.

Damit ist es kein Konkurrenzprodukt, sondern Zubehör zu etwas, das ich ohnehin
verbreite. Der Satz im README, der das transportiert, muss in den ersten drei
Zeilen stehen.

**Namensvorschlag:** Nicht "mwolff-kanban". Etwas, das den Prozessbezug trägt.
`workflow-board`, `gate-board`, `kit-board`. Der Name entscheidet, ob Leute es für
ein weiteres Trello halten.

---

## 02 Lizenz

Die Entscheidung fällt einmal und lässt sich später nur mit dem Einverständnis
aller Beitragenden ändern. Ich bin kein Anwalt, das hier sind die Argumente, nicht
die Beratung.

**Apache-2.0** wenn maximale Verbreitung das Ziel ist. Unternehmensfreundlich,
explizite Patentklausel, niemand muss etwas zurückgeben. Wenn das Board Zubehör
zum Workshop ist und der Workshop das Produkt, ist Apache-2.0 die richtige Wahl.
Ein Teilnehmer soll es am Montag in seiner Firma einsetzen dürfen, ohne die
Rechtsabteilung zu fragen.

**AGPL-3.0** wenn ich mir offenhalten will, später doch gehostetes Hosting zu
verkaufen, ohne dass jemand anders dasselbe Produkt als SaaS anbietet, ohne
etwas beizutragen. Der Preis: Manche Unternehmen verbieten AGPL pauschal, auch
für interne Nutzung, weil die Rechtsabteilung die Netzwerk-Klausel nicht
verstehen will. Das kostet mich genau die Leute, die im Workshop sitzen.

**Meine Einschätzung:** Apache-2.0. Das Geschäftsmodell sind Workshops und
Beratung, nicht Software-Lizenzen. Wer das Board forkt und als SaaS verkauft, hat
den schwierigeren Teil noch vor sich und macht nebenbei Werbung für meinen
Prozess.

Datei: `LICENSE` im Wurzelverzeichnis, unverändert aus dem Original-Wortlaut.
Dazu ein `NOTICE`, falls Apache-2.0, mit Copyright-Zeile.

---

## 03 README

Die einzige Datei, die die meisten Besucher lesen. Reihenfolge nach abnehmender
Wahrscheinlichkeit, dass jemand weiterliest:

1. **Ein Satz, was es ist.** Nicht "Ein modernes Kanban-Board", sondern "Ein
   Issue-Tracker, der den neunstufigen Prozess des claude-workflow-kit abbildet,
   inklusive der drei menschlichen Kontrollpunkte."
2. **Screenshot.** Ein einziger, der die prozessbezogenen Spalten zeigt. Kein
   animiertes GIF, das schneller ist als das Lesen.
3. **Link auf `demo.mwolff.org`**, mit dem Hinweis auf den nächtlichen Reset.
4. **Selbst hosten in fünf Zeilen.** Compose-Datei herunterladen, `docker compose
   up -d`, fertig. Wenn das mehr als fünf Zeilen sind, ist das Projekt zu
   kompliziert.
5. **Warum, nicht wie.** Ein Absatz über den Prozess, mit Link auf das
   Whitepaper und auf docs.mwolff.org.
6. **Der Erwartungsabsatz.** Wörtlich etwa: "Ich pflege dieses Projekt für
   meinen eigenen Workflow und für meine Workshops. Pull Requests sind
   willkommen. Eine Support-Zusage, eine Roadmap oder eine Reaktionszeit gibt es
   nicht." Dieser Absatz erspart mir später jede unangenehme Diskussion, weil er
   vorher da war.
7. Lizenz, Kontakt.

Auf Badges verzichten oder auf drei beschränken (Build, Lizenz, letzte Version).
Eine Badge-Wand signalisiert Unsicherheit.

---

## 04 SECURITY.md

Fünf Minuten Arbeit, verhindert, dass jemand eine Lücke als öffentliches Issue
aufmacht.

```markdown
# Security Policy

## Unterstützte Versionen
Nur die jeweils letzte Minor-Version.

## Schwachstelle melden
Bitte nicht als öffentliches Issue. Mail an security@mwolff.org,
gerne PGP-verschlüsselt (Key: ...).

Ich bestätige den Eingang innerhalb von 5 Werktagen. Eine Frist für
einen Fix kann ich nicht zusagen, dies ist ein Nebenprojekt.
```

Der letzte Satz ist wichtig und ehrlich. Eine Zusage, die ich im Urlaub nicht
halten kann, ist schlimmer als keine.

Dazu GitHub Private Vulnerability Reporting aktivieren, das ist ein Schalter in
den Repository-Einstellungen.

---

## 05 CONTRIBUTING.md

Kurz halten. Vier Punkte reichen:

- Vor größeren Änderungen ein Issue aufmachen, damit niemand umsonst arbeitet.
- Commits nach Conventional Commits, weil daraus der Changelog entsteht.
- Tests laufen lassen, bevor der PR aufgeht. Ein Befehl, nicht drei.
- Der DCO-Weg (`Signed-off-by`) statt eines CLA. Ein CLA ist Overkill und
  schreckt ab.

Ein Satz, der selten dasteht und viel wert ist: **"Ich behalte mir vor, Pull
Requests abzulehnen, die den Projektfokus verwässern."** Damit ist die
Ablehnung eines gutgemeinten Feature-PRs später kein Affront, sondern eine
angekündigte Regel.

---

## 06 Was ein Selbsthoster tatsächlich braucht

Der ganze Zweck der Übung. Wenn dieser Teil nicht stimmt, ist das Repository
Dekoration.

### 06.1 Eine Compose-Datei, die funktioniert

```yaml
services:
  app:
    image: ghcr.io/mwolff/workflow-board:1.2.0
    restart: unless-stopped
    environment:
      DATABASE_URL: postgres://board:${DB_PASSWORD}@db:5432/board
      BASE_URL: https://board.example.org
    ports:
      - "127.0.0.1:3000:3000"
    depends_on:
      db:
        condition: service_healthy

  db:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: board
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      POSTGRES_DB: board
    volumes:
      - board-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U board"]
      interval: 5s
      retries: 10

volumes:
  board-data:
```

Drei Dinge, die hier bewusst so sind:

- **Version festgenagelt** (`:1.2.0`), nicht `:latest`. Ein Selbsthoster soll
  entscheiden, wann er aktualisiert.
- **Port auf 127.0.0.1 gebunden.** Der Reverse Proxy des Selbsthosters kümmert
  sich um TLS. Wer `0.0.0.0:3000` schreibt, exponiert die Anwendung an ufw
  vorbei, weil Docker eigene iptables-Regeln schreibt.
- **Benanntes Volume.** Daten nie im Container.

Dazu eine `.env.example` mit allen Variablen, kommentiert, und ein
`docker-compose.override.yml.example` für den Fall, dass jemand hinter einem
eigenen Traefik sitzt.

### 06.2 Migrationen, die von selbst laufen

Beim Start prüft die Anwendung das Schema und migriert. Ein Selbsthoster darf nie
`docker compose exec app ./migrate` tippen müssen, weil er es vergessen wird und
dann ein Issue aufmacht.

### 06.3 Ein Backup-Absatz in der Dokumentation

Genau ein `pg_dump`-Einzeiler und ein `pg_restore`-Einzeiler. Nicht mehr. Wer
mehr will, findet es selbst. Wer weniger findet, hat kein Backup.

### 06.4 Upgrade-Pfad

Ein Abschnitt `UPGRADING.md`, in dem für jede Major-Version steht, was zu tun ist.
Meistens: "Image-Tag ändern, `docker compose up -d`, fertig." Wenn es einmal nicht
so ist, steht es genau dort.

---

## 07 Releases

Ohne das ist Open Source nur Quelltext im Netz.

- **Tags nach SemVer.** `v1.2.0`.
- **Container-Image in der GitHub Container Registry**, getaggt mit der Version
  und zusätzlich mit `latest`. Multi-Arch (amd64 und arm64), damit es auf einem
  Raspberry Pi und auf einem M-Mac läuft. `docker buildx` macht das in einem
  Schritt.
- **Changelog automatisch** aus den Conventional Commits, per
  `release-please` oder `git-cliff`.
- **Release-Notes**, die für Menschen geschrieben sind, nicht für den Parser. Drei
  Zeilen, was neu ist, was kaputtgeht.
- **SBOM und Signatur.** `docker buildx` erzeugt eine SBOM auf Knopfdruck,
  `cosign` signiert das Image. Beides kostet zwei Zeilen in der Pipeline und ist
  im Workshop ein exzellentes Beispiel dafür, was Supply-Chain-Security konkret
  bedeutet.

---

## 08 Die Pipeline, die zugleich Lehrmaterial ist

Auf öffentlichen Repositories sind GitHub Actions, Renovate und Trivy kostenlos.
Das heißt: Meine gesamte Supply-Chain-Kette aus Abschnitt 08 des Runbooks wird
öffentlich einsehbar.

**Bei jedem Pull Request:**

1. Lint, Typecheck, Unit-Tests
2. Build des Images
3. `trivy image --severity HIGH,CRITICAL --exit-code 1` gegen das frische Image
4. `trivy fs` gegen das Repository, für Secrets und Abhängigkeiten

**Bei jedem Merge auf main:**

5. Multi-Arch-Build, SBOM, Signatur, Push in die Registry
6. Optional: Deployment der Demo per Coolify-API

**Kontinuierlich:**

7. Renovate erzeugt Merge-Requests für Base-Images und Abhängigkeiten. Diese
   durchlaufen Schritt 1 bis 4 wie jeder andere PR.

Der Punkt für den Workshop: Ein Teilnehmer kann in meinem Repository einen
konkreten Renovate-MR anschauen, der ein Base-Image von `node:22.14` auf
`node:22.15` hebt, und sehen, wie er durch Trivy geht. Das ist kein Vortrag über
Supply-Chain-Security, das ist der Beleg.

Branch Protection auf `main`: Merge nur mit grünen Checks. Das ist die technische
Umsetzung des dritten Gates aus dem claude-workflow-kit. Der Zusammenhang gehört
in die Dokumentation, weil er die Brücke zwischen Prozess und Werkzeug schlägt.

---

## 09 Die Git-History ist der eigentliche Showcase

Ein laufendes Board beweist, dass etwas läuft. Ein öffentliches Repository
beweist, **wie** es entstanden ist.

Wenn ich das Board konsequent mit dem claude-workflow-kit baue, zeigt die History:
Issue, GO-Kommentar, Commits, Merge-Request, Merge. Jemand, der nach dem Workshop
skeptisch bleibt, kann nachlesen, ob der Prozess wirklich so gelaufen ist oder ob
das nur auf der Folie stand.

Daraus folgt eine unbequeme Disziplin: **Ab dem Tag, an dem das Repository
öffentlich wird, gibt es keine Commits mehr an main vorbei.** Kein "schnell noch
den Typo gefixt". Jede Ausnahme, die in der History steht, ist ein Argument gegen
meinen eigenen Prozess, und im Workshop wird sie jemand finden.

Das ist anstrengend und genau deshalb überzeugend.

---

## 10 Der Launch

Reihenfolge, damit der erste Eindruck stimmt:

1. Repository privat vorbereiten, README fertig, Demo läuft, ein `v0.1.0` getaggt
   und als Image verfügbar. Nichts ist trauriger als ein öffentliches Repository,
   dessen Installationsanleitung nicht funktioniert.
2. Drei Menschen bitten, die Installationsanleitung auf einer frischen Maschine
   durchzuarbeiten. Nicht kommentieren, nur zuschauen. Jede Stelle, an der sie
   stocken, ist ein Bug in der Dokumentation.
3. Repository öffentlich schalten.
4. Blogpost auf blog.mwolff.org: nicht "Ich habe ein Kanban-Board gebaut", sondern
   die Prozessgeschichte. Wie viele Issues, wie viele Gates, wo der Prozess
   getragen hat und wo nicht. Die Stellen, an denen er nicht getragen hat, sind
   der interessanteste Teil und der glaubwürdigste.
5. LinkedIn-Post, Link im ersten Kommentar, nach dem gewohnten Muster.

Was ich nicht mache: Hacker News, Reddit, Product Hunt. Das Board ist kein
Produkt, das Reichweite braucht. Es ist ein Beleg, der bei den richtigen dreißig
Leuten ankommen muss.

---

## Anhang: Dateien im Wurzelverzeichnis

- [ ] `README.md`
- [ ] `LICENSE` (Apache-2.0)
- [ ] `NOTICE`
- [ ] `SECURITY.md`
- [ ] `CONTRIBUTING.md`
- [ ] `CODE_OF_CONDUCT.md` (Contributor Covenant, unverändert)
- [ ] `CHANGELOG.md` (generiert)
- [ ] `UPGRADING.md`
- [ ] `docker-compose.yml`
- [ ] `.env.example`
- [ ] `renovate.json`
- [ ] `.github/workflows/ci.yml`
- [ ] `.github/workflows/release.yml`
- [ ] `.github/ISSUE_TEMPLATE/` (Bug, Feature, jeweils kurz)
