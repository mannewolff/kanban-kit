import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, test, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { VerbrauchBudget } from '../../api/nightRunUsage'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import type { Sprache } from '../../i18n/woerterbuch'
import { LeitstandBudget } from './LeitstandBudget'

/**
 * Die Budgetzeile im Bereich „Verbrauch" (Issue #1969, Plan #1962, Fachliche Quelle #1957 AK 1, 5,
 * 7, 9 bis 11, 13, 14). Die Zahlen rechnet der Server; geprüft wird, was die Zeile daraus zeigt.
 */

const auth = vi.hoisted(() => ({ vorschau: null as null | Record<string, string> }))
vi.mock('../../auth/AuthContext', () => ({
  useAuth: () => ({ vorschau: auth.vorschau }),
}))

/** `Intl` setzt vor Einheiten ein geschütztes Leerzeichen; verglichen wird der Wortlaut. */
const lesbar = (text: string | null | undefined) => text?.replaceAll(' ', ' ') ?? ''

/** Stand vom 10. Oktober: 40 $ von 100 $, Hochrechnung 124 $, überschritten am 25. (AK 9, 10). */
const stand = (extra: Partial<VerbrauchBudget> = {}): VerbrauchBudget => ({
  budgetUsd: 100,
  editable: true,
  monthFirstDay: '2026-10-01',
  spentUsd: 40,
  percent: 40,
  warning: 'NONE',
  forecastUsd: 124,
  tooEarly: false,
  exceedDate: '2026-10-25',
  coverage: 'COMPLETE',
  ...extra,
})

function zeige(wert: VerbrauchBudget | Error = stand(), sprache: Sprache = 'de') {
  const laden = wert instanceof Error ? vi.fn().mockRejectedValue(wert) : vi.fn().mockResolvedValue(wert)
  const schreiben = {
    setMonthlyBudget: vi.fn().mockResolvedValue(undefined),
    removeMonthlyBudget: vi.fn().mockResolvedValue(undefined),
  }
  renderMitSprache(<LeitstandBudget projectId={5} laden={laden} schreiben={schreiben} />, sprache)
  return { laden, schreiben }
}

/** Der ganze Text der Budgetzeile, sobald der Stand da ist. */
async function zeile(): Promise<string> {
  const gruppe = await screen.findByTestId('budgetzeile-stand')
  return lesbar(gruppe.textContent)
}

beforeEach(() => {
  auth.vorschau = null
})

describe('LeitstandBudget — Szenarien der Fachlichen Quelle #1957', () => {
  test('Szenario: Hochrechnung über dem Budget', async () => {
    zeige()

    const text = await zeile()
    expect(text).toContain('laufender Monat: 40 $ von 100 $ (40 %)')
    expect(text).toContain('voraussichtlich 124 $ bis Monatsende (Schätzung)')
    expect(text).toContain('voraussichtlich überschritten am 25. Oktober (Schätzung)')
    expect(text).not.toContain('Warnung')
  })

  test('Szenario: Budget überschritten', async () => {
    zeige(stand({ spentUsd: 110, percent: 110, warning: 'REACHED', forecastUsd: 170.5, exceedDate: null }))

    const text = await zeile()
    expect(text).toContain('laufender Monat: 110 $ von 100 $ (110 %)')
    expect(text).toContain('Warnung: Das Monatsbudget ist erreicht.')
    expect(text).toContain('voraussichtlich 170,50 $ bis Monatsende (Schätzung)')
    expect(text).not.toContain('überschritten')
  })

  test('Szenario: Zu früh für eine Hochrechnung', async () => {
    zeige(stand({ monthFirstDay: '2026-10-01', spentUsd: 3, percent: 3, forecastUsd: null, tooEarly: true, exceedDate: null }))

    const text = await zeile()
    expect(text).toContain('Für eine Hochrechnung ist es noch zu früh.')
    expect(text).not.toContain('voraussichtlich')
  })

  test('Szenario: Nur der Owner legt das Budget fest', async () => {
    zeige(stand({ editable: false }))

    expect(await zeile()).toContain('von 100 $')
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.queryByRole('button')).toBeNull()
  })

  test('Szenario: Ohne Budget', async () => {
    zeige(stand({ budgetUsd: null, percent: null, exceedDate: null }))

    const text = await zeile()
    expect(text).toContain('voraussichtlich 124 $ bis Monatsende (Schätzung)')
    expect(text).not.toContain('von')
    expect(text).not.toContain('%')
    expect(text).not.toContain('Warnung')
    expect(screen.getByText('Hier lässt sich ein Monatsbudget festlegen.')).toBeInTheDocument()
  })
})

