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

## merge production

Bei jedem `merge production`: Minor-Teil erhöhen (Y+1, Z→0).

```
node scripts/bump-version.mjs minor
```

**Zusätzlich (automatisch, kein manueller Schritt hier):** Sobald der PR tatsächlich nach
`production` gemerged wird, löst der Push auf den `production`-Branch
[.github/workflows/sonarqube.yml](.github/workflows/sonarqube.yml) aus — Backend-/Frontend-Tests
inkl. Coverage, SonarQube-Cloud-Scan, danach automatischer Sync neuer Findings als GitHub Issues
(Label `sonar`, siehe [scripts/sync-sonar-issues-to-github.mjs](scripts/sync-sonar-issues-to-github.mjs),
Issue #111/#112). Das ist an den echten GitHub-Merge gebunden, nicht an das Erstellen des PRs durch
den `merge-production`-Skill.

## Major-Version erhöhen

**Nur auf Mannes explizite Anordnung** — er tippt im Chat genau die Phrase
„Major-Version erhöhen". Kein automatischer Trigger, nicht Teil von `push main` oder
`merge production`.

```
node scripts/bump-version.mjs major
```

Erhöht den Major-Teil (X+1, Y→0, Z→0).
