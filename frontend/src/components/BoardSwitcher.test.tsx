import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { boardsApi } from '../api/boards'
import { ApiError } from '../api/client'
import type { BoardHistoryEntry } from '../lib/useBoardHistory'
import { BoardSwitcher } from './BoardSwitcher'

const navigateMock = vi.fn()
vi.mock('react-router-dom', async (orig) => {
  const actual = await orig<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => navigateMock }
})

vi.mock('../api/boards', () => ({
  boardsApi: { get: vi.fn() },
}))

const mockedBoards = boardsApi as unknown as { get: ReturnType<typeof vi.fn> }

const ENTRIES: BoardHistoryEntry[] = [
  { id: 1, name: 'Alpha', projectName: 'Projekt A' },
  { id: 2, name: 'Beta', projectName: 'Projekt B' },
  { id: 3, name: 'Gamma', projectName: 'Projekt C' },
]

const onClose = vi.fn()
const onRemoveEntry = vi.fn()
const onNotify = vi.fn()

type SwitcherProps = Partial<React.ComponentProps<typeof BoardSwitcher>>

function switcher(props: SwitcherProps = {}) {
  return (
    <BoardSwitcher
      open
      entries={ENTRIES}
      currentBoardId={null}
      onClose={onClose}
      onRemoveEntry={onRemoveEntry}
      onNotify={onNotify}
      {...props}
    />
  )
}

function renderSwitcher(props: SwitcherProps = {}): ReturnType<typeof render> {
  return render(switcher(props))
}

/** Name des gerade ausgewählten Eintrags — die Auswahl ist über `aria-selected` abfragbar. */
function selectedName(): string {
  return screen.getByRole('option', { selected: true }).textContent ?? ''
}

interface KeyModifiers {
  ctrlKey?: boolean
  altKey?: boolean
  metaKey?: boolean
}

/**
 * Ein Tastendruck auf dem ausgewählten Eintrag — dort liegt im Betrieb der Fokus (eigens belegt),
 * und dorthin gehen die Tasten eines Menschen, der das Overlay bedient.
 */
function press(key: string, modifiers: KeyModifiers = {}): void {
  fireEvent.keyDown(screen.getByRole('option', { selected: true }), { key, ...modifiers })
}

beforeEach(() => {
  vi.clearAllMocks()
  mockedBoards.get.mockResolvedValue({ id: 1, name: 'Alpha', projectId: 5, columns: [] })
})

