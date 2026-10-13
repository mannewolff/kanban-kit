import { ThemeProvider } from '@mui/material/styles'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { ReleasePreparationView } from '../../api/nightRuns'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { tagZeit } from '../../lib/leitstand'
import { theme } from '../../theme'
import { MorgenkachelVeroeffentlichung, VEROEFFENTLICHEN_SATZ } from './MorgenkachelVeroeffentlichung'

/**
 * Die Morgenkachel „Veröffentlichung vorbereitet“ (Issue #1458, Plan #1447 E12): Ergebnis mit
 * Symbol und Text (E6), Kennung des Stands, Version als Beschriftung, „gemeldet um“ und die
 * enthaltenen Arbeitspakete; bei Rot die fehlgeschlagene Prüfung und die betroffenen Karten.
 */

const meldung = (felder: Partial<ReleasePreparationView> = {}): ReleasePreparationView => ({
  result: 'GREEN',
  commitHash: 'b2ae30f6',
  version: '1.4.0',
  redCheck: null,
  pending: [],
  receivedAt: '2026-10-06T05:12:00Z',
  cards: [{ number: 1449, title: 'Stufenleiste: Ziel wählen' }],
  redCards: [],
  ...felder,
})

function zeige(m: ReleasePreparationView) {
  render(
    <ThemeProvider theme={theme}>
      <MorgenkachelVeroeffentlichung meldung={m} />
    </ThemeProvider>,
  )
  return screen.getByRole('region', { name: 'Veröffentlichung vorbereitet' })
}

const ergebnis = () => screen.getByTestId('morgenkachel-ergebnis')
const pakete = () => screen.getByRole('list', { name: 'Enthaltene Arbeitspakete' })

describe('MorgenkachelVeroeffentlichung', () => {
  it('zeigt Grün mit Symbol, Text und dem Satz zur Veröffentlichung von Hand, ohne Knopf', () => {
    const kachel = zeige(meldung())

    expect(ergebnis()).toHaveTextContent('grün')
    expect(within(ergebnis()).getByTestId('morgenkachel-symbol-GREEN')).toBeInTheDocument()
    expect(screen.getByTestId('led-gruen')).toBeInTheDocument()
    expect(kachel).toHaveTextContent(VEROEFFENTLICHEN_SATZ)
    expect(within(kachel).queryByRole('button')).not.toBeInTheDocument()
    expect(within(kachel).queryByRole('link')).not.toBeInTheDocument()
  })

  it('zeigt immer Kennung, Version, „gemeldet um“ und die enthaltenen Pakete', () => {
    const kachel = zeige(meldung())

    expect(within(kachel).getByRole('heading', { name: /Veröffentlichung vorbereitet/ })).toBeInTheDocument()
    expect(screen.getByTestId('morgenkachel-version')).toHaveTextContent('1.4.0')
    expect(screen.getByTestId('morgenkachel-stand')).toHaveTextContent('Stand: b2ae30f6')
    expect(screen.getByTestId('morgenkachel-gemeldet')).toHaveTextContent(
      `gemeldet um ${tagZeit('2026-10-06T05:12:00Z')}`,
    )
    expect(within(pakete()).getByText('#1449')).toBeInTheDocument()
    expect(within(pakete()).getByText('Stufenleiste: Ziel wählen')).toBeInTheDocument()
  })

  it('zeigt Grün mit offener Prüfung samt den offenen Einträgen und ohne den Satz', () => {
    const kachel = zeige(meldung({ result: 'GREEN_PENDING', pending: ['mvn verify', 'Mutationsprüfung'] }))

    expect(ergebnis()).toHaveTextContent('grün, Prüfung offen')
    expect(within(ergebnis()).getByTestId('morgenkachel-symbol-GREEN_PENDING')).toBeInTheDocument()
    expect(screen.getByTestId('led-bernst')).toBeInTheDocument()
    const offen = screen.getByRole('list', { name: 'Offene Prüfungen' })
    expect(within(offen).getAllByRole('listitem').map((e) => e.textContent)).toEqual([
      'mvn verify',
      'Mutationsprüfung',
    ])
    expect(kachel).not.toHaveTextContent(VEROEFFENTLICHEN_SATZ)
  })

  it('zeigt Rot mit der fehlgeschlagenen Prüfung und den betroffenen Karten', () => {
    const kachel = zeige(
      meldung({
        result: 'RED',
        redCheck: 'mvn verify',
        redCards: [
          { number: 1450, title: 'Kette starten' },
          { number: 1451, title: null },
        ],
      }),
    )

    expect(ergebnis()).toHaveTextContent('rot')
    expect(within(ergebnis()).getByTestId('morgenkachel-symbol-RED')).toBeInTheDocument()
    expect(screen.getByTestId('led-zinnob')).toBeInTheDocument()
    expect(screen.getByTestId('morgenkachel-rotpruefung')).toHaveTextContent('Fehlgeschlagene Prüfung: mvn verify')
    const betroffen = screen.getByRole('list', { name: 'Betroffene Karten' })
    expect(within(betroffen).getAllByRole('listitem').map((e) => e.textContent)).toEqual([
      '#1450 Kette starten',
      '#1451',
    ])
    expect(kachel).not.toHaveTextContent(VEROEFFENTLICHEN_SATZ)
  })

  it('zeigt Rot ohne gemeldete Prüfung und ohne Karten ohne leere Angaben', () => {
    zeige(meldung({ result: 'RED' }))

    expect(screen.queryByTestId('morgenkachel-rotpruefung')).not.toBeInTheDocument()
    expect(screen.queryByRole('list', { name: 'Betroffene Karten' })).not.toBeInTheDocument()
  })

  it('zeigt „nicht vorbereitet“ mit Symbol und Text', () => {
    const kachel = zeige(meldung({ result: 'NOT_PREPARED', commitHash: null, version: null, cards: [] }))

    expect(ergebnis()).toHaveTextContent('nicht vorbereitet')
    expect(within(ergebnis()).getByTestId('morgenkachel-symbol-NOT_PREPARED')).toBeInTheDocument()
    expect(screen.getByTestId('led-grau')).toBeInTheDocument()
    expect(kachel).toHaveTextContent('In diesem Lauf wurde keine Veröffentlichung vorbereitet.')
    expect(kachel).not.toHaveTextContent(VEROEFFENTLICHEN_SATZ)
    expect(screen.queryByTestId('morgenkachel-version')).not.toBeInTheDocument()
    expect(screen.queryByTestId('morgenkachel-stand')).not.toBeInTheDocument()
    expect(screen.getByTestId('morgenkachel-keine-pakete')).toHaveTextContent('Enthaltene Arbeitspakete: keine')
  })

  it('führt Pakete aus fremden Ketten in gemeldeter Reihenfolge mit', () => {
    zeige(
      meldung({
        cards: [
          { number: 1449, title: 'Stufenleiste: Ziel wählen' },
          { number: 1302, title: 'Export als CSV' },
          { number: 877, title: 'Backup-Anzeige' },
        ],
      }),
    )

    expect(within(pakete()).getAllByRole('listitem').map((e) => e.textContent)).toEqual([
      '#1449 Stufenleiste: Ziel wählen',
      '#1302 Export als CSV',
      '#877 Backup-Anzeige',
    ])
  })

  it('zeigt eine Karte ohne Titel nur mit ihrer Nummer', () => {
    zeige(meldung({ cards: [{ number: 9999, title: null }] }))

    const [eintrag] = within(pakete()).getAllByRole('listitem')
    expect(eintrag.textContent).toBe('#9999')
  })
})

