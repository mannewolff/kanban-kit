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
 * Plans (E10). `CardFields.tsx` nennt kein Bereichspaket; es faellt dem Schlusspaket zu.
 */
const P1650 = 'Sprache: Rahmen und Navigation auf Englisch'
const P1651 = 'Sprache: Projekte, Boards, Mitglieder, Rollen und Profil auf Englisch'
const P1652 = 'Sprache: Board-Ansicht auf Englisch'
const P1653 = 'Sprache: Karten-Detail auf Englisch'
const P1654 = 'Sprache: Karten-Dialoge und Kartensuche auf Englisch'
const P1655 = 'Sprache: Vorhaben auf Englisch'
const P1656 = 'Sprache: Leitstand auf Englisch'
const P1657 = 'Sprache: Plattform-Leitstand auf Englisch'
const P1658 = 'Sprache: Nachtlauf-Bausteine auf Englisch'
const P1659 = 'Sprache: Nachtlauf-Seite auf Englisch'
const P1660 = 'Sprache: Administration, Admin-Bereich und API-Übersicht auf Englisch'
const P1661 = 'Sprache: Ausnahmeliste des Wächters leeren und Abnahme'
const KEIN_ANZEIGETEXT = 'kein Anzeigetext'

const ALLOWLIST: ReadonlyArray<SprachAllowance> = [
  // #1649
  { file: 'src/auth/AuthContext.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  { file: 'src/components/AuthCard.tsx', rule: 'literal', resolvedIn: KEIN_ANZEIGETEXT },
  // #1650
  { file: 'src/components/AppShell.tsx', rule: 'jsx', resolvedIn: P1650 },
  { file: 'src/components/AppShell.tsx', rule: 'literal', resolvedIn: P1650 },
  { file: 'src/components/BoardSwitcher.tsx', rule: 'jsx', resolvedIn: P1650 },
  { file: 'src/components/BoardSwitcher.tsx', rule: 'literal', resolvedIn: P1650 },
  { file: 'src/components/DataTable.tsx', rule: 'jsx', resolvedIn: P1650 },
  { file: 'src/components/DataTable.tsx', rule: 'literal', resolvedIn: P1650 },
  { file: 'src/components/EditModeBanner.tsx', rule: 'jsx', resolvedIn: P1650 },
  { file: 'src/components/EditModeBanner.tsx', rule: 'literal', resolvedIn: P1650 },
  { file: 'src/layout/navItems.ts', rule: 'literal', resolvedIn: P1650 },
  // #1651
  { file: 'src/pages/BoardListPage.tsx', rule: 'jsx', resolvedIn: P1651 },
  { file: 'src/pages/BoardListPage.tsx', rule: 'literal', resolvedIn: P1651 },
  { file: 'src/pages/ProfilePage.tsx', rule: 'jsx', resolvedIn: P1651 },
  { file: 'src/pages/ProfilePage.tsx', rule: 'literal', resolvedIn: P1651 },
  { file: 'src/pages/ProjectBoardsPage.tsx', rule: 'jsx', resolvedIn: P1651 },
  { file: 'src/pages/ProjectBoardsPage.tsx', rule: 'literal', resolvedIn: P1651 },
  { file: 'src/pages/ProjectMembersPage.tsx', rule: 'jsx', resolvedIn: P1651 },
  { file: 'src/pages/ProjectMembersPage.tsx', rule: 'literal', resolvedIn: P1651 },
  { file: 'src/pages/ProjectsPage.tsx', rule: 'jsx', resolvedIn: P1651 },
  { file: 'src/pages/ProjectsPage.tsx', rule: 'literal', resolvedIn: P1651 },
  { file: 'src/pages/RolesPage.tsx', rule: 'jsx', resolvedIn: P1651 },
  { file: 'src/pages/RolesPage.tsx', rule: 'literal', resolvedIn: P1651 },
  // #1652
  { file: 'src/components/BoardView.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/components/BoardView.tsx', rule: 'literal', resolvedIn: P1652 },
  { file: 'src/components/BulkActionBar.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/components/BulkActionBar.tsx', rule: 'literal', resolvedIn: P1652 },
  { file: 'src/components/LabelManagerDialog.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/components/LabelManagerDialog.tsx', rule: 'literal', resolvedIn: P1652 },
  { file: 'src/components/SpecImportDialog.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/components/SpecImportDialog.tsx', rule: 'literal', resolvedIn: P1652 },
  { file: 'src/components/TrashDialog.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/components/TrashDialog.tsx', rule: 'literal', resolvedIn: P1652 },
  { file: 'src/components/board/Belastungsskala.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/components/board/BoardKarte.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/components/board/BoardKarte.tsx', rule: 'literal', resolvedIn: P1652 },
  { file: 'src/components/board/BoardSpalte.tsx', rule: 'literal', resolvedIn: P1652 },
  { file: 'src/components/board/SpaltenKopf.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/components/board/SpaltenKopf.tsx', rule: 'literal', resolvedIn: P1652 },
  { file: 'src/pages/BoardPage.tsx', rule: 'jsx', resolvedIn: P1652 },
  { file: 'src/pages/BoardPage.tsx', rule: 'literal', resolvedIn: P1652 },
  // #1653
  { file: 'src/components/AttachmentPreview.tsx', rule: 'jsx', resolvedIn: P1653 },
  { file: 'src/components/CardDetailModal.tsx', rule: 'jsx', resolvedIn: P1653 },
  { file: 'src/components/CardDetailModal.tsx', rule: 'literal', resolvedIn: P1653 },
  { file: 'src/lib/readTextFile.ts', rule: 'literal', resolvedIn: P1653 },
  // #1654
  { file: 'src/components/AbnahmeSzenarienDialog.tsx', rule: 'jsx', resolvedIn: P1654 },
  { file: 'src/components/AbnahmeSzenarienDialog.tsx', rule: 'literal', resolvedIn: P1654 },
  { file: 'src/components/CardNumberSearch.tsx', rule: 'jsx', resolvedIn: P1654 },
  { file: 'src/components/CardNumberSearch.tsx', rule: 'literal', resolvedIn: P1654 },
  { file: 'src/components/DerivationTree.tsx', rule: 'jsx', resolvedIn: P1654 },
  { file: 'src/components/DerivationTree.tsx', rule: 'literal', resolvedIn: P1654 },
  { file: 'src/components/EpicBadge.tsx', rule: 'jsx', resolvedIn: P1654 },
  { file: 'src/components/EpicBadge.tsx', rule: 'literal', resolvedIn: P1654 },
  { file: 'src/components/NewCardModal.tsx', rule: 'jsx', resolvedIn: P1654 },
  { file: 'src/components/NewCardModal.tsx', rule: 'literal', resolvedIn: P1654 },
  { file: 'src/components/TransferCardDialog.tsx', rule: 'jsx', resolvedIn: P1654 },
  { file: 'src/components/TransferCardDialog.tsx', rule: 'literal', resolvedIn: P1654 },
  { file: 'src/lib/abnahmeSzenarien.ts', rule: 'literal', resolvedIn: P1654 },
  // #1655
  { file: 'src/components/EpicVisibilityList.tsx', rule: 'jsx', resolvedIn: P1655 },
  { file: 'src/components/EpicVisibilityList.tsx', rule: 'literal', resolvedIn: P1655 },
  { file: 'src/pages/EpicsPage.tsx', rule: 'jsx', resolvedIn: P1655 },
  { file: 'src/pages/EpicsPage.tsx', rule: 'literal', resolvedIn: P1655 },
  // #1656
  { file: 'src/components/leitstand/AktuellerStand.tsx', rule: 'jsx', resolvedIn: P1656 },
  { file: 'src/components/leitstand/AktuellerStand.tsx', rule: 'literal', resolvedIn: P1656 },
  { file: 'src/components/leitstand/FestgefahrenAngaben.tsx', rule: 'jsx', resolvedIn: P1656 },
  { file: 'src/components/leitstand/FestgefahrenAngaben.tsx', rule: 'literal', resolvedIn: P1656 },
  { file: 'src/components/leitstand/LeitstandBausteine.tsx', rule: 'literal', resolvedIn: P1656 },
  { file: 'src/components/leitstand/LeitstandVerbrauch.tsx', rule: 'jsx', resolvedIn: P1656 },
  { file: 'src/components/leitstand/LeitstandVerbrauch.tsx', rule: 'literal', resolvedIn: P1656 },
  { file: 'src/lib/laufFortschritt.ts', rule: 'literal', resolvedIn: P1656 },
  { file: 'src/lib/leitstand.ts', rule: 'literal', resolvedIn: P1656 },
  { file: 'src/lib/verbrauchZeitraum.ts', rule: 'literal', resolvedIn: P1656 },
  { file: 'src/pages/LeitstandPage.tsx', rule: 'jsx', resolvedIn: P1656 },
  { file: 'src/pages/LeitstandPage.tsx', rule: 'literal', resolvedIn: P1656 },
  // #1657
  { file: 'src/pages/PlattformLeitstandPage.tsx', rule: 'jsx', resolvedIn: P1657 },
  { file: 'src/pages/PlattformLeitstandPage.tsx', rule: 'literal', resolvedIn: P1657 },
  // #1658
  { file: 'src/components/nachtlauf/HeuteNachtUebersicht.tsx', rule: 'jsx', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/HeuteNachtUebersicht.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/KartenAnlaeufe.tsx', rule: 'jsx', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/KartenAnlaeufe.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/KettenStufenleiste.tsx', rule: 'jsx', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/KettenStufenleiste.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/MorgenkachelVeroeffentlichung.tsx', rule: 'jsx', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/MorgenkachelVeroeffentlichung.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufAnteilsbalken.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufBefund.tsx', rule: 'jsx', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufBefund.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufFortschritt.tsx', rule: 'jsx', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufFortschritt.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufFuss.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufKartenchips.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufLaufInstrumente.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufLaufPlatte.tsx', rule: 'jsx', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufLaufPlatte.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufStufenband.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufVorgangszeile.tsx', rule: 'jsx', resolvedIn: P1658 },
  { file: 'src/components/nachtlauf/NachtlaufVorgangszeile.tsx', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/lib/nachtlaufFormat.ts', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/lib/nightRunErgebnisstand.ts', rule: 'literal', resolvedIn: P1658 },
  { file: 'src/lib/nightRunHandoff.ts', rule: 'literal', resolvedIn: P1658 },
  // #1659
  { file: 'src/pages/NightRunPage.tsx', rule: 'jsx', resolvedIn: P1659 },
  { file: 'src/pages/NightRunPage.tsx', rule: 'literal', resolvedIn: P1659 },
  // #1660
  { file: 'src/components/AusprobierBestaetigung.tsx', rule: 'jsx', resolvedIn: P1660 },
  { file: 'src/components/BackupStatusSection.tsx', rule: 'jsx', resolvedIn: P1660 },
  { file: 'src/components/BackupStatusSection.tsx', rule: 'literal', resolvedIn: P1660 },
  { file: 'src/components/OverloadRejectionsSection.tsx', rule: 'jsx', resolvedIn: P1660 },
  { file: 'src/components/OverloadRejectionsSection.tsx', rule: 'literal', resolvedIn: P1660 },
  { file: 'src/components/StandardLabelsSection.tsx', rule: 'jsx', resolvedIn: P1660 },
  { file: 'src/components/StandardLabelsSection.tsx', rule: 'literal', resolvedIn: P1660 },
  { file: 'src/lib/apiAusprobieren.ts', rule: 'literal', resolvedIn: P1660 },
  { file: 'src/pages/AdminPage.tsx', rule: 'jsx', resolvedIn: P1660 },
  { file: 'src/pages/AdminPage.tsx', rule: 'literal', resolvedIn: P1660 },
  { file: 'src/pages/AdministrationPage.tsx', rule: 'jsx', resolvedIn: P1660 },
  { file: 'src/pages/AdministrationPage.tsx', rule: 'literal', resolvedIn: P1660 },
  { file: 'src/pages/ApiUebersichtPage.tsx', rule: 'jsx', resolvedIn: P1660 },
  { file: 'src/pages/ApiUebersichtPage.tsx', rule: 'literal', resolvedIn: P1660 },
  // #1661
  { file: 'src/components/CardFields.tsx', rule: 'jsx', resolvedIn: P1661 },
  { file: 'src/components/CardFields.tsx', rule: 'literal', resolvedIn: P1661 },
  { file: 'src/lib/kontrast.ts', rule: 'literal', resolvedIn: P1661 },
  { file: 'src/main.tsx', rule: 'literal', resolvedIn: P1661 },
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
})
