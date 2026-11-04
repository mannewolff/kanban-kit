import { screen, within } from '@testing-library/react'
import { describe, expect, it, test } from 'vitest'
import type { VerbrauchAngaben, VerbrauchVorhaben as Posten } from '../../api/nightRunUsage'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { cssRegel } from '../../test/cssRegel'
import { TEXT_SCHWACH } from '../../theme'
import { VerbrauchVorhaben } from './VerbrauchVorhaben'

/** Die Kosten je Vorhaben im Bereich „Verbrauch" (Issue #2000, #1954 AK 1, 11). */

/** `Intl` setzt vor Einheiten ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (text: string | null | undefined) => text?.replaceAll(' ', ' ') ?? ''

const kosten = (costUsd: number | null): VerbrauchAngaben => ({
  costUsd,
  inputTokens: null,
  outputTokens: null,
  cachedInputTokens: null,
  cachedInputSharePercent: null,
})

const vorhaben = (epicId: number, title: string, costUsd: number | null, archived = false): Posten => ({
  epicId,
  number: 900 + epicId,
  shortcode: null,
  title,
  archived,
  cardCount: 1,
  usage: kosten(costUsd),
})

const ohne = (costUsd: number | null): Posten => ({
  epicId: null,
  number: null,
  shortcode: null,
  title: null,
  archived: false,
  cardCount: 1,
  usage: kosten(costUsd),
})

const zeilen = () => within(screen.getByRole('list')).getAllByRole('listitem').map((z) => lesbar(z.textContent))

describe('VerbrauchVorhaben', () => {
  test('Szenario: Kosten je Vorhaben im Verbrauch', () => {
    renderMitSprache(<VerbrauchVorhaben epics={[vorhaben(1, 'Export', 9), vorhaben(2, 'Suche', 4)]} withoutEpic={ohne(2)} />)

    expect(screen.getByRole('region', { name: 'Kosten je Vorhaben' })).toBeInTheDocument()
    expect(zeilen()).toEqual(['Export9,00 $', 'Suche4,00 $', 'ohne Vorhaben2,00 $'])
  })

  it('zeigt ein archiviertes Vorhaben mit seinem Namen und dem Zusatz „archiviert"', () => {
    renderMitSprache(<VerbrauchVorhaben epics={[vorhaben(1, 'Export', 9, true)]} withoutEpic={null} />)

    expect(zeilen()).toEqual(['Export · archiviert9,00 $'])
  })

  it('laesst den Posten „ohne Vorhaben" weg, wenn nichts gemessen wurde', () => {
    renderMitSprache(<VerbrauchVorhaben epics={[vorhaben(1, 'Export', 9)]} withoutEpic={null} />)

    expect(zeilen()).toEqual(['Export9,00 $'])
    expect(screen.queryByText('ohne Vorhaben')).not.toBeInTheDocument()
  })

  it('zeigt den Posten „ohne Vorhaben" auch ohne ein einziges Vorhaben', () => {
    renderMitSprache(<VerbrauchVorhaben epics={[]} withoutEpic={ohne(2)} />)

    expect(zeilen()).toEqual(['ohne Vorhaben2,00 $'])
  })

  it('setzt nur den Posten „ohne Vorhaben" schwach, ein Vorhaben in der Grundfarbe', () => {
    renderMitSprache(<VerbrauchVorhaben epics={[vorhaben(1, 'Export', 9)]} withoutEpic={ohne(2)} />)

    expect(cssRegel(screen.getByText('Export'))).not.toContain(TEXT_SCHWACH)
    expect(cssRegel(screen.getByText('ohne Vorhaben'))).toContain(`color: ${TEXT_SCHWACH}`)
  })

  it('zeigt fehlende Kosten als Strich, nie als 0', () => {
    renderMitSprache(<VerbrauchVorhaben epics={[vorhaben(1, 'Export', null)]} withoutEpic={null} />)

    expect(zeilen()).toEqual(['Export—'])
  })

  it('zeigt ohne Posten gar nichts', () => {
    const { container } = renderMitSprache(<VerbrauchVorhaben epics={[]} withoutEpic={null} />)

    expect(container).toBeEmptyDOMElement()
  })

  it('steht auf Englisch mit englischen Texten', () => {
    renderMitSprache(<VerbrauchVorhaben epics={[vorhaben(1, 'Export', 9, true)]} withoutEpic={ohne(2)} />, 'en')

    expect(screen.getByRole('region', { name: 'Cost per epic' })).toBeInTheDocument()
    expect(zeilen()).toEqual(['Export · archived$9.00', 'without epic$2.00'])
  })
})
