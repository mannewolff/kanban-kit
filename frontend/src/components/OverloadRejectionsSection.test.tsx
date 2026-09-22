import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { OverloadRejection } from '../api/admin'
import { OverloadRejectionsSection } from './OverloadRejectionsSection'

function api(result: Promise<OverloadRejection[]>) {
  return { listOverloadRejections: vi.fn().mockReturnValue(result) }
}

describe('OverloadRejectionsSection', () => {
  it('zeigt die Abweisungen je Person und Stunde in der gelieferten Reihenfolge', async () => {
    render(
      <OverloadRejectionsSection
        api={api(
          Promise.resolve([
            { userId: 8, displayName: 'Bob', hour: '2026-09-22T08:00:00Z', rejections: 3 },
            { userId: 7, displayName: 'Alice', hour: '2026-09-22T07:00:00Z', rejections: 12 },
          ]),
        )}
      />,
    )

    const bob = await screen.findByRole('row', { name: /Bob/ })
    // Die Stunde erscheint in deutscher Ortszeit (Tests laufen in Europe/Berlin).
    expect(within(bob).getByText('22.09.2026, 10:00')).toBeInTheDocument()
    expect(within(bob).getByText('3')).toBeInTheDocument()
    const zeilen = screen.getAllByRole('row').slice(1)
    expect(zeilen.map((z) => within(z).getAllByRole('cell')[0].textContent)).toEqual(['Bob', 'Alice'])
  })

  it('sagt bei leerer Liste, dass es keine Abweisungen gab', async () => {
    render(<OverloadRejectionsSection api={api(Promise.resolve([]))} />)

    expect(await screen.findByText('Keine Abweisungen wegen Last in den letzten 90 Tagen.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('meldet einen Ladefehler, ohne Tabelle', async () => {
    render(<OverloadRejectionsSection api={api(Promise.reject(new Error('503')))} />)

    expect(await screen.findByText('Abweisungen wegen Last konnten nicht geladen werden.')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('zeigt beim Laden einen Hinweis und darunter nichts anderes', () => {
    render(<OverloadRejectionsSection api={api(new Promise(() => {}))} />)

    expect(screen.getByRole('heading', { name: 'Abweisungen wegen Last' })).toBeInTheDocument()
    expect(screen.getByLabelText('Abweisungen werden geladen')).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('schreibt nach dem Verlassen der Seite keinen Zustand mehr', async () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => {})
    let liefern: (zeilen: OverloadRejection[]) => void = () => {}
    let scheitern: (grund: Error) => void = () => {}
    const { unmount } = render(
      <OverloadRejectionsSection api={api(new Promise((resolve) => (liefern = resolve)))} />,
    )
    const { unmount: unmountAfterFailure } = render(
      <OverloadRejectionsSection api={api(new Promise((_, reject) => (scheitern = reject)))} />,
    )
    unmount()
    unmountAfterFailure()

    liefern([])
    scheitern(new Error('zu spät'))
    await Promise.resolve()

    expect(fehler).not.toHaveBeenCalled()
    fehler.mockRestore()
  })
})