describe('BoardSwitcher', () => {
  describe('Vorauswahl und Sichtbarkeit', () => {
    it('wählt Eintrag 2 vor, wenn Eintrag 1 das aktuelle Board ist', () => {
      renderSwitcher({ currentBoardId: 1 })

      expect(selectedName()).toContain('Beta')
    })

    it('wählt Eintrag 1 vor, wenn kein aktuelles Board bekannt ist', () => {
      renderSwitcher({ currentBoardId: null })

      expect(selectedName()).toContain('Alpha')
    })

    it('wählt Eintrag 1 vor, wenn das aktuelle Board nicht an Position 1 steht', () => {
      renderSwitcher({ currentBoardId: 3 })

      expect(selectedName()).toContain('Alpha')
    })

    it('rendert bei leerem Verlauf trotz open keinen Dialog', () => {
      renderSwitcher({ entries: [] })

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('rendert keinen Dialog, wenn der Verlauf nur das aktuelle Board enthält', () => {
      renderSwitcher({ entries: [ENTRIES[0]], currentBoardId: 1 })

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('bietet einen einzelnen Eintrag an, wenn kein aktuelles Board bekannt ist', () => {
      renderSwitcher({ entries: [ENTRIES[0]], currentBoardId: null })

      expect(screen.getByRole('dialog')).toBeInTheDocument()
      expect(selectedName()).toContain('Alpha')
    })

    it('rendert bei open=false keinen Dialog', () => {
      renderSwitcher({ open: false })

      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('hält genau eine Auswahl, wenn der Verlauf während des Wechsels schrumpft', () => {
      const { rerender } = renderSwitcher()
      press('ArrowUp')
      expect(selectedName()).toContain('Gamma')

      rerender(switcher({ entries: [ENTRIES[0], ENTRIES[1]] }))

      expect(screen.getAllByRole('option', { selected: true })).toHaveLength(1)
      expect(selectedName()).toContain('Beta')
    })

    it('beginnt beim Wiederöffnen mit frischer Vorauswahl', () => {
      const { rerender } = renderSwitcher({ open: false })

      rerender(switcher({ open: true }))
      press('ArrowDown')
      expect(selectedName()).toContain('Beta')

      rerender(switcher({ open: false }))
      rerender(switcher({ open: true }))

      expect(selectedName()).toContain('Alpha')
    })

    it('zeigt beim Wiederöffnen keinen Hinweis eines vorigen Versuchs', async () => {
      mockedBoards.get.mockRejectedValue(new ApiError(404, 'weg'))
      const { rerender } = renderSwitcher()
      press('Enter')
      expect(await screen.findByRole('alert')).toBeInTheDocument()

      rerender(switcher({ open: false }))
      rerender(switcher({ open: true }))

      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  describe('Tastaturführung', () => {
    it.each(['b', 'ArrowDown'])('schaltet mit %s zyklisch vorwärts', (key) => {
      renderSwitcher()

      press(key)
      expect(selectedName()).toContain('Beta')
      press(key)
      expect(selectedName()).toContain('Gamma')
      press(key)
      expect(selectedName()).toContain('Alpha')
    })

    it('schaltet mit ArrowUp zyklisch rückwärts', () => {
      renderSwitcher()

      press('ArrowUp')
      expect(selectedName()).toContain('Gamma')
      press('ArrowUp')
      expect(selectedName()).toContain('Beta')
      press('ArrowUp')
      expect(selectedName()).toContain('Alpha')
    })

    it.each([{ ctrlKey: true }, { altKey: true }, { metaKey: true }])(
      'ändert die Auswahl nicht bei %o',
      (modifiers) => {
        renderSwitcher()

        press('b', modifiers)
        press('ArrowDown', modifiers)
        press('ArrowUp', modifiers)

        expect(selectedName()).toContain('Alpha')
      },
    )

    it('ignoriert Tasten ohne Bedeutung', () => {
      renderSwitcher()

      press('x')

      expect(selectedName()).toContain('Alpha')
      expect(mockedBoards.get).not.toHaveBeenCalled()
    })

    it('führt den Fokus mit der Auswahl weiter', () => {
      renderSwitcher()

      press('ArrowDown')

      expect(screen.getByRole('option', { selected: true })).toHaveFocus()
    })
  })

  describe('Zielprüfung vor der Navigation', () => {
    it('navigiert erst nach erfolgreicher Prüfung und schließt dann', async () => {
      let resolve = (): void => {}
      mockedBoards.get.mockReturnValue(
        new Promise((res) => {
          resolve = () => res({ id: 2, name: 'Beta', projectId: 5, columns: [] })
        }),
      )
      renderSwitcher()

      press('ArrowDown')
      press('Enter')

      expect(mockedBoards.get).toHaveBeenCalledWith(2)
      expect(navigateMock).not.toHaveBeenCalled()
      expect(onClose).not.toHaveBeenCalled()

      resolve()

      await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/boards/2'))
      expect(navigateMock).toHaveBeenCalledTimes(1)
      expect(onClose).toHaveBeenCalledTimes(1)
    })

    it('öffnet einen Eintrag auch per Mausklick', async () => {
      renderSwitcher()

      fireEvent.click(screen.getByRole('option', { name: /Gamma/ }))

      await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/boards/3'))
    })

    it('schließt mit Escape ohne Sprung', () => {
      renderSwitcher()

      fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })

      expect(onClose).toHaveBeenCalledTimes(1)
      expect(navigateMock).not.toHaveBeenCalled()
      expect(mockedBoards.get).not.toHaveBeenCalled()
    })

    it.each([403, 404])('entfernt bei Status %i den Eintrag und meldet es', async (status) => {
      mockedBoards.get.mockRejectedValue(new ApiError(status, 'weg'))
      renderSwitcher()

      press('Enter')

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'Board ist nicht mehr verfügbar.',
      )
      expect(onRemoveEntry).toHaveBeenCalledTimes(1)
      expect(onRemoveEntry).toHaveBeenCalledWith(1)
      expect(navigateMock).not.toHaveBeenCalled()
      expect(onClose).not.toHaveBeenCalled()
    })

    it.each([
      ['ApiError(401)', new ApiError(401, 'abgemeldet')],
      ['ApiError(500)', new ApiError(500, 'kaputt')],
      ['Nicht-ApiError', new TypeError('offline')],
    ])('lässt den Verlauf bei %s unangetastet', async (_label, failure) => {
      mockedBoards.get.mockRejectedValue(failure)
      renderSwitcher()

      press('Enter')

      await waitFor(() => expect(onNotify).toHaveBeenCalledTimes(1))
      expect(onNotify).toHaveBeenCalledWith('Das Board konnte nicht geöffnet werden.', 'error')
      expect(onRemoveEntry).not.toHaveBeenCalled()
      expect(navigateMock).not.toHaveBeenCalled()
    })

    it('sperrt eine zweite Auswahl während der laufenden Prüfung', () => {
      mockedBoards.get.mockReturnValue(new Promise(() => {}))
      renderSwitcher()

      press('Enter')
      press('ArrowDown')
      press('Enter')

      expect(mockedBoards.get).toHaveBeenCalledTimes(1)
    })

    it('gibt die Auswahl nach einem Fehlschlag wieder frei', async () => {
      mockedBoards.get.mockRejectedValueOnce(new ApiError(500, 'kaputt'))
      renderSwitcher()

      press('Enter')
      await waitFor(() => expect(onNotify).toHaveBeenCalledTimes(1))

      press('Enter')

      await waitFor(() => expect(navigateMock).toHaveBeenCalledWith('/boards/1'))
      expect(mockedBoards.get).toHaveBeenCalledTimes(2)
    })

    it('navigiert nicht, wenn der Dialog während der Prüfung geschlossen wurde', async () => {
      let resolve = (): void => {}
      mockedBoards.get.mockReturnValue(
        new Promise((res) => {
          resolve = () => res({ id: 1, name: 'Alpha', projectId: 5, columns: [] })
        }),
      )
      renderSwitcher()

      press('Enter')
      fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
      resolve()
      await Promise.resolve()

      await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
      expect(navigateMock).not.toHaveBeenCalled()
    })

    it('entfernt keinen Eintrag, wenn der Dialog vor der Fehlerantwort geschlossen wurde', async () => {
      let reject = (): void => {}
      mockedBoards.get.mockReturnValue(
        new Promise((_res, rej) => {
          reject = () => rej(new ApiError(404, 'weg'))
        }),
      )
      renderSwitcher()

      press('Enter')
      fireEvent.keyDown(screen.getByRole('dialog'), { key: 'Escape' })
      reject()

      await waitFor(() => expect(onClose).toHaveBeenCalledTimes(1))
      expect(onRemoveEntry).not.toHaveBeenCalled()
      expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    })
  })

  describe('Zugänglichkeit', () => {
    it('benennt Dialog, Liste und Einträge und fokussiert die Vorauswahl', () => {
      renderSwitcher({ currentBoardId: 1 })

      expect(screen.getByRole('dialog', { name: 'Board wechseln' })).toBeInTheDocument()
      expect(screen.getByRole('listbox')).toBeInTheDocument()
      expect(screen.getAllByRole('option')).toHaveLength(3)
      expect(screen.getAllByRole('option', { selected: true })).toHaveLength(1)
      expect(screen.getByRole('option', { selected: true })).toHaveFocus()
    })

    it('unterscheidet gleichnamige Boards über den Projektnamen', () => {
      renderSwitcher({
        entries: [
          { id: 7, name: 'Board', projectName: 'Projekt A' },
          { id: 8, name: 'Board', projectName: 'Projekt B' },
        ],
      })

      expect(screen.getByRole('option', { name: /Board.*Projekt A/ })).toBeInTheDocument()
      expect(screen.getByRole('option', { name: /Board.*Projekt B/ })).toBeInTheDocument()
    })
  })
})
