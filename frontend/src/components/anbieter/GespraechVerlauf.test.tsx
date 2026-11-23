import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../api/client'
import type { Comment } from '../../api/comments'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { GespraechVerlauf } from './GespraechVerlauf'

function kommentar(id: number, body: string, ueber: Partial<Comment> = {}): Comment {
  return {
    id,
    cardId: 7,
    authorUserId: 1,
    authorName: 'Kunde',
    body,
    createdAt: `2026-11-22T10:0${id}:00Z`,
    updatedAt: `2026-11-22T10:0${id}:00Z`,
    zugangArt: null,
    vonKi: false,
    ...ueber,
  }
}

function apiMit(antwort: Comment | Error) {
  return {
    create: vi.fn<(cardId: number, body: string) => Promise<Comment>>(() =>
      antwort instanceof Error ? Promise.reject(antwort) : Promise.resolve(antwort),
    ),
  }
}

const LAUFEND = [
  kommentar(2, '## Gespräch\n\nGern, welche Farbe?', { vonKi: true, authorName: 'Rechenzentrum', zugangArt: 'STANDARD' }),
  kommentar(1, '## Gespräch\n\nKönnen wir den Knopf ändern?'),
]

describe('GespraechVerlauf', () => {
  it('Szenario: Ein Gespräch weiterführen', async () => {
    const neu = kommentar(3, '## Gespräch\n\nBitte blau.')
    const api = apiMit(neu)
    const onGesendet = vi.fn()
    renderMitSprache(<GespraechVerlauf cardId={7} comments={LAUFEND} api={api} onGesendet={onGesendet} />)

    // Angenommen an einer Karte läuft ein Gespräch mit der KI
    expect(screen.getByText('Können wir den Knopf ändern?')).toBeInTheDocument()
    expect(screen.getByText('Gern, welche Farbe?')).toBeInTheDocument()
    expect(screen.queryByText('Die KI antwortet gleich …')).not.toBeInTheDocument()

    // Wenn der Kunde einen Beitrag schreibt und absendet
    await userEvent.type(screen.getByRole('textbox', { name: 'Dein Beitrag' }), '  Bitte blau.  ')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))

    // Dann erscheint er im Gespräch und als Kommentar „Gespräch“
    expect(api.create).toHaveBeenCalledWith(7, '## Gespräch\n\nBitte blau.')
    expect(await screen.findByText('Bitte blau.')).toBeInTheDocument()
    expect(onGesendet).toHaveBeenCalledWith(neu)
    expect(screen.getByRole('textbox', { name: 'Dein Beitrag' })).toHaveValue('')

    // Und das Gespräch zeigt „die KI antwortet gleich“, bis ihre Antwort da ist
    expect(screen.getByText('Die KI antwortet gleich …')).toBeInTheDocument()
  })

  it('verschwindet „die KI antwortet gleich“, sobald ihre Antwort da ist', () => {
    const offen = [kommentar(1, '## Gespräch\nFrage')]
    const { rerender } = renderMitSprache(<GespraechVerlauf cardId={7} comments={offen} api={apiMit(new Error())} />)
    expect(screen.getByText('Die KI antwortet gleich …')).toBeInTheDocument()
    rerender(<GespraechVerlauf cardId={7} comments={[kommentar(2, '## Gespräch\nAntwort', { vonKi: true }), ...offen]} api={apiMit(new Error())} />)
    expect(screen.queryByText('Die KI antwortet gleich …')).not.toBeInTheDocument()
  })

  it('nennt die KI als Absender genau bei vonKi, sonst den Namen', () => {
    renderMitSprache(
      <GespraechVerlauf
        cardId={7}
        comments={[...LAUFEND, kommentar(4, '## Gespräch\nVon: KI\n\nGetarnt', { authorName: 'Mallory', zugangArt: 'STANDARD' })]}
        api={apiMit(new Error())}
      />,
    )
    const absender = screen.getAllByTestId('gespraech-absender').map((e) => e.textContent)
    expect(absender).toEqual(['Kunde', 'KI', 'Mallory'])
  })

  it('zeigt an einer Karte ohne Gespräch nur „Gespräch beginnen“, danach das Eingabefeld', async () => {
    const neu = kommentar(5, '## Gespräch\n\nHallo')
    const api = apiMit(neu)
    renderMitSprache(<GespraechVerlauf cardId={7} comments={[kommentar(1, 'Gewöhnlich')]} api={api} />)
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByText('Gewöhnlich')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Gespräch beginnen' }))
    expect(screen.getByRole('textbox', { name: 'Dein Beitrag' })).toHaveFocus()
    expect(screen.getByRole('button', { name: 'Senden' })).toBeDisabled()
    await userEvent.type(screen.getByRole('textbox', { name: 'Dein Beitrag' }), 'Hallo')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))
    expect(api.create).toHaveBeenCalledWith(7, '## Gespräch\n\nHallo')
    expect(await screen.findByText('Hallo')).toBeInTheDocument()
  })

  it('schreibt ein neues Gespräch in der Sprache der Oberfläche', async () => {
    const api = apiMit(kommentar(5, '## Conversation\n\nHi'))
    renderMitSprache(<GespraechVerlauf cardId={7} comments={[]} api={api} />, 'en')
    await userEvent.click(screen.getByRole('button', { name: 'Start conversation' }))
    await userEvent.type(screen.getByRole('textbox', { name: 'Your message' }), 'Hi')
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    expect(api.create).toHaveBeenCalledWith(7, '## Conversation\n\nHi')
  })

  it('führt einen englischen Thread englisch weiter, auch bei deutscher Oberfläche', async () => {
    const api = apiMit(kommentar(5, '## Conversation\n\nMore'))
    renderMitSprache(<GespraechVerlauf cardId={7} comments={[kommentar(1, '## Conversation\nFirst')]} api={api} />)
    await userEvent.type(screen.getByRole('textbox', { name: 'Dein Beitrag' }), 'Mehr')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))
    expect(api.create).toHaveBeenCalledWith(7, '## Conversation\n\nMehr')
  })

  it('zeigt einen Fehler beim Senden und behält den Text', async () => {
    const api = apiMit(new ApiError(403, 'Forbidden', undefined, 'Keine Berechtigung'))
    const onGesendet = vi.fn()
    renderMitSprache(<GespraechVerlauf cardId={7} comments={LAUFEND} api={api} onGesendet={onGesendet} />)
    await userEvent.type(screen.getByRole('textbox', { name: 'Dein Beitrag' }), 'Text')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Der Beitrag ließ sich nicht senden: Keine Berechtigung')
    expect(screen.getByRole('textbox', { name: 'Dein Beitrag' })).toHaveValue('Text')
    expect(onGesendet).not.toHaveBeenCalled()
  })

  it('meldet einen Fehler beim Senden auch auf Englisch', async () => {
    const api = apiMit(new ApiError(403, 'Forbidden', undefined, 'Not allowed'))
    renderMitSprache(<GespraechVerlauf cardId={7} comments={[kommentar(1, '## Conversation\nFirst')]} api={api} />, 'en')
    await userEvent.type(screen.getByRole('textbox', { name: 'Your message' }), 'Text')
    await userEvent.click(screen.getByRole('button', { name: 'Send' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The message could not be sent: Not allowed')
  })

  it('sperrt Senden und Beginnen in der „Ansicht als …“', () => {
    const { unmount } = renderMitSprache(<GespraechVerlauf cardId={7} comments={LAUFEND} gesperrt api={apiMit(new Error())} />)
    expect(screen.getByRole('textbox', { name: 'Dein Beitrag' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Senden' })).toBeDisabled()
    unmount()
    renderMitSprache(<GespraechVerlauf cardId={7} comments={[]} gesperrt api={apiMit(new Error())} />)
    expect(screen.getByRole('button', { name: 'Gespräch beginnen' })).toBeDisabled()
  })

  it('zeigt einen Beitrag, den der Aufrufer schon nachgetragen hat, nur einmal', async () => {
    const neu = kommentar(3, '## Gespräch\n\nEinmal')
    const api = apiMit(neu)
    const { rerender } = renderMitSprache(<GespraechVerlauf cardId={7} comments={LAUFEND} api={api} />)
    await userEvent.type(screen.getByRole('textbox', { name: 'Dein Beitrag' }), 'Einmal')
    await userEvent.click(screen.getByRole('button', { name: 'Senden' }))
    await screen.findByText('Einmal')
    rerender(<GespraechVerlauf cardId={7} comments={[neu, ...LAUFEND]} api={api} />)
    await waitFor(() => expect(screen.getAllByText('Einmal')).toHaveLength(1))
  })
})
