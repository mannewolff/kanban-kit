import { describe, expect, it } from 'vitest'
import { buildNavItems, type BoardContext, type NavParams } from './navItems'

const board: BoardContext = { id: 1, name: 'B', projectId: 5 }

const topLabels = (params: NavParams) => buildNavItems(params).map((n) => n.label)

const groupChildren = (params: NavParams) => {
  const group = buildNavItems(params).find((n) => n.label === 'B')
  return group?.kind === 'group' ? group.children.map((c) => c.label) : []
}

describe('buildNavItems Sichtbarkeit', () => {
  it('blendet „Projekte" bei genau einem Projekt aus (Nicht-Admin)', () => {
    expect(topLabels({ board: null, projectCount: 1 })).not.toContain('Projekte')
  })

  it('zeigt „Projekte" bei mehreren Projekten', () => {
    expect(topLabels({ board: null, projectCount: 2 })).toContain('Projekte')
  })

  it('zeigt „Projekte" für System-Admins auch bei genau einem Projekt', () => {
    expect(topLabels({ board: null, projectCount: 1, isAdmin: true })).toContain('Projekte')
  })

  it('zeigt „Projekte" solange die Anzahl unbekannt ist (kein Flackern)', () => {
    expect(topLabels({ board: null })).toContain('Projekte')
  })

  it('blendet „Boards" bei genau einem Board aus (ohne Verwaltungsrecht)', () => {
    expect(topLabels({ board, boardCount: 1 })).not.toContain('Boards')
  })

  it('zeigt „Boards" als Top-Level-Eintrag bei mehreren Boards', () => {
    expect(topLabels({ board, boardCount: 2 })).toContain('Boards')
  })

  it('zeigt „Boards" trotz einem Board, wenn man Boards verwalten darf', () => {
    expect(topLabels({ board, boardCount: 1, canManageBoards: true })).toContain('Boards')
  })

  it('hängt „Boards" nach „Projekte" und vor die Board-Gruppe', () => {
    const labels = topLabels({ board, projectCount: 2, boardCount: 2 })
    expect(labels.indexOf('Boards')).toBeGreaterThan(labels.indexOf('Projekte'))
    expect(labels.indexOf('Boards')).toBeLessThan(labels.indexOf('B'))
  })

  it('führt in der Board-Gruppe nur die vier Ansichten (kein „Boards")', () => {
    expect(groupChildren({ board, boardCount: 2 })).toEqual(['Board', 'Liste', 'Epics', 'Dashboard'])
  })

  it('verlinkt „Boards" auf die existierende Projekt-Route (nicht /projects/:id/boards)', () => {
    // Regression zu Issue #3: /projects/:id/boards ist in App.tsx keine registrierte Route
    // (nur /projects/:id) — der falsche Pfad führte zu einer leeren Seite.
    const boardsLink = buildNavItems({ board, boardCount: 2 }).find((n) => n.label === 'Boards')
    expect(boardsLink?.kind === 'link' ? boardsLink.path : undefined).toBe(`/projects/${board.projectId}`)
  })

  it('verlinkt „Dashboard" auf die Board-Dashboard-Route', () => {
    expect(groupChildren({ board })).toContain('Dashboard')
    const group = buildNavItems({ board }).find((n) => n.label === 'B')
    const link = group?.kind === 'group' ? group.children.find((c) => c.label === 'Dashboard') : undefined
    expect(link?.path).toBe(`/boards/${board.id}/dashboard`)
  })
})

describe('buildNavItems Ideen-Link', () => {
  const ideasLink = (params: NavParams) => {
    const node = buildNavItems(params).find((n) => n.label === 'Ideen')
    return node?.kind === 'link' ? node : undefined
  }

  it('zeigt „Ideen" bei offenem Board (Projekt-Kontext aus dem Board)', () => {
    expect(ideasLink({ board })?.path).toBe(`/projects/${board.projectId}/ideas`)
  })

  it('zeigt „Ideen" auf einer Projekt-Route ohne offenes Board', () => {
    expect(ideasLink({ board: null, projectId: 7 })?.path).toBe('/projects/7/ideas')
  })

  it('blendet „Ideen" ohne Projekt-Kontext aus (kein Board, keine projectId)', () => {
    expect(ideasLink({ board: null })).toBeUndefined()
  })

  it('bevorzugt den Board-Projektkontext vor einer abweichenden projectId', () => {
    // Bei offenem Board hat board.projectId Vorrang — der Ideen-Link bleibt beim Board-Projekt.
    expect(ideasLink({ board, projectId: 99 })?.path).toBe(`/projects/${board.projectId}/ideas`)
  })

  it('hängt „Ideen" als Geschwister von „Boards" (nach Boards, vor die Board-Gruppe)', () => {
    const labels = topLabels({ board, boardCount: 2 })
    expect(labels.indexOf('Ideen')).toBeGreaterThan(labels.indexOf('Boards'))
    expect(labels.indexOf('Ideen')).toBeLessThan(labels.indexOf('B'))
  })
})
