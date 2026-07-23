import { act, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SnackbarProvider, useSnackbar } from './SnackbarProvider'

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
  it('blendet eine Erfolgsmeldung nach 3 s aus', () => {
    renderProvider()
    fireEvent.click(screen.getByText('ok'))
    expect(screen.getByText('Erfolg')).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(3000))

    expect(screen.queryByText('Erfolg')).not.toBeInTheDocument()
  })

  it('blendet eine Warnung nach 3 s aus', () => {
    renderProvider()
    fireEvent.click(screen.getByText('warn'))
    expect(screen.getByText('Warnung')).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(3000))

    expect(screen.queryByText('Warnung')).not.toBeInTheDocument()
  })

  it('blendet eine Info-Meldung (Default-Severity) nach 3 s aus', () => {
    renderProvider()
    fireEvent.click(screen.getByText('info'))
    expect(screen.getByText('Info')).toBeInTheDocument()

    act(() => vi.advanceTimersByTime(3000))

    expect(screen.queryByText('Info')).not.toBeInTheDocument()
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
