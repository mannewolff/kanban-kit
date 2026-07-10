# docs-site — VitePress-Setup

Build-Setup für die Benutzer- und Betriebsdokumentation (analog zu docs.mwolff.org).
Die eigentlichen Inhalte liegen im Repo unter [`../docs`](../docs) (per `srcDir` eingebunden),
getrennt vom Build-Setup.

## Lokale Vorschau

```
cd docs-site
npm install
npm run dev        # http://localhost:5173
```

## Statischer Build

```
npm run build      # Ausgabe nach .vitepress/dist
npm run preview
```

> **Bekannter Haken (Upstream):** `npm run build` scheitert aktuell mit
> `Rollup failed to resolve import "vue/server-renderer"` — eine Versions-Regression
> von vitepress 1.6.x + vue 3.5.39, die dieselbe Toolchain (u. a. docs.mwolff.org)
> im frischen `npm install` ebenfalls trifft, **nicht** ein Problem der Doku-Inhalte.
> Der **Dev-Server (`npm run dev`) läuft normal**, und die Markdown-Dateien rendern
> auch direkt auf GitHub. Der statische Build wird nachgezogen, sobald die
> vitepress/vue-Versionen zentral auf eine funktionierende Kombination gepinnt sind.
