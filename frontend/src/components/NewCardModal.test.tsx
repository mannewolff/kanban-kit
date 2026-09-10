import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { NewCardModal } from './NewCardModal'
import { MAX_TEXT_LENGTH } from '../lib/textLimits'

describe('NewCardModal', () => {
  it('legt per Cmd/Ctrl+Enter im Titel-Feld an', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const onClose = vi.fn()
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={onClose} onSubmit={onSubmit} />,
    )

    const titleField = screen.getByLabelText('Titel')
    fireEvent.change(titleField, { target: { value: 'Neue Karte' } })
    fireEvent.keyDown(titleField, { key: 'Enter', ctrlKey: true })

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'CARD', title: 'Neue Karte' }),
    )
    await Promise.resolve()
  })

  it('reicht Epic-Zuordnung und Beschreibung durch', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    render(
      <NewCardModal open columnName="Backlog" epics={epics} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '9' } })
    // Wieder abwählen -> deckt den `? null`-Zweig der Epic-Auswahl ab.
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '9' } })
    fireEvent.change(screen.getByLabelText('Markdown-Beschreibung'), { target: { value: 'Text' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({ title: 'Karte', parentId: 9, description: 'Text' }),
    )
    await Promise.resolve()
  })

  it('legt eine Karte mit Zuständigen, Fälligkeit, Abhängigkeiten und Labels in einem Schritt an', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const members = [{ userId: 7, email: 'a@x.de', displayName: 'Alice', role: 'MEMBER' as const }]
    const boardLabels = [{ id: 5, boardId: 1, name: 'Bug', color: '#f00', countOnEpicTile: false }]
    render(
      <NewCardModal
        open
        columnName="Backlog"
        epics={[]}
        members={members}
        boardLabels={boardLabels}
        onClose={vi.fn()}
        onSubmit={onSubmit}
      />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: '3, 4' } })
    fireEvent.change(screen.getByLabelText('Fällig am'), { target: { value: '2026-03-01' } })
    fireEvent.mouseDown(screen.getByLabelText('Zuständige'))
    fireEvent.click(await screen.findByText('Alice'))
    fireEvent.mouseDown(screen.getByLabelText('Labels'))
    fireEvent.click(await screen.findByText('Bug'))
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'CARD',
        title: 'Karte',
        dependencies: [3, 4],
        dueDate: '2026-03-01T00:00:00Z',
        assigneeIds: [7],
        labelIds: [5],
      }),
    )
    await Promise.resolve()
  })

  it('meldet ungültige Abhängigkeiten und legt nichts an', async () => {
    const onSubmit = vi.fn()
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: 'abc' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(
      await screen.findByText('Nur positive Nummern, kommagetrennt (z. B. 12, 34).'),
    ).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()

    // Erneute Eingabe löscht den Fehler wieder.
    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: '3' } })
    expect(
      screen.queryByText('Nur positive Nummern, kommagetrennt (z. B. 12, 34).'),
    ).not.toBeInTheDocument()
  })

  it('zeigt im Ideen-Modus nur den schlanken Feldsatz und legt ohne Zusatzfelder an', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const epics = [{ id: 9, number: 2, title: 'Auth', description: null, shortcode: 'AUT', done: 0, total: 1, memberNumbers: [], rootNumbers: [], requirementCardNumber: null }]
    render(
      <NewCardModal open ideaOnly columnName="" epics={epics} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    expect(screen.getByLabelText('Titel')).toBeInTheDocument()
    expect(screen.getByLabelText('Beschreibung')).toBeInTheDocument()
    expect(screen.getByLabelText('Vorhaben')).toBeInTheDocument()
    // Optionsvorrat wie in der gemeinsamen Feldbasis: „(kein Vorhaben)" plus Kürzel + Titel (#781).
    expect(screen.getByRole('option', { name: '(kein Vorhaben)' })).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'AUT – Auth' })).toBeInTheDocument()
    expect(screen.queryByLabelText('Zuständige')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Labels')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Fällig am')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Abhängig von')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Idee' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: 'Ideentext' } })
    // Epic-Auswahl im schlanken Zweig auslösen (deckt onChange + beide Ternary-Seiten ab).
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '9' } })
    fireEvent.change(screen.getByLabelText('Vorhaben'), { target: { value: '' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'CARD',
        title: 'Idee',
        description: 'Ideentext',
        dependencies: [],
        dueDate: null,
        assigneeIds: [],
        labelIds: [],
      }),
    )
    await Promise.resolve()
  })

  it('legt ein Epic mit Kürzel an, ohne die neuen Kartenfelder', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <NewCardModal open epicOnly columnName="" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    expect(screen.getByLabelText('Kürzel')).toBeInTheDocument()
    expect(screen.queryByLabelText('Zuständige')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Fällig am')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Epic' } })
    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'EP' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(onSubmit).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'EPIC',
        title: 'Epic',
        shortcode: 'EP',
        dependencies: [],
        assigneeIds: [],
        labelIds: [],
      }),
    )
    await Promise.resolve()
  })

  it('legt per Ctrl+Enter bei leerem Titel nichts an (canSubmit-Guard)', () => {
    const onSubmit = vi.fn()
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.keyDown(screen.getByLabelText('Titel'), { key: 'Enter', ctrlKey: true })
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('deaktiviert „Anlegen" während des Speicherns', async () => {
    let resolve: () => void = () => {}
    const onSubmit = vi.fn().mockReturnValue(
      new Promise<void>((r) => {
        resolve = r
      }),
    )
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    await waitFor(() => expect(screen.getByRole('button', { name: 'Anlegen' })).toBeDisabled())
    resolve()
  })

  it('selektiert den Titel, wenn sich initialValues bei offenem Dialog ändern (Ref gesetzt)', async () => {
    const { rerender } = render(
      <NewCardModal
        open
        columnName="Backlog"
        epics={[]}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        initialValues={{ title: 'Alt', description: 'x', parentId: null }}
      />,
    )
    const titleField = (await screen.findByLabelText('Titel')) as HTMLInputElement
    const selectSpy = vi.spyOn(titleField, 'select')

    // Neuer initialValues-Objektbezug bei weiterhin offenem Dialog → Effekt läuft erneut, diesmal
    // ist die Ref gesetzt und select() wird aufgerufen (deckt den non-null-Zweig von Zeile 76).
    rerender(
      <NewCardModal
        open
        columnName="Backlog"
        epics={[]}
        onClose={vi.fn()}
        onSubmit={vi.fn()}
        initialValues={{ title: 'Neu', description: 'y', parentId: null }}
      />,
    )

    expect(selectSpy).toHaveBeenCalled()
  })

  it('legt ohne Ctrl/Cmd bei Enter nichts an', () => {
    const onSubmit = vi.fn()
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    const titleField = screen.getByLabelText('Titel')
    fireEvent.change(titleField, { target: { value: 'Neue Karte' } })
    fireEvent.keyDown(titleField, { key: 'Enter' })

    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('sperrt Anlegen bei einer zu langen Beschreibung, ohne Text zu verschlucken', () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    const tooLong = 'a'.repeat(MAX_TEXT_LENGTH + 10_000)
    render(
      <NewCardModal open ideaOnly columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Titel' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), { target: { value: tooLong } })

    expect(screen.getByLabelText('Beschreibung')).toHaveValue(tooLong)
    expect(screen.getByText('60.000 / 50.000 Zeichen')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    expect(onSubmit).not.toHaveBeenCalled()
  })

  // --- Eigene Fehlerbehandlung im Dialog (Issue #808) ------------------------

  it('bleibt bei einem Fehlschlag offen, behält die Eingabe und zeigt die Server-Meldung', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, 'Konflikt', undefined, 'Die Spalte hat ihr WIP-Limit erreicht.'))
    const onClose = vi.fn()
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={onClose} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.change(screen.getByLabelText('Markdown-Beschreibung'), { target: { value: 'Text' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByText('Die Spalte hat ihr WIP-Limit erreicht.')).toBeInTheDocument()
    // Der Dialog bleibt stehen — sonst wäre die getippte Eingabe verloren.
    expect(onClose).not.toHaveBeenCalled()
    expect(screen.getByLabelText('Titel')).toHaveValue('Karte')
    expect(screen.getByLabelText('Markdown-Beschreibung')).toHaveValue('Text')
    // Ein zweiter Versuch ist möglich: „Anlegen" ist nach dem Fehlschlag wieder bedienbar.
    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeEnabled()
  })

  it('fällt ohne API-Kontext auf den eigenen Text zurück', async () => {
    const onSubmit = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByText('Anlegen fehlgeschlagen.')).toBeInTheDocument()
  })

  it('bindet einen Feldfehler zum Titel an das Titel-Feld', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, 'Ungültig', { title: 'Titel ist zu lang.' }, 'Ungültige Eingabe.'))
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByText('Titel ist zu lang.')).toBeInTheDocument()
    expect(screen.getByLabelText('Titel')).toHaveAttribute('aria-invalid', 'true')
  })

  it('bindet einen Feldfehler zum Kürzel an das Kürzel-Feld', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, 'Konflikt', { shortcode: 'Kürzel „EP" ist vergeben.' }, 'Ungültige Eingabe.'))
    render(
      <NewCardModal open epicOnly columnName="" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Epic' } })
    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'EP' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByText('Kürzel „EP" ist vergeben.')).toBeInTheDocument()
    expect(screen.getByLabelText('Kürzel')).toHaveAttribute('aria-invalid', 'true')
  })

  it('bindet einen Feldfehler zu den Abhängigkeiten an das Abhängigkeiten-Feld', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, 'Ungültig', { dependencies: 'Karte #99 gibt es nicht.' }, 'Ungültige Eingabe.'))
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.change(screen.getByLabelText('Abhängig von'), { target: { value: '99' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(await screen.findByText('Karte #99 gibt es nicht.')).toBeInTheDocument()
  })

  /**
   * Ein Feldfehler ohne Feld im Dialog verschwände sonst spurlos: Der Nutzer sähe nur die
   * allgemeine Meldung und wüsste nicht, was der Server konkret beanstandet.
   */
  it('hängt Feldfehler ohne eigenes Feld an die allgemeine Meldung an', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, 'Ungültig', { dueDate: 'Fälligkeit liegt in der Vergangenheit.' }, 'Ungültige Eingabe.'))
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))

    expect(
      await screen.findByText(/Ungültige Eingabe\..*Fälligkeit liegt in der Vergangenheit\./),
    ).toBeInTheDocument()
  })

  it('räumt die Titel-Feldmeldung bei einer neuen Eingabe weg', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, 'Ungültig', { title: 'Titel ist zu lang.' }, 'Ungültige Eingabe.'))
    render(
      <NewCardModal open columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    expect(await screen.findByText('Titel ist zu lang.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Kartentitel' } })

    expect(screen.queryByText('Titel ist zu lang.')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Titel')).toHaveAttribute('aria-invalid', 'false')
  })

  // Der schlanke Zweig (Idee/Vorhaben) hat sein eigenes Titel-Feld — die Feldmeldung muss auch
  // dort ankommen und beim Tippen wieder verschwinden, nicht nur in der gemeinsamen Feldbasis.
  it('bindet und räumt die Titel-Feldmeldung auch im schlanken Zweig', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, 'Ungültig', { title: 'Titel ist zu lang.' }, 'Ungültige Eingabe.'))
    render(
      <NewCardModal open ideaOnly columnName="" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Idee' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    expect(await screen.findByText('Titel ist zu lang.')).toBeInTheDocument()
    expect(screen.getByLabelText('Titel')).toHaveAttribute('aria-invalid', 'true')

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Ideentitel' } })

    expect(screen.queryByText('Titel ist zu lang.')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Titel')).toHaveAttribute('aria-invalid', 'false')
  })

  it('räumt die Kürzel-Feldmeldung bei einer neuen Eingabe weg', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(409, 'Konflikt', { shortcode: 'Kürzel vergeben.' }, 'Ungültige Eingabe.'))
    render(
      <NewCardModal open epicOnly columnName="" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Epic' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    expect(await screen.findByText('Kürzel vergeben.')).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Kürzel'), { target: { value: 'EPX' } })

    expect(screen.queryByText('Kürzel vergeben.')).not.toBeInTheDocument()
  })

  it('öffnet nach einem Fehlschlag wieder fehlerfrei', async () => {
    const onSubmit = vi
      .fn()
      .mockRejectedValue(new ApiError(400, 'Ungültig', { title: 'Titel ist zu lang.' }, 'Ungültige Eingabe.'))
    const props = { columnName: 'Backlog', epics: [], onClose: vi.fn(), onSubmit }
    const { rerender } = render(<NewCardModal open {...props} />)

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Karte' } })
    fireEvent.click(screen.getByRole('button', { name: 'Anlegen' }))
    expect(await screen.findByText('Titel ist zu lang.')).toBeInTheDocument()

    rerender(<NewCardModal open={false} {...props} />)
    rerender(<NewCardModal open {...props} />)

    expect(screen.queryByText('Titel ist zu lang.')).not.toBeInTheDocument()
    expect(screen.queryByText('Ungültige Eingabe.')).not.toBeInTheDocument()
  })

  it('legt an der Grenze noch an und meldet nichts', () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(
      <NewCardModal open ideaOnly columnName="Backlog" epics={[]} onClose={vi.fn()} onSubmit={onSubmit} />,
    )

    fireEvent.change(screen.getByLabelText('Titel'), { target: { value: 'Titel' } })
    fireEvent.change(screen.getByLabelText('Beschreibung'), {
      target: { value: 'a'.repeat(MAX_TEXT_LENGTH - 1) },
    })

    expect(screen.queryByText(/\/ 50\.000 Zeichen/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Anlegen' })).toBeEnabled()
  })
})
