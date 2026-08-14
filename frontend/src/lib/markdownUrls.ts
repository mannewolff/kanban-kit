// Schema-Whitelist für nutzergesteuerte URLs aus Markdown (CLAUDE-security.md §Input & XSS).
//
// react-markdown escapt eingebettetes HTML von sich aus, lässt in `[text](url)` und `![alt](url)`
// aber `http:` und relative URLs durch — nur `javascript:` blockiert es selbst. Kommentare sind
// Fremdeingaben anderer Nutzer, deshalb wird das Schema hier zusätzlich geprüft: alles, was nicht
// auf der Whitelist steht (auch relative und nicht parsebare URLs), erzeugt gar kein Attribut.

/** Erlaubte Schemata für Links: verschlüsselter Abruf oder E-Mail. */
const LINK_SCHEMES = ['https:', 'mailto:']
/** Erlaubte Schemata für Bilder: nur verschlüsselter Abruf (kein `data:`, kein `mailto:`). */
const IMAGE_SCHEMES = ['https:']

/** Schema der URL oder `null`, wenn sie relativ oder nicht parsebar ist. */
function scheme(url: string): string | null {
  try {
    return new URL(url).protocol
  } catch {
    return null
  }
}

/** Der `href`-Wert, wenn das Schema erlaubt ist — sonst `undefined` (kein aktiver Link). */
export function safeLinkHref(href: string | undefined): string | undefined {
  return href && LINK_SCHEMES.includes(scheme(href) ?? '') ? href : undefined
}

/** Der `src`-Wert, wenn das Schema erlaubt ist — sonst `undefined` (kein Abruf). */
export function safeImageSrc(src: string | undefined): string | undefined {
  return src && IMAGE_SCHEMES.includes(scheme(src) ?? '') ? src : undefined
}
