// Kopiert die Markdown-Quellen aus ../docs in einen lokalen content/-Ordner INNERHALB des
// VitePress-Projektroots (docs-site/). Hintergrund: Ein srcDir außerhalb des Projektroots bricht
// den statischen `vitepress build` — Rollup kann dann beim SSR-Bundle `vue/server-renderer` nicht
// mehr auflösen (VitePress-Issue #2713). Der Dev-Server (`vitepress dev`) ist davon nicht betroffen
// und liest weiterhin direkt aus ../docs (siehe srcDir-Default in .vitepress/config.ts).
import { cpSync, rmSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

const src = fileURLToPath(new URL('../docs', import.meta.url))
const dest = fileURLToPath(new URL('./content', import.meta.url))

rmSync(dest, { recursive: true, force: true })
cpSync(src, dest, { recursive: true })
console.log(`Doku-Quellen kopiert: ${src} -> ${dest}`)
