import { screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { Card } from '../../api/cards'
import { commentsApi, type Comment } from '../../api/comments'
import type { KitNamenZustand } from '../../api/kitNamen'
import type { Label } from '../../api/labels'
import { formatierer } from '../../i18n/format'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { KIT_NAMEN } from '../../test/kitNamen'
import { AnbieterHinweis, beimAnbieterSeit } from './AnbieterHinweis'

vi.mock('../../api/comments', () => ({ commentsApi: { list: vi.fn() } }))
const kitNamen = vi.hoisted(() => ({ zustand: null as KitNamenZustand | null }))
vi.mock('../../api/kitNamen', async (original) => ({
  ...(await original<typeof import('../../api/kitNamen')>()),
  useKitNamen: (): KitNamenZustand => kitNamen.zustand ?? { status: 'fertig', daten: KIT_NAMEN },
}))

const liste = commentsApi.list as unknown as ReturnType<typeof vi.fn>

const SEIT = '2026-11-22T10:15:00Z'
const ANBIETER: Label = { id: 7, boardId: 1, name: 'kit:provider', color: '#888888', countOnEpicTile: false }
const ANDERES: Label = { id: 8, boardId: 1, name: 'kit:approval', color: '#888888', countOnEpicTile: false }

const card: Card = {
  id: 100, boardId: 1, columnId: 10, number: 41, title: 'Export als CSV', description: null, excerpt: null,
  positionInColumn: 0, archived: false, movedToDoneAt: null, dependencies: [],
  type: 'CARD', parentId: null, shortcode: null, assignees: [], dueDate: null, labels: [7],
  derivedFrom: null, status: null, canSetStatus: false, herkunft: null,
}

function kommentar(id: number, body: string, createdAt = '2026-11-22T10:00:00Z'): Comment {
  return { id, cardId: 100, authorUserId: 1, authorName: 'Rechenzentrum', body, createdAt, updatedAt: createdAt }
}

beforeEach(() => {
  liste.mockReset()
  kitNamen.zustand = null
})

describe('beimAnbieterSeit', () => {
  it('liest Seit aus dem jüngsten Kommentar mit Kopf „Beim Anbieter“, gleich in welcher Sprache', () => {
    expect(
      beimAnbieterSeit([
        kommentar(1, '## Beim Anbieter\nSeit: 2026-11-20T08:00:00Z', '2026-11-20T08:00:00Z'),
        kommentar(3, '## With the provider\nSince: 2026-11-22T10:15:00Z', '2026-11-22T10:15:00Z'),
        kommentar(2, 'Ein gewöhnlicher Kommentar\nSeit: 2026-11-23T00:00:00Z', '2026-11-23T00:00:00Z'),
      ]),
    ).toBe('2026-11-22T10:15:00Z')
  })

  it('nimmt den jüngsten Kommentar auch, wenn ein älterer nach ihm steht', () => {
    expect(
      beimAnbieterSeit([
        kommentar(3, '## Beim Anbieter\nSeit: 2026-11-22T10:15:00Z', '2026-11-22T10:15:00Z'),
        kommentar(1, '## Beim Anbieter\nSeit: 2026-11-20T08:00:00Z', '2026-11-20T08:00:00Z'),
      ]),
    ).toBe('2026-11-22T10:15:00Z')
  })

  it('liefert null ohne Kommentar mit Kopf, ohne Zeile Seit oder bei einem unlesbaren Zeitpunkt', () => {
    expect(beimAnbieterSeit([])).toBeNull()
    expect(beimAnbieterSeit([kommentar(1, 'Seit: 2026-11-22T10:15:00Z')])).toBeNull()
    expect(beimAnbieterSeit([kommentar(1, '## Beim Anbieter\nText')])).toBeNull()
    expect(beimAnbieterSeit([kommentar(1, '## Beim Anbieter\nSeit: morgen')])).toBeNull()
  })
})

describe('AnbieterHinweis', () => {
  it('Szenario: Reparaturen bleiben unsichtbar', async () => {
    // Angenommen eine Prüfung ist beim Anbieter rot und eine Reparaturkarte ist entstanden: Die
    // Reparaturkarte und die rote Prüfmeldung liefert der Server dem Kunden nicht (#2211, #2214);
    // an der betroffenen Karte steht nur der Kommentar „Beim Anbieter“.
    liste.mockResolvedValue([kommentar(5, `## Beim Anbieter\nSeit: ${SEIT}`, SEIT)])

    renderMitSprache(<AnbieterHinweis card={card} boardLabels={[ANBIETER]} />)

    const zeit = formatierer('de').datumUhrzeit(SEIT)
    expect(await screen.findByText(`beim Anbieter in Arbeit seit ${zeit}`)).toBeInTheDocument()
    expect(liste).toHaveBeenCalledWith(100)
    expect(screen.queryByText(/Reparatur|Prüfung|rot/)).not.toBeInTheDocument()
  })

  it('zeigt den Hinweis englisch, wenn die Oberfläche englisch ist', async () => {
    liste.mockResolvedValue([kommentar(5, `## With the provider\nSince: ${SEIT}`, SEIT)])

    renderMitSprache(<AnbieterHinweis card={card} boardLabels={[ANBIETER]} />, 'en')

    const zeit = formatierer('en').datumUhrzeit(SEIT)
    expect(await screen.findByText(`in progress with the provider since ${zeit}`)).toBeInTheDocument()
  })

  it('zeigt den Hinweis ohne Zeitpunkt, solange kein Kommentar ihn nennt', async () => {
    liste.mockResolvedValue([kommentar(5, 'Ein Kommentar')])

    renderMitSprache(<AnbieterHinweis card={card} boardLabels={[ANBIETER]} />)

    expect(await screen.findByText('beim Anbieter in Arbeit')).toBeInTheDocument()
  })

  it('zeigt den Hinweis ohne Zeitpunkt, wenn die Kommentare nicht zu laden sind', async () => {
    liste.mockRejectedValue(new Error('weg'))

    renderMitSprache(<AnbieterHinweis card={card} boardLabels={[ANBIETER]} />)

    expect(await screen.findByText('beim Anbieter in Arbeit')).toBeInTheDocument()
  })

  it('zeigt nichts und lädt nichts an einer Karte ohne Label der Sache Anbieter', async () => {
    renderMitSprache(<AnbieterHinweis card={{ ...card, labels: [8] }} boardLabels={[ANBIETER, ANDERES]} />)

    await waitFor(() => expect(liste).not.toHaveBeenCalled())
    expect(screen.queryByText(/beim Anbieter/)).not.toBeInTheDocument()
  })

  it('zeigt nichts, solange die Namen des Kits nicht geladen sind', async () => {
    kitNamen.zustand = { status: 'laden' }

    renderMitSprache(<AnbieterHinweis card={card} boardLabels={[ANBIETER]} />)

    await waitFor(() => expect(liste).not.toHaveBeenCalled())
    expect(screen.queryByText(/beim Anbieter/)).not.toBeInTheDocument()
  })
})
