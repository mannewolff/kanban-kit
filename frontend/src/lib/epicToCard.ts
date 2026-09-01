import type { Card } from '../api/cards'
import type { Epic } from '../api/epics'

export function epicToCard(epic: Epic, boardId: number): Card {
  return {
    id: epic.id, boardId, columnId: 0, number: epic.number, title: epic.title,
    description: epic.description, positionInColumn: 0, archived: false, ideaStored: false, movedToDoneAt: null,
    dependencies: [], type: 'EPIC', parentId: null, shortcode: epic.shortcode, assignees: [], dueDate: null, labels: [],
    // Vorhaben tragen keine Herkunft (Issue #607): der Anlege-Endpunkt lehnt sie fuer EPIC ab.
    derivedFrom: null,
  }
}
