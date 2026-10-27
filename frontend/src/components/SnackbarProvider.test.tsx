import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ThemeProvider } from '@mui/material/styles'
import { cssRegel } from '../test/cssRegel'
import { theme } from '../theme'
import { SnackbarProvider, useSnackbar } from './SnackbarProvider'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { apiFetch } from '../api/client'

function Harness() {
  const notify = useSnackbar()
  return (
    <>
      <button onClick={() => notify('Erfolg', 'success')}>ok</button>
      <button onClick={() => notify('Warnung', 'warning')}>warn</button>
      <button onClick={() => notify('Fehler', 'error')}>err</button>
      <button onClick={() => notify('Info')}>info</button>
    </>
  )
}

function renderProvider() {
  return render(
    <SnackbarProvider>
      <Harness />
    </SnackbarProvider>,
  )
}

beforeEach(() => vi.useFakeTimers())
afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
})

describe('SnackbarProvider', () => {
  // success/warning/info (Info = Default-Severity, notify ohne severity) blenden alle nach 3 s aus.
  it.each([
    ['Erfolgsmeldung', 'ok', 'Erfolg'],
    ['Warnung', 'warn', 'Warnung'],
    ['Info-Meldung (Default-Severity)', 'info', 'Info'],
  ])('blendet eine %s nach 3 s aus', (_label, button, message) => {
    renderProvider()
    fireEvent.click(screen.getByText(button))
    expect(screen.getByText(message)).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(3000))

    expect(screen.queryByText(message)).not.toBeInTheDocument()
  })

  it('lässt einen Fehler stehen (blendet nicht aus)', () => {
    renderProvider()
    fireEvent.click(screen.getByText('err'))
    expect(screen.getByText('Fehler')).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(10_000))

    expect(screen.getByText('Fehler')).toBeInTheDocument()
  })

  it('schließt einen Fehler über das X', () => {
    renderProvider()
    fireEvent.click(screen.getByText('err'))
    expect(screen.getByText('Fehler')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Close'))

    expect(screen.queryByText('Fehler')).not.toBeInTheDocument()
  })

  it('schließt eine Erfolgsmeldung über das X und räumt den Timer ab', () => {
    renderProvider()
    fireEvent.click(screen.getByText('ok'))

    fireEvent.click(screen.getByLabelText('Close'))
    expect(screen.queryByText('Erfolg')).not.toBeInTheDocument()

    // Der Auto-Hide-Timer wurde beim Schließen abgeräumt -> kein zweites remove, kein Fehler.
    act(() => vi.advanceTimersByTime(3000))
    expect(screen.queryByText('Erfolg')).not.toBeInTheDocument()
  })

  it('stapelt mehrere Toasts; der Fehler bleibt, während der Erfolg ausblendet', () => {
    renderProvider()
    fireEvent.click(screen.getByText('err'))
    fireEvent.click(screen.getByText('ok'))
    expect(screen.getByText('Fehler')).toBeInTheDocument()
    expect(screen.getByText('Erfolg')).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(3000))

    expect(screen.getByText('Fehler')).toBeInTheDocument()
    expect(screen.queryByText('Erfolg')).not.toBeInTheDocument()
  })

  it('räumt Timer beim Unmount ab (kein State-Update danach)', () => {
    const { unmount } = renderProvider()
    fireEvent.click(screen.getByText('ok'))
    expect(screen.getByText('Erfolg')).toBeInTheDocument()

    unmount()

    act(() => vi.advanceTimersByTime(3000))
  })

  it('ist ohne Provider ein No-op (kein Crash)', () => {
    render(<Harness />)
    fireEvent.click(screen.getByText('ok'))
    expect(screen.queryByText('Erfolg')).not.toBeInTheDocument()
  })
})

describe('SnackbarProvider in beiden Erscheinungsbildern (#960)', () => {
  it.each([
    ['err', 'Fehler', 'error'],
    ['warn', 'Warnung', 'warning'],
    ['ok', 'Erfolg', 'success'],
    ['info', 'Info', 'info'],
  ])('färbt die Meldung „%s" aus den Zustandsfarben des Themes, nicht aus einem festen Wert', (button, text, zustand) => {
    render(
      <ThemeProvider theme={theme}>
        <SnackbarProvider>
          <Harness />
        </SnackbarProvider>
      </ThemeProvider>,
    )
    fireEvent.click(screen.getByText(button))

    const meldung = screen.getByRole('alert')
    expect(meldung).toHaveTextContent(text)
    const regel = cssRegel(meldung)
    expect(regel).toContain(`background-color: var(--mb-palette-Alert-${zustand}FilledBg)`)
    expect(regel).toContain(`color: var(--mb-palette-Alert-${zustand}FilledColor)`)
    expect(regel).not.toMatch(/#[0-9A-Fa-f]{3,8}\b/)
  })
})

describe('SnackbarProvider auf Englisch', () => {
  it('zeigt die Meldung des Aufrufers unverändert und lässt einen Fehler stehen', () => {
    renderMitSprache(
      <SnackbarProvider>
        <Harness />
      </SnackbarProvider>,
      'en',
    )
    fireEvent.click(screen.getByText('err'))

    act(() => vi.advanceTimersByTime(3000))
    expect(screen.getByText('Fehler')).toBeInTheDocument()
  })
})

describe('SnackbarProvider – Netz der Vorschau (Plan #1914, E15)', () => {
  function mockNurLesend() {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      text: () => Promise.resolve(JSON.stringify({ type: 'vorschau-nur-lesend', status: 403 })),
    } as Response)
  }

  beforeEach(() => vi.useRealTimers())
  afterEach(() => vi.restoreAllMocks())

  it('meldet „Vorschau – nur lesend“, wenn der Server einen Änderungsaufruf abweist', async () => {
    mockNurLesend()
    renderProvider()

    await act(() => apiFetch('/api/cards/1', { method: 'PATCH' }).catch(() => undefined))

    const meldung = await screen.findByRole('alert')
    expect(meldung).toHaveTextContent('Vorschau – nur lesend')
    expect(meldung).toHaveClass('MuiAlert-filledWarning')
  })

  it('meldet auf Englisch „Preview – read only“', async () => {
    mockNurLesend()
    renderMitSprache(
      <SnackbarProvider>
        <Harness />
      </SnackbarProvider>,
      'en',
    )

    await act(() => apiFetch('/api/cards/1', { method: 'PATCH' }).catch(() => undefined))

    expect(await screen.findByText('Preview – read only')).toBeInTheDocument()
  })

  it('meldet nach dem Unmount nichts mehr', async () => {
    mockNurLesend()
    const { unmount } = renderProvider()
    unmount()

    await apiFetch('/api/cards/1', { method: 'PATCH' }).catch(() => undefined)

    expect(screen.queryByText('Vorschau – nur lesend')).not.toBeInTheDocument()
  })
})
