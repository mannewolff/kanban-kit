# Releasing

kanban-kit trägt eine dreiteilige Betriebsversion **X.Y.Z**, gepflegt in [`VERSION`](VERSION)
(Quelle der Wahrheit) und von dort in `pom.xml` sowie `frontend/package.json`/
`package-lock.json` synchronisiert. Jede Erhöhung setzt die niedrigeren Teile auf `0` zurück
(Z zählt „Pushes seit dem letzten Production-Release", Y „Production-Releases seit dem
letzten Major").

Dieses Dokument wird von den Skills `push-main` und `merge-production` gelesen — sie führen
die unten genannten Schritte automatisch als Teil des jeweiligen Triggers aus. Dasselbe gilt für
`hotfix production` und `rollback production` des Kits: Es setzt `<version>` ein und führt den
gleichnamigen Abschnitt aus.

## push main

Bei jedem `push main`: Patch-Teil erhöhen (Z+1).

```
node scripts/bump-version.mjs patch
```

**Zusätzlich (automatisch, kein manueller Schritt hier):** Jeder Push auf `main` löst
[.github/workflows/sonarqube.yml](.github/workflows/sonarqube.yml) aus — Backend-/Frontend-Tests
inkl. Coverage, SonarQube-Cloud-Scan, danach automatischer Sync neuer Findings als Karten ins
Backlog des Sonar-Boards (kanbancompat-Ingest mit `externalKey`-Idempotenz, siehe
[scripts/sync-sonar-issues-to-board.mjs](scripts/sync-sonar-issues-to-board.mjs),
Issue #534–#536; ursprünglich GitHub-Issues, #111/#112). Nicht mehr an den `production`-Merge
gebunden: SonarCloud (Free-Tier) kennt
ohnehin nur den `main`-Branch, ein zusätzlicher Scan bei `merge production` wäre nur eine
redundante Zweitanalyse desselben Commits (main -> production per PR-Merge, siehe unten).

## merge production

Bei jedem `merge production`: Minor-Teil erhöhen (Y+1, Z→0), Changelog schreiben und den
Release taggen. Schrittfolge:

```
node scripts/bump-version.mjs minor # VERSION/pom/package + eigene Image-Tags auf die neue Version
node scripts/gen-changelog.mjs      # Changelog-Block der NEUEN Version oben in CHANGELOG.md
# Release-Commit (VERSION, pom.xml, package(-lock).json, CHANGELOG.md,
#                 docker-compose.yml, docker-compose.backup.yml, scripts/bausteine.json)
node scripts/bump-version.mjs tag   # annotated Tag vX.Y.Z auf den Release-Commit setzen
git push origin main --follow-tags  # main + Tag pushen (annotated Tag wird mitgenommen)
# der Tag-Push startet release-images.yml (siehe unten) — kein manueller Schritt
# PR main -> production erstellen (Mannes Merge ist der Stop-Punkt)
# nach dem Merge: GitHub Release zum Tag vX.Y.Z anlegen (Changelog-Block als Beschreibung)
```

**`bump-version.mjs minor` zieht seit Issue #1267 die eigenen Image-Tags selbst nach** — die
ghcr-Zeile in `docker-compose.yml` und `docker-compose.backup.yml` sowie die beiden ghcr-Einträge
in [`scripts/bausteine.json`](scripts/bausteine.json). Kein Handgriff mehr, und darum gehören diese
drei Dateien in den Release-Commit. `patch` lässt sie bewusst unberührt: Ein `push main`
veröffentlicht kein Abbild, eine Betriebsdatei mit `2.14.3` zeigte auf etwas, das es nie gab.

### Der Tag-Push veröffentlicht die Abbilder

Der Push des Tags löst [.github/workflows/release-images.yml](.github/workflows/release-images.yml)
aus. Der Lauf baut das Anwendungs- und das Sicherungs-Abbild für `linux/amd64` und `linux/arm64`,
legt sie als `ghcr.io/mannewolff/kanban-kit:X.Y.Z` bzw. `…-backup:X.Y.Z` und jeweils zusätzlich als
`latest` ab und weist danach mit `node scripts/bezugspruefung.mjs --eigen` nach, dass beide anonym
— also ohne Registry-Anmeldung — beziehbar sind. Ein roter Nachweisschritt heißt nicht, dass der
Bau fehlschlug, sondern dass ein Abbild nicht abrufbar ist.

**Einmalig, beim ersten Release mit Abbildern:** Die Sichtbarkeit **beider** ghcr-Pakete in den
Package-Einstellungen auf „Public" stellen (GitHub → Profil → Packages → `kanban-kit` bzw.
`kanban-kit-backup` → Package settings → Change visibility). Ein neu erzeugtes ghcr-Paket ist
zunächst **privat**; der Workflow kann das nicht selbst ändern. Ohne diesen Handgriff scheitert der
Nachweisschritt — und die Zusage „ohne Registry-Anmeldung abrufbar" wäre still verletzt.

Reihenfolge beachten: **erst** der Version-Bump, **dann** `gen-changelog.mjs`, **dann** der
Release-Commit, **erst danach** `bump-version.mjs tag` — das Skript liest die Zielversion aus
`VERSION` für den Blocktitel; liefe `gen-changelog.mjs` vor dem Bump, entstünde ein Block für die
alte Version. Der Tag muss nach dem Release-Commit gesetzt werden, sonst zeigt er auf den Commit
davor statt auf den eigentlichen Release-Stand.

`gen-changelog.mjs` grenzt den Range über den Tag der Vorversion ab (roher Dump der Commit-Titel,
Keep-a-Changelog-Format). `bump-version.mjs tag` setzt den annotated Tag `vX.Y.Z` (annotated statt
lightweight, damit `git push --follow-tags` ihn mitnimmt — kein separater Tag-Push nötig), der beim
nächsten Release wiederum die Range-Untergrenze bildet. Ein `push main` (Patch-Bump) erzeugt
bewusst weder Changelog-Block noch Tag.

## hotfix production

Für einen Fehler in Produktion, der nicht bis zum nächsten `merge production` warten kann. Der
Hotfix sitzt auf dem Stand, der in Produktion läuft (Zweig `hotfix/…` von `production`), und trägt
die Version `<version>` der Form **X.Y.Z-hotfix.N** — X.Y.Z ist die laufende Version, N zählt die
Hotfixes auf ihr ab 1. Schrittfolge:

```
node scripts/bump-version.mjs hotfix <version>  # VERSION/pom/package + eigene Image-Tags auf <version>
node scripts/gen-changelog.mjs                  # Block [<version>] mit dem Vermerk „verkürzt geprüft“
```

Danach committet das Kit `chore: v<version>` (dieselben Dateien wie der Release-Commit von
`merge production`), setzt mit `node scripts/bump-version.mjs tag` den Tag `v<version>` und stellt
den Pull Request `hotfix/…` → `production`. Der Tag-Push startet
[release-images.yml](.github/workflows/release-images.yml) (`tags: v*`), der Merge auf `production`
startet [deploy.yml](.github/workflows/deploy.yml) — beides wie bei einem Release.

`bump-version.mjs hotfix` nimmt nur die Form X.Y.Z-hotfix.N an und lehnt jeden anderen Zusatz ab
(`-rc.1`, `-hotfix`, `-hotfix.0`). `gen-changelog.mjs` grenzt einen Hotfix gegen den vorigen
Hotfix derselben Version bzw. gegen `vX.Y.Z` ab; ein reguläres Release übergeht die Hotfix-Tags.
Ein späteres `patch`/`minor`/`major` legt den Zusatz ab.

**Verkürzt geprüft.** Der Pull Request aus einem Zweig `hotfix/…` prüft in den Jobs „Backend (mvn
verify)“ und „Backend (PIT Mutation, …)“ nicht voll, sondern mit
`node .claude/kit/checks.mjs run --stage push --hotfix` — unter denselben Job-Namen, weil das
Ruleset von `production` genau diese Namen verlangt (siehe [docs/betrieb.md](docs/betrieb.md)).
Der Vermerk im Changelog hält fest, dass dieser Stand nicht die volle Prüfung gesehen hat; der Fix
gehört danach regulär nach `main`.

## rollback production

Zurück zu einer markierten Vorversion `<version>`, wenn ein Release in Produktion nicht trägt.
Gestartet wird das veröffentlichte Abbild `ghcr.io/mannewolff/kanban-kit:<version>` (und
`…-backup:<version>`) mit den Compose-Dateien aus dem Tag `v<version>` — ohne die Bau-Overlays,
also ohne Bauen, in Minuten. Der Aufruf läuft auf dem eigenen Rechner und erreicht den Server per
SSH; `MANBAN_SERVER` nennt das SSH-Ziel (etwa `root@kanban.mwolff.org` oder ein Host-Alias aus
`~/.ssh/config`). Exit 0 heißt: Die Vorversion läuft und antwortet über die öffentliche Adresse.

```bash
set -eu
: "${MANBAN_SERVER:?MANBAN_SERVER (SSH-Ziel des Produktionsservers) ist nicht gesetzt}"
ssh "$MANBAN_SERVER" sh -eu <<'SERVER'
cd /root/opt/kanban-kit
STACK="-f docker-compose.yml -f docker-compose.prod.yml -f docker-compose.backup.yml"
git fetch --tags origin
git -c advice.detachedHead=false checkout --detach "v<version>"
docker compose $STACK pull manban-api manban-backup
docker compose $STACK up -d
scripts/warte-auf-bereitschaft.sh https://kanban.mwolff.org/login 180
LAEUFT=$(docker inspect --format '{{.Config.Image}}' "$(docker compose $STACK ps -q manban-api)")
test "$LAEUFT" = "ghcr.io/mannewolff/kanban-kit:<version>" || { echo "manban-api läuft mit $LAEUFT statt <version>" >&2; exit 1; }
echo "Produktion läuft mit <version>."
SERVER
```

**Zurück auf den aktuellen Stand.** Derselbe Abschnitt mit der laufenden Version als `<version>`
startet wieder deren veröffentlichtes Abbild. Spätestens der nächste Merge auf `production` stellt
den Server ohnehin zurück: [deploy.yml](.github/workflows/deploy.yml) setzt das Verzeichnis mit
`git reset --hard origin/production` zurück und baut wie gewohnt vor Ort.

**Datenbank.** Die Vorversion startet auf den Daten der neueren — Flyway-Migrationen werden nicht
zurückgenommen. Flyway übergeht dabei angewandte Migrationen, die die ältere Fassung nicht kennt
(Vorgabe `ignore-migration-patterns: "*:future"`), die Anwendung fährt also hoch. Hat die neuere
Version eine bestehende Spalte oder Tabelle umbenannt oder entfernt, scheitert die ältere zur
Laufzeit an genau dieser Stelle; die Bereitschaftsprüfung oben belegt nur den Start. Ein Release
mit solcher Migration ist darum nicht ohne Weiteres zurückrollbar — das gehört in seine
Release-Notiz.

## Major-Version erhöhen

**Nur auf Mannes explizite Anordnung** — er tippt im Chat genau die Phrase
„Major-Version erhöhen". Kein automatischer Trigger, nicht Teil von `push main` oder
`merge production`.

```
node scripts/bump-version.mjs major
```

Erhöht den Major-Teil (X+1, Y→0, Z→0).
