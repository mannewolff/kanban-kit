import { KlassenMarke } from './LeitstandBausteine'

/**
 * Die Marken einer gleichzeitigen Nacht — „gleichzeitig" und „Übernahme verschoben" — für die
 * zugeklappte Zeile der Lauf-Ansicht und die Platte „Letzter Run" (Issue #1879, Plan #1876 E13).
 * Die Wörter liefert `gleichzeitigkeitsMarken`; ohne Marke steht nichts.
 *
 * <p>Beide in Stahl: Die Angaben sind Auskunft, keine Störung — Zinnober und Bernstein tragen im
 * Leitstand Fehlschlag und Warten. Die Aussage hängt am Wort, nicht an der Farbe (K4).
 */
export function GleichzeitigkeitMarken({ marken }: Readonly<{ marken: readonly string[] }>) {
  return (
    <>
      {marken.map((marke) => (
        <KlassenMarke key={marke} melder="stahl">
          {marke}
        </KlassenMarke>
      ))}
    </>
  )
}
