# Releasing

kanban-kit trägt eine dreiteilige Betriebsversion **X.Y.Z**, gepflegt in [`VERSION`](VERSION)
(Quelle der Wahrheit) und von dort in `pom.xml` sowie `frontend/package.json`/
`package-lock.json` synchronisiert. Jede Erhöhung setzt die niedrigeren Teile auf `0` zurück
(Z zählt „Pushes seit dem letzten Production-Release", Y „Production-Releases seit dem
letzten Major").

Dieses Dokument wird von den Skills `push-main` und `merge-production` gelesen — sie führen
die unten genannten Schritte automatisch als Teil des jeweiligen Triggers aus.

## push main

Bei jedem `push main`: Patch-Teil erhöhen (Z+1).

```
node scripts/bump-version.mjs patch
```

**Zusätzlich (automatisch, kein manueller Schritt hier):** Jeder Push auf `main` löst
[.github/workflows/sonarqube.yml](.github/workflows/sonarqube.yml) aus — Backend-/Frontend-Tests
inkl. Coverage, SonarQube-Cloud-Scan, danach automatischer Sync neuer Findings als GitHub Issues
(Label `sonar`, siehe [scripts/sync-sonar-issues-to-github.mjs](scripts/sync-sonar-issues-to-github.mjs),
Issue #111/#112). Nicht mehr an den `production`-Merge gebunden: SonarCloud (Free-Tier) kennt
ohnehin nur den `main`-Branch, ein zusätzlicher Scan bei `merge production` wäre nur eine
redundante Zweitanalyse desselben Commits (main -> production per PR-Merge, siehe unten).

## merge production

Bei jedem `merge production`: Minor-Teil erhöhen (Y+1, Z→0), Changelog schreiben und den
Release taggen. Schrittfolge:

```
node scripts/bump-version.mjs minor # VERSION/pom/package auf die neue Version + Tag vX.Y.Z setzen
node scripts/gen-changelog.mjs      # Changelog-Block der NEUEN Version oben in CHANGELOG.md
# Release-Commit (VERSION, pom.xml, package(-lock).json, CHANGELOG.md)
git push origin main --follow-tags  # main + Tag pushen
# PR main -> production erstellen (Mannes Merge ist der Stop-Punkt)
# nach dem Merge: GitHub Release zum Tag vX.Y.Z anlegen (Changelog-Block als Beschreibung)
```

Reihenfolge beachten: **erst** der Version-Bump, **dann** `gen-changelog.mjs` — das Skript liest
die Zielversion aus `VERSION` für den Blocktitel; liefe es vor dem Bump, entstünde ein Block für
die alte Version.

`gen-changelog.mjs` grenzt den Range über den Tag der Vorversion ab (roher Dump der Commit-Titel,
Keep-a-Changelog-Format). `bump-version.mjs minor` setzt den Tag `vX.Y.Z`, der beim nächsten
Release wiederum die Range-Untergrenze bildet. Ein `push main` (Patch-Bump) erzeugt bewusst
weder Changelog-Block noch Tag.

## Major-Version erhöhen

**Nur auf Mannes explizite Anordnung** — er tippt im Chat genau die Phrase
„Major-Version erhöhen". Kein automatischer Trigger, nicht Teil von `push main` oder
`merge production`.

```
node scripts/bump-version.mjs major
```

Erhöht den Major-Teil (X+1, Y→0, Z→0).
