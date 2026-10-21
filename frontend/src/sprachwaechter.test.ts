import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { deadAllowlistEntries, scanSource, unmatchedFindings, type SprachAllowance } from './lib/sprachwaechter'

/**
 * Bestandswaechter gegen fest eingebaute Texte (Plan #1638, A9). Die Heuristik selbst und ihre
 * Kanten stehen in `lib/sprachwaechter.ts` samt eigener Testdatei — hier wird nur der echte
 * Quelltext dagegen gehalten.
 *
 * Die Ausnahmeliste ist ein **Ratchet** wie bei `designGuard.test.ts`: Sie duldet die Funde einer
 * Datei unter einer Regel, bis das benannte Bereichspaket sie aufloest. Ein Eintrag ohne Fund macht
 * den Test ebenso rot wie ein Fund ohne Eintrag — so kann ein Paket seine Datei nicht umstellen,
 * ohne sie auszutragen. Was am Ende bleibt, ist ein Literal ohne Anzeigetext und traegt
 * `kein Anzeigetext`.
 *
 * Startbestand aus #1645: alle Funde je Datei und Regel, geordnet nach den Bereichspaketen des
 * Plans (E10). `CardFields.tsx` nannte kein Bereichspaket; das Schlusspaket #1661 hat es umgestellt.
 */
const KEIN_ANZEIGETEXT = 'kein Anzeigetext'

const ALLOWLIST: ReadonlyArray<SprachAllowance> = [
  // #1649
  { file: 'src/auth/AuthContext.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/AuthCard.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1650 — CSS-Werte, `rel` des Doku-Links und das Versionspraefix `v`
  { file: 'src/components/AppShell.tsx', rule: 'jsx', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/AppShell.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1651 — CSS-Werte der Liste und die Plattform-Rollen USER/ADMIN (Schluessel, in beiden Sprachen gleich)
  { file: 'src/pages/BoardListPage.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/pages/RolesPage.tsx', rule: 'jsx', resolvedIn: KEIN_ANZEIGETEXT },
  // #1652 — CSS-Werte (Rahmen, Farbmischungen, Verläufe, Leuchtschatten) der Board-Ansicht
  { file: 'src/components/BoardView.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/board/BoardKarte.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/board/BoardSpalte.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/board/SpaltenKopf.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1653 — CSS-Werte (Haarlinien, Code-Selektor, Rasterflächen) des Karten-Details
  { file: 'src/components/CardDetailModal.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1654 — CSS-Werte (Rahmen, Fokusrand) und der Urteilswert `nicht okay` eines Vermerks (Schluessel, angezeigt wird das Woerterbuch)
  { file: 'src/components/AbnahmeSzenarienDialog.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/CardNumberSearch.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/DerivationTree.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/EpicBadge.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/lib/abnahmeSzenarien.ts', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1655 — CSS-Werte (Haarlinien) der Vorhaben-Liste
  { file: 'src/components/EpicVisibilityList.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1656 — CSS-Werte (Rahmen, Raster, Farbmischungen, Verläufe, Übergänge, SVG-Pfad) des Leitstands
  { file: 'src/components/leitstand/AktuellerStand.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/leitstand/LeitstandBausteine.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/leitstand/LeitstandVerbrauch.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/pages/LeitstandPage.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1657 — CSS-Werte (Haarlinien, Farbmischung, Übergang) des Plattform-Leitstands
  { file: 'src/pages/PlattformLeitstandPage.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1658 — CSS-Werte (Rahmen, Fokusrand, Raster, Übergänge, Schraffur) der Nachtlauf-Bausteine
  { file: 'src/components/nachtlauf/HeuteNachtUebersicht.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/KettenStufenleiste.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufAnteilsbalken.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufBefund.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufFortschritt.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufFuss.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufKartenchips.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufLaufInstrumente.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufLaufPlatte.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufStufenband.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/nachtlauf/NachtlaufVorgangszeile.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1729 — CSS-Wert (Rahmen) des Bereichs „Runner jetzt“
  { file: 'src/components/nachtlauf/RunnerJetzt.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1658 — die Auszüge des Ergebnisstands gehen mit dem Lauf an den Server und werden dort
  // wiedererkannt: Daten wie die Protokollauszüge (Kriterium 4 der Quelle #1637), kein Anzeigetext
  { file: 'src/lib/nightRunErgebnisstand.ts', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1659 — CSS-Werte (Haarlinien) und der Zustandswert `nicht abrufbar` eines Fortschritts (Schluessel, angezeigt wird das Woerterbuch)
  { file: 'src/pages/NightRunPage.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1660 — CSS-Wert (Rahmen der Token-Zeile) und `rel` des Doku-Links der Sicherung
  { file: 'src/components/BackupStatusSection.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/pages/AdministrationPage.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1661 — Entwicklerfehler, die keine Oberflaeche anzeigt: ungueltiger Hexwert im Theme, fehlendes `#root`
  { file: 'src/lib/kontrast.ts', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/main.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // Literale ohne Anzeigetext (CSS-Werte, Schriftstapel)
  { file: 'src/components/boardSurfaceSx.ts', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/dialogChromeSx.ts', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/nachtlaufDesign.ts', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/theme.ts', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
]

/**
 * Die Dateiliste kommt aus `import.meta.glob`, ohne ein Modul zu laden; gelesen wird ueber
 * `node:fs` relativ zu dieser Datei. Ein zweiter `?raw`-Glob ueber alle Quellen neben dem in
 * `designGuard.test.ts` verfaelscht die v8-Abdeckung: Die eingeloesten Rohmodule landen als
 * nie gerufene Export-Getter in `designGuard.ts` und `i18n/woerterbuch.ts` (gemessen in #1645).
 */
const SOURCES: Record<string, string> = Object.fromEntries(
  Object.keys(import.meta.glob('./**/*.{ts,tsx}')).map((path) => [
    path,
    readFileSync(new URL(path, import.meta.url), 'utf8'),
  ]),
)

const funde = Object.entries(SOURCES).flatMap(([path, source]) => scanSource(path.replace('./', 'src/'), source))

describe('Sprachwaechter im Bestand', () => {
  it('sammelt ueberhaupt Quelltext ein', () => {
    // Ohne diese Zusicherung waeren die beiden folgenden Tests auch bei leerem Glob gruen.
    expect(Object.keys(SOURCES).length).toBeGreaterThan(50)
  })

  it('haelt den Bestand frei von Funden ausserhalb der Ausnahmeliste', () => {
    expect(unmatchedFindings(funde, ALLOWLIST)).toEqual([])
  })

  it('fuehrt keine Ausnahme, die kein Fund mehr deckt', () => {
    expect(deadAllowlistEntries(funde, ALLOWLIST)).toEqual([])
  })

  it('duldet nach den Bereichspaketen nur noch Literale ohne Anzeigetext (A9)', () => {
    expect(ALLOWLIST.filter((eintrag) => eintrag.resolvedIn !== KEIN_ANZEIGETEXT)).toEqual([])
  })
})
