import { describe, expect, it } from 'vitest'
import { cardLocationCrumbs, cardLocationLabel, type CardLocation } from './cardLocation'

const board = { id: 2, name: 'Entwicklung', columnName: 'In Progress' }
const onBoard: CardLocation = { projectId: 9, projectName: 'IT-Bildungshaus', board }

describe('cardLocationCrumbs', () => {
  it('nennt Projekt, Board und Spalte und verlinkt Projekt und Board', () => {
    expect(cardLocationCrumbs(onBoard)).toEqual([
      { label: 'IT-Bildungshaus', to: '/projects/9' },
      { label: 'Entwicklung', to: '/boards/2' },
      { label: 'In Progress' },
    ])
  })

  it('kennzeichnet ein archiviertes Board und verlinkt es nicht', () => {
    expect(cardLocationCrumbs({ ...onBoard, board: { ...board, archived: true } })).toEqual([
      { label: 'IT-Bildungshaus', to: '/projects/9' },
      { label: 'Entwicklung (archiviert)' },
      { label: 'In Progress' },
    ])
  })

  it('lässt das Spaltensegment weg, wenn der Spaltenname nicht aufgelöst ist', () => {
    expect(cardLocationCrumbs({ ...onBoard, board: { ...board, columnName: null } })).toEqual([
      { label: 'IT-Bildungshaus', to: '/projects/9' },
      { label: 'Entwicklung', to: '/boards/2' },
    ])
  })

  it('fällt auf „Projekt“ zurück, solange der Projektname nicht geladen ist', () => {
    expect(cardLocationCrumbs({ ...onBoard, projectName: null })[0]).toEqual({
      label: 'Projekt',
      to: '/projects/9',
    })
  })
})

describe('cardLocationLabel', () => {
  it('fügt dieselben Segmente zu einer Zeile zusammen', () => {
    expect(cardLocationLabel(onBoard)).toBe('IT-Bildungshaus / Entwicklung / In Progress')
  })

  it('nennt stets Board und Spalte — es gibt kein Ortssegment „Ideen“ mehr (Issue #1202)', () => {
    expect(cardLocationLabel(onBoard)).not.toContain('Ideen')
    expect(cardLocationCrumbs(onBoard).map((c) => c.label)).toEqual([
      'IT-Bildungshaus',
      'Entwicklung',
      'In Progress',
    ])
  })
})
