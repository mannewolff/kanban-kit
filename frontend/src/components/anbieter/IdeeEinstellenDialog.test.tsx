import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { Board } from '../../api/boards'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { IdeeEinstellenDialog } from './IdeeEinstellenDialog'

vi.mock('../../auth/AuthContext', () => ({ useAuth: () => ({ vorschau: null }) }))

/** Zwei Boards; die Ideenspalte ist die Backlog-Spalte, nicht die erste nach Position. */
const BOARDS: Board[] = [
  {
    id: 3,
    projectId: 5,
    name: 'Produkt',
    createdAt: '',
    columns: [
      { id: 31, name: 'Ready', position: 0, wipLimit: null },
      { id: 30, name: 'Backlog', position: 1, wipLimit: null },
    ],
  },
  { id: 4, projectId: 5, name: 'Website', createdAt: '', columns: [{ id: 40, name: 'Backlog', position: 0, wipLimit: null }] },
]

const anhang = (id: number, filename: string) => ({ id, cardId: 77, filename, contentType: 'application/pdf', size: 1, createdAt: '' })

function zeigen(
  upload = vi.fn((_karte: number, datei: File) => Promise.resolve(anhang(1, datei.name))),
  create = vi.fn().mockResolvedValue({ id: 77 }),
  boards: Board[] = BOARDS,
  sprache: 'de' | 'en' = 'de',
) {
  const onClose = vi.fn()
  const onEingestellt = vi.fn()
  renderMitSprache(
    <IdeeEinstellenDialog
      boards={boards}
      onClose={onClose}
      onEingestellt={onEingestellt}
      cardsApi={{ create }}
      attachmentsApi={{ upload, fetchBlob: vi.fn() }}
    />,
    sprache,
  )
  return { upload, create, onClose, onEingestellt }
}

const dateien = () => [new File(['a'], 'skizze.pdf'), new File(['b'], 'liste.pdf')]

function ausfuellen(text: string, files: File[] = []) {
  fireEvent.change(screen.getByLabelText('Deine Idee'), { target: { value: text } })
  if (files.length > 0) fireEvent.change(screen.getByLabelText('Dateien anhängen'), { target: { files } })
}

