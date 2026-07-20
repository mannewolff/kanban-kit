import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Epic } from '../api/epics'
import { CardFields } from './CardFields'

const epics: Epic[] = [
  { id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1 },
]

function handlers() {
  return {
    onTitleChange: vi.fn(),
    onBodyChange: vi.fn(),
    onShortcodeChange: vi.fn(),
    onParentIdChange: vi.fn(),
    onDepsInputChange: vi.fn(),
    onDueInputChange: vi.fn(),
  }
}

describe('CardFields', () => {
  it('rendert für eine Karte Titel, Beschreibung, Epic, Abhängigkeiten und Fälligkeit', () => {
    const h = handlers()
    render(
      <CardFields
        isEpic={false}
        title="T"
        body="B"
        shortcode=""
        parentId={null}
        epics={epics}
        depsInput="12"
        depsError={null}
        dueInput="2026-02-01"
        {...h}
      />,
    )

    expect(screen.getByLabelText('Titel')).toHaveValue('T')
    expect(screen.getByLabelText('Markdown-Beschreibung')).toHaveValue('B')
    expect(screen.getByLabelText('Epic')).toBeInTheDocument()
    expect(screen.getByLabelText('Abhängig von')).toHaveValue('12')
    expect(screen.getByLabelText('Fällig am')).toHaveValue('2026-02-01')
    expect(screen.queryByLabelText('Kürzel')).not.toBeInTheDocument()
  })

  it('feuert die onChange-Callbacks der Kartenfelder', () => {
    const h = handlers()
    render(
      <CardFields
        isEpic={false}
        title=""
        body=""
        shortcode=""
        parentId={null}
        epics={epics}
        depsInput=""
        depsError={null}
        dueInput=""
        {...h}
      />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Neu' } })
    fireEvent.change(screen.getByLabelText('Markdown-Beschreibung'), { target: { value: 'Text' } })
    fireEvent.change(screen.getByLabelText('Epic'), { target: { value: '9' } })
    fireEvent.change(screen.getByLabelText('Epic'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('Fällig am'), { target: { value: '2026-03-01' } })

    expect(h.onTitleChange).toHaveBeenCalledWith('Neu')
    expect(h.onBodyChange).toHaveBeenCalledWith('Text')
    expect(h.onParentIdChange).toHaveBeenCalledWith(9)
    expect(h.onParentIdChange).toHaveBeenCalledWith(null)
    expect(h.onDepsInputChange).toHaveBeenCalledWith('3')
    expect(h.onDueInputChange).toHaveBeenCalledWith('2026-03-01')
  })

  it('zeigt die gewählte Epic-Zuordnung an', () => {
    render(
      <CardFields
        isEpic={false}
        title=""
        body=""
        shortcode=""
        parentId={9}
        epics={epics}
        depsInput=""
        depsError={null}
        dueInput=""
        {...handlers()}
      />,
    )

    expect(screen.getByLabelText('Epic')).toHaveValue('9')
  })

  it('zeigt einen Abhängigkeits-Fehler als helperText', () => {
    render(
      <CardFields
        isEpic={false}
        title=""
        body=""
        shortcode=""
        parentId={null}
        epics={epics}
        depsInput="x"
        depsError="Nur positive Nummern"
        dueInput=""
        {...handlers()}
      />,
    )

    expect(screen.getByText('Nur positive Nummern')).toBeInTheDocument()
  })

  it('rendert für ein Epic nur das Kürzel statt Epic/Abhängigkeiten/Fälligkeit', () => {
    const h = handlers()
    render(
      <CardFields
        isEpic
        title="E"
        body=""
        shortcode="AUT"
        parentId={null}
        epics={epics}
        depsInput=""
        depsError={null}
        dueInput=""
        {...h}
      />,
    )

    const shortcode = screen.getByLabelText('Kürzel')
    expect(shortcode).toHaveValue('AUT')
    fireEvent.change(shortcode, { target: { value: 'XY' } })
    expect(h.onShortcodeChange).toHaveBeenCalledWith('XY')

    expect(screen.queryByLabelText('Epic')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Abhängig von')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Fällig am')).not.toBeInTheDocument()
  })
})
