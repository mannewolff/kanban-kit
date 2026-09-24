import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import type { DisruptionView, LaufPaketeView, PaketView } from '../../api/plattformLeitstand'
import { AktuellerStand } from './AktuellerStand'

/**
 * Der Kopf eines laufenden Runs in „Aktueller Status" (Issue #1193).
 *
 * Seit die zweite Platte über dieselbe Liste `laufende` entfallen ist, steht alles, was sie als
 * einzige trug, hier: der **Verweis** auf den Lauf, das **Zustandswort** und die **Startzeit**.
 * Geprüft wird deshalb genau
 * das — dass der Kopf diese drei Angaben führt, auch an einem Lauf, der noch nichts gemeldet hat.
 *
 * <p>Das Wort ist keine Zierde: Nach Kriterium 3 der Quelle #1064 darf der Zustand weder allein an
 * einer Farbe noch allein an der Bewegung hängen. Wer `prefers-reduced-motion` gesetzt hat — das
 * Theme hält den Puls dann an — und Farben nicht unterscheidet, läse sonst nirgends, dass dieser
 * Run arbeitet.
 *
 * <p>Die Sektion als ganze ist über `PlattformLeitstandPage.test.tsx` geprüft; hier steht nur der
 * Kopf, weil seine Angaben an der Komponente hängen und nicht an der Seite.
 */
describe('AktuellerStand — Kopf eines laufenden Runs (#1193)', () => {
  const laufend = (extra: Partial<DisruptionView> = {}): DisruptionView => ({
    nightRunId: 8,
    projectId: 9,
    projectName: 'Mein Projekt',
    mode: 'CHAIN',
    startedAt: '2026-09-21T01:10:00Z',
    outcome: { abortReason: null, verdict: 'RUNNING', decisiveItem: null, noWorkReason: null },
    ...extra,
  })

  const paket = (extra: Partial<PaketView> = {}): PaketView => ({
    cardNumber: 721,
    title: 'Erstes Paket',
    state: 'GREEN',
    errorClass: null,
    cardExists: true,
    ...extra,
  })

  const pakete = (nightRunId: number, liste: PaketView[]): LaufPaketeView => ({
    nightRunId,
    pakete: liste,
  })

  const zeige = (
    laufende: DisruptionView[],
    gemeldetePakete: LaufPaketeView[],
    onAlsBeendetKennzeichnen: (lauf: DisruptionView) => void = () => {},
  ) =>
    render(
      <AktuellerStand
        laufende={laufende}
        gemeldetePakete={gemeldetePakete}
        verschwunden={new Set()}
        onKarteOeffnen={() => {}}
        onAlsBeendetKennzeichnen={onAlsBeendetKennzeichnen}
      />,
      { wrapper: MemoryRouter },
    )

  /** Der Weg zur Auswertung genau dieses Laufs — gestaltet und benannt wie der Verweis der Zeilen. */
  it('führt die Kennung als Verweis in die Lauf-Ansicht', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    const kopf = screen.getByTestId('stand-kopf-8')
    const verweis = within(kopf).getByRole('link', { name: 'Run #8 von Mein Projekt' })
    expect(verweis).toHaveTextContent('Run #8')
    expect(verweis).toHaveAttribute('href', '/projects/9/nachtlauf?lauf=8')
  })

  /** Kriterium 3: der Zustand als Wort, dazu die Startzeit im Format des Laufbands. */
  it('nennt Zustandswort, Startzeit und Stand', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    expect(screen.getByTestId('stand-kopf-8')).toHaveTextContent(
      'Run #8 · läuft seit 03:10 · 1 gemeldet · 1 Erfolg',
    )
  })

  /**
   * Ein Lauf, der noch nichts gemeldet hat, steht mit „0 gemeldet" da — und trägt Verweis, Wort
   * und Zeit genauso. Genau ihn zeigte vorher die entfallene zweite Platte vollständig — hier
   * stand er nur mit „0 gemeldet" da.
   */
  it('zeigt Verweis, Wort und Zeit auch an einem Lauf ohne gemeldetes Paket', () => {
    zeige([laufend({ nightRunId: 6 })], [pakete(6, [])])

    const kopf = screen.getByTestId('stand-kopf-6')
    expect(within(kopf).getByRole('link', { name: 'Run #6 von Mein Projekt' })).toHaveAttribute(
      'href',
      '/projects/9/nachtlauf?lauf=6',
    )
    expect(kopf).toHaveTextContent('Run #6 · läuft seit 03:10 · 0 gemeldet')
    expect(within(screen.getByTestId('stand-lauf-6')).queryAllByTestId(/^stand-paket-/)).toHaveLength(
      0,
    )
  })

  /**
   * Der Name der Paketliste ist der Kopf — er enthält damit Kennung, Zustand, Zeit und Stand.
   * Ohne ihn sagte ein Vorlesewerkzeug nur „Liste mit einem Eintrag".
   */
  it('benennt die Paketliste mit Kennung, Zustand, Zeit und Stand', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    expect(screen.getByRole('list')).toHaveAccessibleName(
      /Run #8.*läuft seit 03:10 · 1 gemeldet · 1 Erfolg/,
    )
  })

  /** Der Melder pulsiert weiter am laufenden Run — Farbe und Bewegung kommen aus dem Befund. */
  it('lässt den Melder des laufenden Runs weiter pulsieren', () => {
    zeige([laufend()], [pakete(8, [paket()])])

    const led = within(screen.getByTestId('stand-kopf-8')).getByTestId('led-stahl')
    expect(led).toHaveAttribute('data-puls', 'an')
  })

  /**
   * Issue #1197: Die Schaltfläche, mit der ein Plattform-Admin einen hängenden Run wegräumt.
   *
   * Ihr Name nennt die Run-Nummer: In einer Sektion mit mehreren laufenden Runs wären zwei
   * gleichlautende Tasten für ein Vorlesewerkzeug nicht zu unterscheiden.
   */
  it('gibt jedem laufenden Run eine Taste, die seine Nummer nennt', async () => {
    const gemeldet: number[] = []
    zeige(
      [laufend(), laufend({ nightRunId: 6 })],
      [pakete(8, [paket()]), pakete(6, [])],
      (lauf) => gemeldet.push(lauf.nightRunId),
    )

    await userEvent.click(
      screen.getByRole('button', { name: 'Run #6 als beendet kennzeichnen' }),
    )

    expect(gemeldet).toEqual([6])
    expect(
      screen.getByRole('button', { name: 'Run #8 als beendet kennzeichnen' }),
    ).toBeInTheDocument()
  })
})
