import { describe, expect, it } from 'vitest'
import { buildNavItems, navKontext, type BoardContext, type NavKontextParams, type NavParams } from './navItems'

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

  it('ordnet den Projekt-Block wie der Entwurf: Leitstand, Board, Liste, Vorhaben, Runs', () => {
    expect(eintraege({ board, canViewNightRun: true }, 'Projekt')).toEqual([
      'Leitstand',
      'Board',
      'Liste',
      'Vorhaben',
      'Runner',
    ])
  })

  it('führt ohne offenes Board im Projekt-Block nur die projektweiten Einträge', () => {
    expect(eintraege({ board: null, projectId: 7, canViewNightRun: true }, 'Projekt')).toEqual(['Runner'])
  })

  // Seit dem Rückbau des Ideen-Links (#1201) kann der Projekt-Block leer bleiben: kein Board, kein
  // Nachtlauf-Recht. Dann darf er gar nicht erscheinen, statt einen Titel ohne Eintrag zu zeigen.
  it('lässt den Projekt-Block weg, wenn er ohne Board und ohne Nachtlauf-Recht leer bliebe', () => {
    expect(bloecke({ board: null, projectId: 7 })).toEqual(['Übersicht', 'Verwaltung'])
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

describe('buildNavItems Projekt-Kontext', () => {
  it('bevorzugt den Board-Projektkontext vor einer abweichenden projectId', () => {
    expect(link({ board, projectId: 99, canViewNightRun: true }, 'Runner')?.path).toBe(
      `/projects/${board.projectId}/nachtlauf`,
    )
  })
})

describe('buildNavItems Läufe-Link', () => {
  // Reine Parameterprüfung: buildNavItems bekommt den fertigen Booleschen Wert. Wie er entsteht
  // (canManageProject, also auch für den Plattform-Admin), ist in der AppShell geprüft.
  it('zeigt „Läufe" bei gesetztem Sichtbarkeitswert und offenem Board', () => {
    expect(link({ board, canViewNightRun: true }, 'Runner')?.path).toBe(`/projects/${board.projectId}/nachtlauf`)
  })

  it('zeigt „Läufe" auf einer Projekt-Route ohne offenes Board', () => {
    expect(link({ board: null, projectId: 7, canViewNightRun: true }, 'Runner')?.path).toBe('/projects/7/nachtlauf')
  })

  it('blendet „Läufe" ohne Projekt-Kontext aus', () => {
    expect(link({ board: null, canViewNightRun: true }, 'Runner')).toBeUndefined()
  })

  it('blendet „Läufe" ohne gesetzten oder mit fehlendem Sichtbarkeitswert aus', () => {
    expect(link({ board, canViewNightRun: false }, 'Runner')).toBeUndefined()
    expect(link({ board }, 'Runner')).toBeUndefined()
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

  it('führt „Plattform-Leitstand" und „Admin" nur für System-Admins, nach Mitglieder und Rollen', () => {
    expect(eintraege({ board, canManageMembers: true, isAdmin: true }, 'Verwaltung')).toEqual([
      'Mitglieder',
      'Rollen & Rechte',
      'Plattform-Leitstand',
      'Admin',
    ])
    expect(link({ board: null }, 'Admin')).toBeUndefined()
  })

  // AK 2: jederzeit über die Schiene erreichbar, nicht nur beim Anmelden. AK 3: wer nicht
  // Plattform-Admin ist, sieht den Eintrag nicht.
  it('zeigt „Plattform-Leitstand" nur dem Plattform-Admin und verweist auf die Seite (#1082)', () => {
    expect(link({ board, isAdmin: true }, 'Plattform-Leitstand')?.path).toBe('/plattform-leitstand')
    expect(link({ board, isAdmin: false }, 'Plattform-Leitstand')).toBeUndefined()
  })

  it('führt „Projekte" auf den eigenen Pfad der Projektliste (#1082)', () => {
    expect(link({ board, isAdmin: true }, 'Projekte')?.path).toBe('/projects')
  })
})

const projekte = [
  { id: 5, name: 'Fünf', role: 'OWNER' },
  { id: 9, name: 'Neun', role: 'VIEWER' },
]

/** Voreinstellung einer Board-Route auf Board 1 (Projekt 5); jeder Test ändert nur, was er meint. */
const kontext = (ueberschreibungen: Partial<NavKontextParams> = {}) =>
  navKontext({ board, routeProjectId: null, boardId: 1, projects: projekte, admin: false, ...ueberschreibungen })

describe('navKontext Board-Kontext der Schiene', () => {
  it('nimmt auf einer Board-Route das geladene Board', () => {
    expect(kontext().kontextBoard).toEqual(board)
  })

  it('behält das Board auf einer Projektseite desselben Projekts (#990)', () => {
    expect(kontext({ routeProjectId: 5, boardId: null }).kontextBoard).toEqual(board)
  })

  it('verwirft das Board auf der Projektseite eines anderen Projekts', () => {
    expect(kontext({ routeProjectId: 9, boardId: null }).kontextBoard).toBeNull()
  })

  it('bleibt ohne geladenes Board auf einer Projektseite ohne Board-Kontext', () => {
    expect(kontext({ board: null, routeProjectId: 7, boardId: null }).kontextBoard).toBeNull()
  })

  it('bleibt auf einer Seite ohne Projekt- und Board-Bezug (Admin-Seite) ohne Board-Kontext', () => {
    expect(kontext({ board: null, boardId: null }).kontextBoard).toBeNull()
  })
})

describe('navKontext Projektzahl', () => {
  it('zählt die geladenen Projekte', () => {
    expect(kontext().projectCount).toBe(2)
  })

  it('meldet die Anzahl als unbekannt, solange die Liste nicht geladen ist', () => {
    expect(kontext({ projects: null }).projectCount).toBe(null)
  })
})

describe('navKontext Bezugsprojekt', () => {
  it('nimmt auf einer Board-Route das Projekt des Boards', () => {
    expect(kontext().pfadProjektId).toBe(5)
  })

  it('nimmt auf einer Projektseite ohne Board das Projekt der Adresse (#1124)', () => {
    expect(kontext({ board: null, boardId: null, routeProjectId: 9 }).pfadProjektId).toBe(9)
  })

  it('nimmt auf der Projektseite eines anderen Projekts dieses und nicht das geladene Board', () => {
    expect(kontext({ routeProjectId: 9, boardId: null }).pfadProjektId).toBe(9)
  })

  // Die Kartensuche braucht das Projekt sofort; die Projektliste sagt nur, wie es heißt.
  it('steht auch ohne geladene Projektliste fest', () => {
    expect(kontext({ projects: null }).pfadProjektId).toBe(5)
  })

  it('bleibt ohne Projekt- und Board-Bezug offen', () => {
    expect(kontext({ board: null, boardId: null }).pfadProjektId).toBe(null)
  })
})

describe('navKontext Rechte des aktuellen Projekts', () => {
  it('leitet das Board-Verwaltungsrecht aus der Rolle im Projekt des Boards ab', () => {
    expect(kontext().canManageCurrentBoards).toBe(true)
    expect(kontext({ routeProjectId: 9, boardId: null }).canManageCurrentBoards).toBe(false)
  })

  it('gibt dem Plattform-Admin die Rechte auch ohne Projektrolle', () => {
    const ohneProjekt = kontext({ board: null, boardId: null, admin: true })
    expect(ohneProjekt.canManageCurrentBoards).toBe(true)
    // `canManageMembers` bekommt bewusst keinen Plattform-Admin-Wert: Der Eintrag „Mitglieder"
    // hängt an der Projektrolle, so wie bisher in der Shell.
    expect(ohneProjekt.canManageCurrentMembers).toBe(false)
  })

  /**
   * Seit Issue #1079 lässt der Server einen Plattform-Admin ohne eigene OWNER-Rolle die
   * Nachtlauf-Auswertung nur noch am **teilnehmenden** Projekt lesen. Ein Eintrag, der sonst auf
   * einen Fehlertext führte, ist schlechter als keiner (#1082).
   */
  it('zeigt dem Plattform-Admin „Läufe" nur am teilnehmenden Projekt (#1082)', () => {
    const fremd = { id: 7, name: 'Sieben', role: 'VIEWER' }
    const ohneTeilnahme = kontext({ board: null, boardId: null, routeProjectId: 7, admin: true, projects: [fremd] })
    const mitTeilnahme = kontext({
      board: null,
      boardId: null,
      routeProjectId: 7,
      admin: true,
      projects: [{ ...fremd, dashboardParticipation: true }],
    })

    expect(ohneTeilnahme.canViewNightRun).toBe(false)
    expect(mitTeilnahme.canViewNightRun).toBe(true)
  })

  it('zeigt dem echten Owner „Läufe" auch ohne Teilnahme (#1082)', () => {
    const eigen = { id: 7, name: 'Sieben', role: 'OWNER' }

    expect(kontext({ board: null, boardId: null, routeProjectId: 7, admin: false, projects: [eigen] }).canViewNightRun).toBe(true)
  })

  it('zeigt Läufe nur mit Owner-Rolle im Projekt des Pfads', () => {
    expect(kontext().canViewNightRun).toBe(true)
    expect(kontext({ routeProjectId: 9, boardId: null }).canViewNightRun).toBe(false)
    expect(kontext({ board: null, boardId: null, projects: [{ id: 7, name: 'Sieben', role: 'ADMIN' }], routeProjectId: 7 }).canViewNightRun).toBe(
      false,
    )
  })

  it('erlaubt die Mitgliederverwaltung ab der Projektrolle ADMIN', () => {
    const projektAdmin = kontext({
      board: null,
      boardId: null,
      routeProjectId: 7,
      projects: [{ id: 7, name: 'Sieben', role: 'ADMIN' }],
    })
    expect(projektAdmin.canManageCurrentMembers).toBe(true)
    // Boards verwalten hängt am Projekt des Boards — ohne Board-Kontext bleibt es aus.
    expect(projektAdmin.canManageCurrentBoards).toBe(false)
    expect(kontext({ routeProjectId: 9, boardId: null }).canManageCurrentMembers).toBe(false)
  })

  it('lässt ohne Projekt-Kontext alle Rechte aus', () => {
    const leer = kontext({ board: null, boardId: null })
    expect(leer.canManageCurrentBoards).toBe(false)
    expect(leer.canViewNightRun).toBe(false)
    expect(leer.canManageCurrentMembers).toBe(false)
  })
})

describe('navKontext Projektnamen', () => {
  it('nennt auf einer Board-Route beide Namen aus demselben Projekt', () => {
    expect(kontext().projectName).toBe('Fünf')
    expect(kontext().currentProjectName).toBe('Fünf')
  })

  it('sucht das Projekt des Boards heraus, statt das erste der Liste zu nehmen', () => {
    const zweites = kontext({ board: { id: 3, name: 'C', projectId: 9 }, boardId: 3 })
    expect(zweites.projectName).toBe('Neun')
    expect(zweites.currentProjectName).toBe('Neun')
  })

  it('nennt auf der Projektseite eines anderen Projekts nur den Namen des Pfad-Projekts', () => {
    const fremd = kontext({ routeProjectId: 9, boardId: null })
    expect(fremd.projectName).toBe('Neun')
    expect(fremd.currentProjectName).toBe(null)
  })

  it('lässt beide Namen ohne Projekt-Kontext offen', () => {
    expect(kontext({ board: null, boardId: null }).projectName).toBe(null)
    expect(kontext({ board: null, boardId: null }).currentProjectName).toBe(null)
  })

  it('lässt beide Namen offen, solange die Projektliste fehlt', () => {
    expect(kontext({ projects: null }).projectName).toBe(null)
    expect(kontext({ projects: null }).currentProjectName).toBe(null)
  })
})

describe('navKontext Pfad im Kopf', () => {
  it('führt auf einer Board-Route Projekt und Board', () => {
    expect(kontext().pfad).toEqual([
      { label: 'Fünf', to: '/projects/5' },
      { label: 'B', to: '/boards/1' },
    ])
  })

  it('nennt auf einer Projektseite nur das Projekt (#990)', () => {
    expect(kontext({ routeProjectId: 5, boardId: null }).pfad).toEqual([{ label: 'Fünf', to: '/projects/5' }])
  })

  it('nennt beim Board-Wechsel nicht das noch geladene Vorgänger-Board', () => {
    expect(kontext({ boardId: 2 }).pfad).toEqual([{ label: 'Fünf', to: '/projects/5' }])
  })

  it('nennt das Board auch ohne geladene Projektliste', () => {
    expect(kontext({ projects: null }).pfad).toEqual([{ label: 'B', to: '/boards/1' }])
  })

  it('nennt das Projekt der Route auch ohne Board', () => {
    expect(kontext({ board: null, boardId: null, routeProjectId: 9 }).pfad).toEqual([
      { label: 'Neun', to: '/projects/9' },
    ])
  })

  it('bleibt ohne Projekt- und Board-Bezug leer', () => {
    expect(kontext({ board: null, boardId: null }).pfad).toEqual([])
  })
})