describe('LeitstandBudget — Anzeige', () => {
  it('zeigt bei 80 % die Warnung mit dem Wort „Warnung"', async () => {
    zeige(stand({ spentUsd: 85, percent: 85, warning: 'EIGHTY', forecastUsd: 263.5, exceedDate: '2026-10-12' }))

    const text = await zeile()
    expect(text).toContain('laufender Monat: 85 $ von 100 $ (85 %)')
    expect(text).toContain('Warnung: 80 % des Monatsbudgets sind erreicht.')
  })

  it('zeigt den Hinweis für den Owner ohne Budget nur bei editable', async () => {
    zeige(stand({ budgetUsd: null, percent: null, exceedDate: null, editable: false }))

    await zeile()
    expect(screen.queryByText('Hier lässt sich ein Monatsbudget festlegen.')).toBeNull()
  })

  it('sagt ohne gemeldete Kosten „keine Kosten gemeldet" ohne Anteil, Warnung und Hochrechnung (E2)', async () => {
    zeige(stand({ spentUsd: null, percent: null, forecastUsd: null, exceedDate: null }))

    const text = await zeile()
    expect(text).toContain('laufender Monat: keine Kosten gemeldet (Budget 100 $)')
    expect(text).not.toContain('%')
    expect(text).not.toContain('Warnung')
    expect(text).not.toContain('voraussichtlich')
    expect(text).not.toContain('zu früh')
  })

  it('sagt ohne Budget und ohne Kosten nur „keine Kosten gemeldet" (E2)', async () => {
    zeige(stand({ budgetUsd: null, spentUsd: null, percent: null, forecastUsd: null, tooEarly: true, exceedDate: null }))

    const text = await zeile()
    expect(text).toContain('laufender Monat: keine Kosten gemeldet')
    expect(text).not.toContain('Budget')
    expect(text).not.toContain('zu früh')
  })

  it('zeigt bei teilweiser Abdeckung denselben Hinweis wie die Zeiträume (E1)', async () => {
    zeige(stand({ coverage: 'PARTIAL' }))

    expect(await zeile()).toContain('Dieser Zeitraum ist nur teilweise abgedeckt')
  })

  it('zeigt bei vollständiger Abdeckung keinen Abdeckungshinweis', async () => {
    zeige()

    expect(await zeile()).not.toContain('teilweise')
  })

  it('zeigt Beträge mit Nachkommastellen und Tausendertrennung', async () => {
    zeige(stand({ budgetUsd: 1500.5, spentUsd: 40.25, percent: 2, exceedDate: null }))

    expect(await zeile()).toContain('laufender Monat: 40,25 $ von 1.500,50 $ (2 %)')
  })

  it('zeigt beim Laden einen Satz und nach einem Fehler einen eigenen', async () => {
    zeige(new Error('kaputt'))

    expect(screen.getByText('Das Monatsbudget wird geladen …')).toBeInTheDocument()
    expect(await screen.findByText('Das Monatsbudget konnte nicht geladen werden.')).toBeInTheDocument()
    expect(screen.queryByTestId('budgetzeile-stand')).toBeNull()
  })

  it('lädt den Stand des übergebenen Projekts', async () => {
    const { laden } = zeige()

    await zeile()
    expect(laden).toHaveBeenCalledWith(5)
  })
})