describe('IdeeEinstellenDialog', () => {
  it('legt die Idee in der Ideenspalte an und lädt beide Dateien an die neue Karte', async () => {
    const { create, upload, onClose, onEingestellt } = zeigen()
    const [a, b] = dateien()
    ausfuellen('  Export als CSV  ', [a, b])

    expect(screen.getByText('skizze.pdf')).toBeInTheDocument()
    expect(screen.getByText('liste.pdf')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Einstellen' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(create).toHaveBeenCalledTimes(1)
    expect(create).toHaveBeenCalledWith(3, 30, '[Idea] Export als CSV')
    expect(upload).toHaveBeenCalledTimes(2)
    expect(upload).toHaveBeenNthCalledWith(1, 77, a)
    expect(upload).toHaveBeenNthCalledWith(2, 77, b)
    expect(onEingestellt).toHaveBeenCalledTimes(1)
  })

  it('lässt die Karte stehen und nennt die Datei, deren Hochladen scheitert', async () => {
    const upload = vi.fn((_karte: number, datei: File) =>
      datei.name === 'liste.pdf' ? Promise.reject(new Error('boom')) : Promise.resolve(anhang(1, datei.name)),
    )
    const { create, onClose, onEingestellt } = zeigen(upload)
    ausfuellen('Export als CSV', dateien())
    fireEvent.click(screen.getByRole('button', { name: 'Einstellen' }))

    expect(
      await screen.findByText(
        'Die Idee ist angelegt, aber diese Dateien ließen sich nicht anhängen: liste.pdf. Hänge sie an der Karte erneut an.',
      ),
    ).toBeInTheDocument()
    expect(upload).toHaveBeenCalledTimes(2)
    expect(create).toHaveBeenCalledTimes(1)
    expect(onClose).not.toHaveBeenCalled()
    // Die Karte steht — die Startseite erfährt davon, ein zweites Einstellen gibt es nicht.
    expect(onEingestellt).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('button', { name: 'Einstellen' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
    expect(onClose).toHaveBeenCalled()
  })

  it('stellt eine Idee ohne Dateien ein', async () => {
    const { create, upload, onClose } = zeigen()
    ausfuellen('Nur Text')
    fireEvent.click(screen.getByRole('button', { name: 'Einstellen' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(create).toHaveBeenCalledWith(3, 30, '[Idea] Nur Text')
    expect(upload).not.toHaveBeenCalled()
  })

  it('legt die Idee im gewählten Board an', async () => {
    const { create, onClose } = zeigen()
    fireEvent.change(screen.getByLabelText('Board'), { target: { value: '4' } })
    ausfuellen('Neue Startseite')
    fireEvent.click(screen.getByRole('button', { name: 'Einstellen' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(create).toHaveBeenCalledWith(4, 40, '[Idea] Neue Startseite')
  })

  it('zeigt bei nur einem Board keine Auswahl', () => {
    zeigen(undefined, undefined, [BOARDS[1]])
    expect(screen.queryByLabelText('Board')).not.toBeInTheDocument()
  })

  it('nimmt eine gewählte Datei wieder heraus', async () => {
    const { upload, onClose } = zeigen()
    const [a, b] = dateien()
    ausfuellen('Export', [a, b])
    fireEvent.click(screen.getByRole('button', { name: 'skizze.pdf entfernen' }))
    expect(screen.queryByText('skizze.pdf')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Einstellen' }))

    await waitFor(() => expect(onClose).toHaveBeenCalled())
    expect(upload).toHaveBeenCalledTimes(1)
    expect(upload).toHaveBeenCalledWith(77, b)
  })

  it('sperrt das Einstellen ohne Text und bei zu langem Text', () => {
    zeigen()
    expect(screen.getByRole('button', { name: 'Einstellen' })).toBeDisabled()
    ausfuellen('   ')
    expect(screen.getByRole('button', { name: 'Einstellen' })).toBeDisabled()
    ausfuellen('x'.repeat(300))
    expect(screen.getByRole('button', { name: 'Einstellen' })).toBeDisabled()
    expect(screen.getByText('300 von höchstens 293 Zeichen')).toBeInTheDocument()
  })

  it('meldet ein gescheitertes Anlegen und lädt nichts hoch', async () => {
    const { upload, onClose, onEingestellt } = zeigen(undefined, vi.fn().mockRejectedValue(new Error('boom')))
    ausfuellen('Export', dateien())
    fireEvent.click(screen.getByRole('button', { name: 'Einstellen' }))

    expect(await screen.findByText('Die Idee ließ sich nicht anlegen.')).toBeInTheDocument()
    expect(upload).not.toHaveBeenCalled()
    expect(onClose).not.toHaveBeenCalled()
    expect(onEingestellt).not.toHaveBeenCalled()
    // Der Text bleibt stehen, ein zweiter Versuch ist möglich.
    expect(screen.getByLabelText('Deine Idee')).toHaveValue('Export')
    expect(screen.getByRole('button', { name: 'Einstellen' })).toBeEnabled()
  })

  it('meldet ein Board ohne Spalte, ohne etwas anzulegen', async () => {
    const leer: Board = { ...BOARDS[1], columns: [] }
    const { create } = zeigen(undefined, undefined, [leer])
    ausfuellen('Export')
    fireEvent.click(screen.getByRole('button', { name: 'Einstellen' }))

    expect(await screen.findByText('Das Board hat keine Spalte, in die die Idee fallen kann.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('meldet ohne Board, dass die Idee nirgends hinfallen kann', async () => {
    const { create } = zeigen(undefined, undefined, [])
    ausfuellen('Export')
    fireEvent.click(screen.getByRole('button', { name: 'Einstellen' }))

    expect(await screen.findByText('Das Board hat keine Spalte, in die die Idee fallen kann.')).toBeInTheDocument()
    expect(create).not.toHaveBeenCalled()
  })

  it('übergeht eine Dateiauswahl ohne Dateien', () => {
    zeigen()
    fireEvent.change(screen.getByLabelText('Dateien anhängen'), { target: { files: null } })
    expect(screen.queryByRole('button', { name: /entfernen$/ })).not.toBeInTheDocument()
  })

  it('bricht ohne Anlegen ab', () => {
    const { create, onClose } = zeigen()
    fireEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))
    expect(onClose).toHaveBeenCalled()
    expect(create).not.toHaveBeenCalled()
  })

  it('spricht englisch', () => {
    zeigen(undefined, undefined, BOARDS, 'en')
    expect(screen.getByRole('heading', { name: 'Submit an idea' })).toBeInTheDocument()
    expect(screen.getByLabelText('Your idea')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit' })).toBeInTheDocument()
  })

  it('nennt die nicht angehängte Datei auch englisch', async () => {
    zeigen(vi.fn().mockRejectedValue(new Error('boom')), undefined, BOARDS, 'en')
    fireEvent.change(screen.getByLabelText('Your idea'), { target: { value: 'x'.repeat(300) } })
    expect(screen.getByText('300 of at most 293 characters')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Your idea'), { target: { value: 'CSV export' } })
    fireEvent.change(screen.getByLabelText('Attach files'), { target: { files: [new File(['a'], 'sketch.pdf')] } })
    expect(screen.getByRole('button', { name: 'Remove sketch.pdf' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Submit' }))

    expect(
      await screen.findByText(
        'The idea has been created, but these files could not be attached: sketch.pdf. Attach them to the card again.',
      ),
    ).toBeInTheDocument()
  })
})
