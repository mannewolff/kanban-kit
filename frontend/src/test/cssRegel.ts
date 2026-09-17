/**
 * Die von Emotion erzeugte Grundregel eines Elements als Text, etwa
 * `.css-abc { border-left: 3px solid var(--mb-palette-status-backlog-dot); … }`.
 *
 * **Warum nicht `toHaveStyle`:** Seit #951 tragen Farben CSS-Variablen. jsdom verwirft
 * Kurzschreibweisen wie `border-top: 3px solid var(…)` im berechneten Stil vollständig — Breite
 * und Farbe fehlen dort, obwohl der Browser beides setzt. Die erzeugte Regel steht dagegen
 * unverändert im Stylesheet und belegt, was tatsächlich ausgeliefert wird.
 */
export function cssRegel(element: Element): string {
  const klasse = [...element.classList].find((c) => c.startsWith('css-'))
  const regel = [...document.styleSheets]
    .flatMap((blatt) => [...blatt.cssRules])
    .find((r) => r.cssText.startsWith(`.${klasse} {`))
  if (!regel) {
    throw new Error(`Keine erzeugte CSS-Regel für die Klasse ${String(klasse)} gefunden`)
  }
  return regel.cssText
}

/**
 * Eine zusätzliche Regel derselben Emotion-Klasse, deren Selektor den Zusatz trägt — etwa
 * `.css-abc.Mui-focusVisible { outline: … }` oder `.css-abc:focus { … }`.
 */
export function cssRegelMit(element: Element, zusatz: string): string {
  const klasse = [...element.classList].find((c) => c.startsWith('css-'))
  const regel = [...document.styleSheets]
    .flatMap((blatt) => [...blatt.cssRules])
    .find((r) => r.cssText.startsWith(`.${klasse}${zusatz}`))
  if (!regel) {
    throw new Error(`Keine erzeugte CSS-Regel für .${String(klasse)}${zusatz} gefunden`)
  }
  return regel.cssText
}