describe('MorgenkachelVeroeffentlichung auf Englisch (Issue #1658)', () => {
  const zeigeEnglisch = (m: ReleasePreparationView) => {
    renderMitSprache(
      <ThemeProvider theme={theme}>
        <MorgenkachelVeroeffentlichung meldung={m} />
      </ThemeProvider>,
      'en',
    )
    return screen.getByRole('region', { name: 'Release prepared' })
  }

  it('zeigt Grün mit Stand, Meldezeit, Satz und Paketen auf Englisch', () => {
    const kachel = zeigeEnglisch(meldung())

    expect(ergebnis()).toHaveTextContent('green')
    expect(screen.getByTestId('morgenkachel-stand')).toHaveTextContent('State: b2ae30f6')
    expect(screen.getByTestId('morgenkachel-gemeldet')).toHaveTextContent(
      `reported at ${tagZeit('2026-10-06T05:12:00Z', 'en')}`,
    )
    expect(within(kachel).getByText('The state can be published by hand.')).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Included work packages' })).toHaveTextContent('#1449')
  })

  it('zeigt Grün mit offener Prüfung und Rot mit Prüfung und Karten auf Englisch', () => {
    zeigeEnglisch(meldung({ result: 'GREEN_PENDING', pending: ['mvn verify'] }))
    expect(ergebnis()).toHaveTextContent('green, check pending')
    expect(screen.getByRole('list', { name: 'Pending checks' })).toHaveTextContent('mvn verify')
  })

  it('zeigt Rot mit fehlgeschlagener Prüfung und betroffenen Karten auf Englisch', () => {
    zeigeEnglisch(meldung({ result: 'RED', redCheck: 'npm test', redCards: [{ number: 7, title: null }], cards: [] }))

    expect(ergebnis()).toHaveTextContent('red')
    expect(screen.getByTestId('morgenkachel-rotpruefung')).toHaveTextContent('Failed check: npm test')
    expect(screen.getByRole('list', { name: 'Affected cards' })).toHaveTextContent('#7')
    expect(screen.getByTestId('morgenkachel-keine-pakete')).toHaveTextContent('Included work packages: none')
  })

  it('sagt auf Englisch, dass nichts vorbereitet wurde', () => {
    zeigeEnglisch(meldung({ result: 'NOT_PREPARED', commitHash: null }))

    expect(ergebnis()).toHaveTextContent('not prepared')
    expect(screen.getByText('No release was prepared in this run.')).toBeInTheDocument()
  })
})
