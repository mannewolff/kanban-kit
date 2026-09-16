import { describe, expect, it } from 'vitest'
import { buildNavItems, type BoardContext, type NavParams } from './navItems'

const board: BoardContext = { id: 1, name: 'B', projectId: 5 }

/** Titel der Blöcke in ihrer Reihenfolge. */
const bloecke = (params: NavParams) => buildNavItems(params).map((b) => b.label)

/** Beschriftungen eines Blocks; Projekt-Block über das Präfix „Projekt". */
const eintraege = (params: NavParams, titel: string) =>
  buildNavItems(params)
    .find((b) => b.label === titel || (titel === 'Projekt' && b.label.startsWith('Projekt')))
    ?.children.map((c) => c.label) ?? []

/** Alle Einträge über alle Blöcke, zum Suchen nach einem Link. */
const link = (params: NavParams, label: string) =>
  buildNavItems(params)
    .flatMap((b) => b.children)
    .find((c) => c.label === label)

describe('buildNavItems Gliederung nach dem Leitstand-Entwurf (#978)', () => {
  it('führt bei offenem Board die Blöcke Projekt, Übersicht und Verwaltung in dieser Reihenfolge', () => {
    expect(bloecke({ board, projectName: 'kanban-kit', boardCount: 2 })).toEqual([
      'Projekt kanban-kit',
      'Übersicht',
      'Verwaltung',
    ])
  })

  it('nennt den Projekt-Block ohne bekannten Namen schlicht „Projekt"', () => {
    expect(bloecke({ board })[0]).toBe('Projekt')
  })

  it('ordnet den Projekt-Block wie der Entwurf: Leitstand, Board, Liste, Vorhaben, Ideen, Nachtläufe', () => {
    expect(eintraege({ board, canViewNightRun: true }, 'Projekt')).toEqual([
      'Leitstand',
      'Board',
      'Liste',
      'Vorhaben',
      'Ideen',
      'Nachtläufe',
    ])
  })

  it('führt ohne offenes Board im Projekt-Block nur die projektweiten Einträge', () => {
    expect(eintraege({ board: null, projectId: 7, canViewNightRun: true }, 'Projekt')).toEqual(['Ideen', 'Nachtläufe'])
  })

  it('lässt den Projekt-Block ohne Projekt-Kontext weg', () => {
    expect(bloecke({ board: null })).toEqual(['Übersicht', 'Verwaltung'])
  })

  it('verlinkt die Ansichten des Boards auf ihre Routen', () => {
    expect(link({ board }, 'Leitstand')?.path).toBe('/boards/1/leitstand')
    expect(link({ board }, 'Board')?.path).toBe('/boards/1')
    expect(link({ board }, 'Liste')?.path).toBe('/boards/1/list')
    expect(link({ board }, 'Vorhaben')?.path).toBe('/boards/1/vorhaben')
  })
})

describe('buildNavItems Übersicht', () => {
  it('blendet „Projekte" bei genau einem Projekt aus (Nicht-Admin)', () => {
    expect(eintraege({ board: null, projectCount: 1 }, 'Übersicht')).not.toContain('Projekte')
  })

  it('zeigt „Projekte" bei mehreren Projekten', () => {
    expect(eintraege({ board: null, projectCount: 2 }, 'Übersicht')).toContain('Projekte')
  })

  it('zeigt „Projekte" für System-Admins auch bei genau einem Projekt', () => {
    expect(eintraege({ board: null, projectCount: 1, isAdmin: true }, 'Übersicht')).toContain('Projekte')
  })

  it('zeigt „Projekte" solange die Anzahl unbekannt ist (kein Flackern)', () => {
    expect(eintraege({ board: null }, 'Übersicht')).toContain('Projekte')
  })

  it('blendet „Boards" bei genau einem Board aus (ohne Verwaltungsrecht)', () => {
    expect(eintraege({ board, boardCount: 1 }, 'Übersicht')).not.toContain('Boards')
  })

  it('zeigt „Boards" bei mehreren Boards nach „Projekte"', () => {
    expect(eintraege({ board, boardCount: 2, projectCount: 2 }, 'Übersicht')).toEqual(['Projekte', 'Boards'])
  })

  it('zeigt „Boards" trotz einem Board, wenn man Boards verwalten darf', () => {
    expect(eintraege({ board, boardCount: 1, canManageBoards: true }, 'Übersicht')).toContain('Boards')
  })

  it('verlinkt „Boards" auf die existierende Projekt-Route (nicht /projects/:id/boards)', () => {
    // Regression zu Issue #3: /projects/:id/boards ist in App.tsx keine registrierte Route.
    expect(link({ board, boardCount: 2 }, 'Boards')?.path).toBe(`/projects/${board.projectId}`)
  })

  it('lässt den Block weg, wenn es nichts zu wählen gibt', () => {
    expect(bloecke({ board, projectCount: 1, boardCount: 1 })).not.toContain('Übersicht')
  })
})

