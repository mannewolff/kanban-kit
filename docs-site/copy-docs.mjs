// Kopiert die Markdown-Quellen aus ../docs in einen lokalen content/-Ordner INNERHALB des
// VitePress-Projektroots (docs-site/). Hintergrund: Ein srcDir außerhalb des Projektroots bricht
// den statischen `vitepress build` — Rollup kann dann beim SSR-Bundle `vue/server-renderer` nicht
// mehr auflösen (VitePress-Issue #2713). Der Dev-Server (`vitepress dev`) ist davon nicht betroffen
// und liest weiterhin direkt aus ../docs (siehe srcDir-Default in .vitepress/config.ts).
import { cpSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const src = fileURLToPath(new URL('../docs', import.meta.url))
const dest = fileURLToPath(new URL('./content', import.meta.url))

// UPGRADING.md liegt bewusst im Wurzelverzeichnis (dort sucht ein Selbsthoster sie, und GitHub
// zeigt sie neben README und CHANGELOG), gehört aber in die gerenderte Doku. Sie wird deshalb
// zusätzlich mitkopiert: Ein Sidebar-Eintrag auf eine Datei außerhalb des VitePress-Projektroots
// wäre ein toter Link, an dem `vitepress build` abbricht — und der läuft im Dockerfile und über das
// frontend-maven-plugin in `mvn verify` (Issue #1268).
const wurzelseite = fileURLToPath(new URL('../UPGRADING.md', import.meta.url))
const wurzelziel = join(dest, 'upgrading.md')

// Relative Verweise, die aus dem kopierten Baum herauszeigen, lösen darin nicht mehr auf. Im Repo
// sind sie richtig (von der Wurzel nach docs/ und zurück), in content/ liegen beide Seiten
// nebeneinander — also werden genau diese beiden Richtungen beim Kopieren umgeschrieben. Ohne das
// bricht der Bau am toten Link, statt ihn nur falsch zu rendern.
const umschreibungen = [
  { von: /\]\(docs\//g, nach: '](' }, // aus der Wurzelseite heraus
  { von: /\]\(\.\.\/UPGRADING\.md/g, nach: '](upgrading.md' }, // aus docs/ zur Wurzelseite
]

function umgeschrieben(text) {
  return umschreibungen.reduce((stand, { von, nach }) => stand.replace(von, nach), text)
}

function schreibeUmgeschrieben(quelle, ziel) {
  writeFileSync(ziel, umgeschrieben(readFileSync(quelle, 'utf8')), 'utf8')
}

function markdownDateien(verzeichnis) {
  return readdirSync(verzeichnis, { withFileTypes: true, recursive: true })
    .filter((eintrag) => eintrag.isFile() && eintrag.name.endsWith('.md'))
    .map((eintrag) => join(eintrag.parentPath ?? eintrag.path, eintrag.name))
}

rmSync(dest, { recursive: true, force: true })
cpSync(src, dest, { recursive: true })
schreibeUmgeschrieben(wurzelseite, wurzelziel)
for (const datei of markdownDateien(dest)) {
  schreibeUmgeschrieben(datei, datei)
}
console.log(`Doku-Quellen kopiert: ${src} + ${wurzelseite} -> ${dest}`)