describe('LeitstandBudget — festlegen, ändern, entfernen (AK 1, 2, E4)', () => {
  const eingabe = () => screen.getByRole('textbox', { name: 'Monatsbudget in $' })
  const speichern = () => screen.getByRole('button', { name: 'Budget speichern' })
  const entfernen = () => screen.getByRole('button', { name: 'Budget entfernen' })

  it('nimmt Komma an und sendet mit Punkt, danach lädt es den Stand neu', async () => {
    const { laden, schreiben } = zeige()
    await zeile()

    fireEvent.change(eingabe(), { target: { value: ' 150,5 ' } })
    fireEvent.click(speichern())

    await waitFor(() => expect(laden).toHaveBeenCalledTimes(2))
    expect(schreiben.setMonthlyBudget).toHaveBeenCalledWith(5, '150.5')
    expect(eingabe()).toHaveValue('')
  })

  it('nimmt Punkt an', async () => {
    const { schreiben } = zeige()
    await zeile()

    fireEvent.change(eingabe(), { target: { value: '99.99' } })
    fireEvent.submit(eingabe())

    await waitFor(() => expect(schreiben.setMonthlyBudget).toHaveBeenCalledWith(5, '99.99'))
  })

  it.each(['0', '0,00', '-5', '1,005', '12.345', 'abc', '', '1,'])('weist „%s" vorab ab, ohne zu senden', async (wert) => {
    const { schreiben } = zeige()
    await zeile()

    fireEvent.change(eingabe(), { target: { value: wert } })
    fireEvent.click(speichern())

    expect(
      await screen.findByText('Ein Budget ist größer als 0 $ und hat höchstens zwei Nachkommastellen.'),
    ).toBeInTheDocument()
    expect(schreiben.setMonthlyBudget).not.toHaveBeenCalled()
  })

  it('zeigt sonst detail der Problem-Antwort und lädt nicht neu', async () => {
    const { laden, schreiben } = zeige()
    schreiben.setMonthlyBudget.mockRejectedValue(new ApiError(400, 'Bad Request', undefined, 'Betrag zu groß.'))
    await zeile()

    fireEvent.change(eingabe(), { target: { value: '99999999999' } })
    fireEvent.click(speichern())

    expect(await screen.findByText('Betrag zu groß.')).toBeInTheDocument()
    expect(laden).toHaveBeenCalledTimes(1)
  })

  it('zeigt ohne detail eine eigene Meldung', async () => {
    const { schreiben } = zeige()
    schreiben.setMonthlyBudget.mockRejectedValue(new Error('Netz'))
    await zeile()

    fireEvent.change(eingabe(), { target: { value: '10' } })
    fireEvent.click(speichern())

    expect(await screen.findByText('Das Monatsbudget konnte nicht gespeichert werden.')).toBeInTheDocument()
  })

  it('nimmt eine frühere Meldung nach einer gültigen Eingabe zurück', async () => {
    zeige()
    await zeile()

    fireEvent.change(eingabe(), { target: { value: '0' } })
    fireEvent.click(speichern())
    await screen.findByText('Ein Budget ist größer als 0 $ und hat höchstens zwei Nachkommastellen.')
    fireEvent.change(eingabe(), { target: { value: '10' } })
    fireEvent.click(speichern())

    await waitFor(() =>
      expect(screen.queryByText('Ein Budget ist größer als 0 $ und hat höchstens zwei Nachkommastellen.')).toBeNull(),
    )
  })

  it('entfernt das Budget und lädt den Stand neu', async () => {
    const { laden, schreiben } = zeige()
    await zeile()

    fireEvent.click(entfernen())

    await waitFor(() => expect(laden).toHaveBeenCalledTimes(2))
    expect(schreiben.removeMonthlyBudget).toHaveBeenCalledWith(5)
  })

  it('meldet ein gescheitertes Entfernen', async () => {
    const { laden, schreiben } = zeige()
    schreiben.removeMonthlyBudget.mockRejectedValue(new Error('Netz'))
    await zeile()

    fireEvent.click(entfernen())

    expect(await screen.findByText('Das Monatsbudget konnte nicht entfernt werden.')).toBeInTheDocument()
    expect(laden).toHaveBeenCalledTimes(1)
  })

  it('bietet Entfernen nur an, wenn ein Budget festgelegt ist', async () => {
    zeige(stand({ budgetUsd: null, percent: null, exceedDate: null }))
    await zeile()

    expect(speichern()).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Budget entfernen' })).toBeNull()
  })

  it('sperrt in der Vorschau Speichern und Entfernen', async () => {
    auth.vorschau = { kennung: 'vk-1', zielName: 'Grace', adminName: 'Ada', laeuftAbAm: '2026-10-27T10:30:00Z' }
    zeige()
    await zeile()

    const knoepfe = within(screen.getByTestId('budgetzeile')).getAllByRole('button')
    expect(knoepfe).toHaveLength(2)
    knoepfe.forEach((knopf) => expect(knopf).toBeDisabled())
  })
})

describe('LeitstandBudget auf Englisch (AK 14)', () => {
  it('zeigt Stand, Warnung, Hochrechnung und Überschreitungstag auf Englisch', async () => {
    zeige(stand({ spentUsd: 85, percent: 85, warning: 'EIGHTY', forecastUsd: 263.5, exceedDate: '2026-10-12' }), 'en')

    const text = await zeile()
    expect(text).toContain('current month: $85 of $100 (85%)')
    expect(text).toContain('Warning: 80% of the monthly budget reached.')
    expect(text).toContain('projected $263.50 by month end (estimate)')
    expect(text).toContain('projected to be exceeded on October 12 (estimate)')
    expect(screen.getByRole('button', { name: 'Save budget' })).toBeInTheDocument()
  })

  it('zeigt die übrigen Sätze auf Englisch', async () => {
    zeige(stand({ budgetUsd: null, spentUsd: null, percent: null, forecastUsd: null, exceedDate: null }), 'en')

    expect(await zeile()).toContain('current month: no costs reported')
    expect(screen.getByText('A monthly budget can be set here.')).toBeInTheDocument()
  })

  it('nennt ohne gemeldete Kosten das Budget auf Englisch', async () => {
    zeige(stand({ spentUsd: null, percent: null, forecastUsd: null, exceedDate: null }), 'en')

    expect(await zeile()).toContain('current month: no costs reported (budget $100)')
  })
})