describe('buildNavItems Ideen-Link', () => {
  it('zeigt „Ideen" bei offenem Board (Projekt-Kontext aus dem Board)', () => {
    expect(link({ board }, 'Ideen')?.path).toBe(`/projects/${board.projectId}/ideas`)
  })

  it('zeigt „Ideen" auf einer Projekt-Route ohne offenes Board', () => {
    expect(link({ board: null, projectId: 7 }, 'Ideen')?.path).toBe('/projects/7/ideas')
  })

  it('blendet „Ideen" ohne Projekt-Kontext aus (kein Board, keine projectId)', () => {
    expect(link({ board: null }, 'Ideen')).toBeUndefined()
  })

  it('bevorzugt den Board-Projektkontext vor einer abweichenden projectId', () => {
    expect(link({ board, projectId: 99 }, 'Ideen')?.path).toBe(`/projects/${board.projectId}/ideas`)
  })
})

describe('buildNavItems Nachtläufe-Link', () => {
  // Reine Parameterprüfung: buildNavItems bekommt den fertigen Booleschen Wert. Wie er entsteht
  // (canManageProject, also auch für den Plattform-Admin), ist in der AppShell geprüft.
  it('zeigt „Nachtläufe" bei gesetztem Sichtbarkeitswert und offenem Board', () => {
    expect(link({ board, canViewNightRun: true }, 'Nachtläufe')?.path).toBe(`/projects/${board.projectId}/nachtlauf`)
  })

  it('zeigt „Nachtläufe" auf einer Projekt-Route ohne offenes Board', () => {
    expect(link({ board: null, projectId: 7, canViewNightRun: true }, 'Nachtläufe')?.path).toBe('/projects/7/nachtlauf')
  })

  it('blendet „Nachtläufe" ohne Projekt-Kontext aus', () => {
    expect(link({ board: null, canViewNightRun: true }, 'Nachtläufe')).toBeUndefined()
  })

  it('blendet „Nachtläufe" ohne gesetzten oder mit fehlendem Sichtbarkeitswert aus', () => {
    expect(link({ board, canViewNightRun: false }, 'Nachtläufe')).toBeUndefined()
    expect(link({ board }, 'Nachtläufe')).toBeUndefined()
  })
})

describe('buildNavItems Verwaltung', () => {
  it('führt „Rollen & Rechte" immer', () => {
    expect(link({ board: null }, 'Rollen & Rechte')?.path).toBe('/roles')
  })

  it('führt „Mitglieder" nur mit Projekt-Kontext und Verwaltungsrecht', () => {
    expect(link({ board, canManageMembers: true }, 'Mitglieder')?.path).toBe('/projects/5/members')
    expect(link({ board, canManageMembers: false }, 'Mitglieder')).toBeUndefined()
    expect(link({ board: null, canManageMembers: true }, 'Mitglieder')).toBeUndefined()
  })

  it('führt „Admin" nur für System-Admins, nach Mitglieder und Rollen', () => {
    expect(eintraege({ board, canManageMembers: true, isAdmin: true }, 'Verwaltung')).toEqual([
      'Mitglieder',
      'Rollen & Rechte',
      'Admin',
    ])
    expect(link({ board: null }, 'Admin')).toBeUndefined()
  })
})
