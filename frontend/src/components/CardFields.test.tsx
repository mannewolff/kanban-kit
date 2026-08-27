import { fireEvent, render, screen } from '@testing-library/react'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import type { Epic } from '../api/epics'
import { MAX_TEXT_LENGTH } from '../lib/textLimits'
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
    expect(screen.getByLabelText('Vorhaben')).toBeInTheDocument()
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
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '9' } })
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '' } })
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

    expect(screen.getByLabelText('Vorhaben')).toHaveValue('9')
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

    expect(screen.queryByLabelText('Vorhaben')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Abhängig von')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Fällig am')).not.toBeInTheDocument()
  })

  // Checkbox-Kurzschreibweise (Issue #420): Der Hook führt den Cursor nach dem Render nach, deshalb
  // braucht es hier eine echt kontrollierte Umgebung statt eines onChange-Mocks.
  function Controlled({ initial = '' }: Readonly<{ initial?: string }>) {
    const [body, setBody] = useState(initial)
    return (
      <CardFields
        isEpic={false}
        title="T"
        body={body}
        shortcode=""
        parentId={null}
        epics={epics}
        depsInput=""
        depsError={null}
        dueInput=""
        onTitleChange={vi.fn()}
        onBodyChange={setBody}
        onShortcodeChange={vi.fn()}
        onParentIdChange={vi.fn()}
        onDepsInputChange={vi.fn()}
        onDueInputChange={vi.fn()}
      />
    )
  }

  function typeInBody(value: string, caret: number): HTMLTextAreaElement {
    const field = screen.getByLabelText('Markdown-Beschreibung') as HTMLTextAreaElement
    fireEvent.change(field, { target: { value, selectionStart: caret, selectionEnd: caret } })
    return field
  }

  it('macht aus Slash plus Leerzeichen eine leere Checkbox und setzt den Cursor dahinter', () => {
    render(<Controlled />)
    const field = typeInBody('/ ', 2)
    expect(field).toHaveValue('- [ ] ')
    expect(field.selectionStart).toBe(6)
  })

  it('macht aus /x plus Leerzeichen eine abgehakte Checkbox', () => {
    render(<Controlled />)
    expect(typeInBody('/x ', 3)).toHaveValue('- [x] ')
  })

  it('lässt eine Zeile mit einem Pfad unangetastet', () => {
    render(<Controlled />)
    expect(typeInBody('/api/cards ', 11)).toHaveValue('/api/cards ')
  })

  it('nimmt die Ersetzung per Backspace zurück und stellt den Cursor wieder her', () => {
    render(<Controlled />)
    const field = typeInBody('/ ', 2)
    expect(field).toHaveValue('- [ ] ')
    fireEvent.keyDown(field, { key: 'Backspace' })
    expect(field).toHaveValue('/ ')
    expect(field.selectionStart).toBe(2)
  })

  it('nimmt nur unmittelbar zurück: nach einer anderen Taste bleibt die Checkbox stehen', () => {
    render(<Controlled />)
    const field = typeInBody('/ ', 2)
    fireEvent.keyDown(field, { key: 'a' })
    fireEvent.keyDown(field, { key: 'Backspace' })
    expect(field).toHaveValue('- [ ] ')
  })

  it('lässt Backspace ohne vorangehende Ersetzung unverändert durch', () => {
    render(<Controlled initial="Text" />)
    const field = screen.getByLabelText('Markdown-Beschreibung') as HTMLTextAreaElement
    fireEvent.keyDown(field, { key: 'Backspace' })
    expect(field).toHaveValue('Text')
  })

  it('haelt eine zu lange Beschreibung vollstaendig und meldet die Ueberschreitung', () => {
    // Kein maxLength mehr: eingefuegter Text darf nicht lautlos verschwinden (Issue #572).
    const h = handlers()
    const tooLong = 'a'.repeat(MAX_TEXT_LENGTH + 10_000)
    render(
      <CardFields
        isEpic={false}
        title="T"
        body={tooLong}
        shortcode=""
        parentId={null}
        epics={epics}
        depsInput=""
        depsError={null}
        dueInput=""
        {...h}
      />,
    )

    const field = screen.getByLabelText('Markdown-Beschreibung')
    expect(field).toHaveValue(tooLong)
    expect(field).not.toHaveAttribute('maxlength')
    expect(screen.getByText('60.000 / 50.000 Zeichen')).toBeInTheDocument()
  })

  it('meldet an der Grenze nichts', () => {
    const h = handlers()
    render(
      <CardFields
        isEpic={false}
        title="T"
        body={'a'.repeat(MAX_TEXT_LENGTH)}
        shortcode=""
        parentId={null}
        epics={epics}
        depsInput=""
        depsError={null}
        dueInput=""
        {...h}
      />,
    )

    expect(screen.queryByText(/\/ 50\.000 Zeichen/)).not.toBeInTheDocument()
  })

  it('zeigt die Epic-Zuordnung ohne ladbare Epic-Liste nur lesend', () => {
    const h = handlers()
    render(
      <CardFields
        isEpic={false}
        title="T"
        body=""
        shortcode=""
        parentId={9}
        epics={[]}
        epicReadOnly
        depsInput=""
        depsError={null}
        dueInput=""
        {...h}
      />,
    )

    const field = screen.getByLabelText('Vorhaben')
    expect(field).toHaveValue('#9')
    expect(field).toHaveAttribute('readonly')
    // Kein Auswahlvorrat: Es gibt nichts anzuklicken, was die Zuordnung löschen könnte.
    expect(screen.queryByRole('option')).not.toBeInTheDocument()
    expect(h.onParentIdChange).not.toHaveBeenCalled()
  })

  it('benennt die fehlende Epic-Zuordnung auch im Nur-Lese-Fall', () => {
    const h = handlers()
    render(
      <CardFields
        isEpic={false}
        title="T"
        body=""
        shortcode=""
        parentId={null}
        epics={[]}
        epicReadOnly
        depsInput=""
        depsError={null}
        dueInput=""
        {...h}
      />,
    )

    expect(screen.getByLabelText('Vorhaben')).toHaveValue('(kein Vorhaben)')
  })
})
