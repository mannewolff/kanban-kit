import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DataTable, type DataTableColumn } from './DataTable'

interface Row {
  id: number
  name: string
  email: string
  role: string
}

const rows: Row[] = [
  { id: 1, name: 'Alice', email: 'a@x.de', role: 'OWNER' },
  { id: 2, name: 'Bob', email: 'b@x.de', role: 'MEMBER' },
]

function makeColumns(): DataTableColumn<Row>[] {
  return [
    { key: 'name', header: 'Name', render: (r) => r.name, defaultWidth: 120, minWidth: 60, resizable: true },
    { key: 'email', header: 'E-Mail', render: (r) => r.email, defaultWidth: 160, resizable: true, hideable: true },
    // Bewusst ohne defaultWidth/minWidth: deckt die Fallbacks (150/40) beim Resize ab.
    { key: 'role', header: 'Rolle', render: (r) => r.role, resizable: true, hideable: true },
    { key: 'actions', header: 'Aktionen', render: () => '⋮', align: 'right' },
  ]
}

function renderTable(storageKey = 'users', columns = makeColumns()) {
  return render(
    <DataTable columns={columns} rows={rows} getRowKey={(r) => r.id} storageKey={storageKey} />,
  )
}

// Node 26 deaktiviert natives localStorage (Zugriff wirft) — wie in BoardListPage.test ein
// funktionierendes Fake stubben, damit die Persistenz beobachtbar ist.
function fakeStorage(): Storage {
  const map = new Map<string, string>()
  return {
    getItem: (k) => map.get(k) ?? null,
    setItem: (k, v) => void map.set(k, String(v)),
    removeItem: (k) => void map.delete(k),
    clear: () => map.clear(),
    key: (i) => [...map.keys()][i] ?? null,
    get length() {
      return map.size
    },
  }
}

describe('DataTable', () => {
  beforeEach(() => vi.stubGlobal('localStorage', fakeStorage()))
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('rendert Header und Zeilen', () => {
    renderTable()
    expect(screen.getByText('Name')).toBeInTheDocument()
    expect(screen.getByText('Aktionen')).toBeInTheDocument()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('b@x.de')).toBeInTheDocument()
  })

  it('blendet eine Spalte über das Menü aus und persistiert', () => {
    renderTable()
    expect(screen.getByText('a@x.de')).toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Spalten ein-/ausblenden'))
    fireEvent.click(screen.getByLabelText('Spalte email ein-/ausblenden'))

    expect(screen.queryByText('a@x.de')).not.toBeInTheDocument()
    expect(localStorage.getItem('manban.table.users.hidden')).toBe(JSON.stringify(['email']))
  })

  it('stellt ausgeblendete Spalten beim Mount wieder her', () => {
    localStorage.setItem('manban.table.users.hidden', JSON.stringify(['email']))
    renderTable()
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.queryByText('a@x.de')).not.toBeInTheDocument()
  })

  it('blendet eine ausgeblendete Spalte wieder ein und persistiert', () => {
    localStorage.setItem('manban.table.users.hidden', JSON.stringify(['email']))
    renderTable()
    expect(screen.queryByText('a@x.de')).not.toBeInTheDocument()

    fireEvent.click(screen.getByLabelText('Spalten ein-/ausblenden'))
    fireEvent.click(screen.getByLabelText('Spalte email ein-/ausblenden'))

    expect(screen.getByText('a@x.de')).toBeInTheDocument()
    expect(localStorage.getItem('manban.table.users.hidden')).toBe(JSON.stringify([]))
  })

  it('zeigt nur hideable Spalten im Menü', () => {
    renderTable()
    fireEvent.click(screen.getByLabelText('Spalten ein-/ausblenden'))
    expect(screen.getByLabelText('Spalte email ein-/ausblenden')).toBeInTheDocument()
    expect(screen.getByLabelText('Spalte role ein-/ausblenden')).toBeInTheDocument()
    expect(screen.queryByLabelText('Spalte name ein-/ausblenden')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Spalte actions ein-/ausblenden')).not.toBeInTheDocument()
  })

  it('schließt das Spalten-Menü wieder', async () => {
    renderTable()
    fireEvent.click(screen.getByLabelText('Spalten ein-/ausblenden'))
    expect(screen.getByLabelText('Spalte email ein-/ausblenden')).toBeInTheDocument()

    fireEvent.keyDown(screen.getByRole('menu'), { key: 'Escape' })
    await waitFor(() =>
      expect(screen.queryByLabelText('Spalte email ein-/ausblenden')).not.toBeInTheDocument(),
    )
  })

  it('zeigt keinen Menü-Button ohne hideable Spalten', () => {
    const columns: DataTableColumn<Row>[] = [
      { key: 'name', header: 'Name', render: (r) => r.name },
      { key: 'actions', header: 'Aktionen', render: () => '⋮' },
    ]
    renderTable('fixed', columns)
    expect(screen.queryByLabelText('Spalten ein-/ausblenden')).not.toBeInTheDocument()
  })

  it('zeigt Ziehgriffe nur an resizebaren Spalten', () => {
    renderTable()
    expect(screen.getByLabelText('Breite von Spalte name ändern')).toBeInTheDocument()
    expect(screen.getByLabelText('Breite von Spalte email ändern')).toBeInTheDocument()
    expect(screen.queryByLabelText('Breite von Spalte actions ändern')).not.toBeInTheDocument()
  })

  it('schreibt die geänderte Breite in den localStorage', () => {
    renderTable()
    const handle = screen.getByLabelText('Breite von Spalte name ändern')
    fireEvent.mouseDown(handle, { clientX: 100 })
    fireEvent.mouseMove(document, { clientX: 170 })
    fireEvent.mouseUp(document)
    // 120 (defaultWidth) + (170 - 100) = 190
    expect(localStorage.getItem('manban.table.users.widths')).toBe(JSON.stringify({ name: 190 }))
  })

  it('liest die persistierte Breite beim Mount als Ausgangsbreite', () => {
    localStorage.setItem('manban.table.users.widths', JSON.stringify({ name: 200 }))
    renderTable()
    const handle = screen.getByLabelText('Breite von Spalte name ändern')
    fireEvent.mouseDown(handle, { clientX: 100 })
    fireEvent.mouseMove(document, { clientX: 110 })
    fireEvent.mouseUp(document)
    // Ausgangsbreite 200 (aus dem Storage) + (110 - 100) = 210
    expect(localStorage.getItem('manban.table.users.widths')).toBe(JSON.stringify({ name: 210 }))
  })

  it('respektiert die Mindestbreite beim Resize', () => {
    renderTable()
    const handle = screen.getByLabelText('Breite von Spalte name ändern')
    fireEvent.mouseDown(handle, { clientX: 100 })
    fireEvent.mouseMove(document, { clientX: 0 }) // 120 - 100 = 20 -> auf minWidth 60 geklemmt
    fireEvent.mouseUp(document)
    expect(localStorage.getItem('manban.table.users.widths')).toBe(JSON.stringify({ name: 60 }))
  })

  it('nutzt Fallback-Breite/-Mindestbreite für Spalten ohne Angaben', () => {
    renderTable()
    const handle = screen.getByLabelText('Breite von Spalte role ändern')
    fireEvent.mouseDown(handle, { clientX: 100 })
    fireEvent.mouseMove(document, { clientX: 120 })
    fireEvent.mouseUp(document)
    // Ohne defaultWidth: Ausgangsbreite 150 + 20 = 170
    expect(localStorage.getItem('manban.table.users.widths')).toBe(JSON.stringify({ role: 170 }))
  })

  it('bricht bei lesendem localStorage-Ausfall nicht ab', () => {
    vi.stubGlobal('localStorage', {
      ...fakeStorage(),
      getItem: () => {
        throw new Error('locked')
      },
    })
    expect(() => renderTable()).not.toThrow()
    expect(screen.getByText('Alice')).toBeInTheDocument()
  })

  it('bricht bei schreibendem localStorage-Ausfall nicht ab', () => {
    vi.stubGlobal('localStorage', {
      ...fakeStorage(),
      setItem: () => {
        throw new Error('locked')
      },
    })
    renderTable()

    fireEvent.click(screen.getByLabelText('Spalten ein-/ausblenden'))
    expect(() => fireEvent.click(screen.getByLabelText('Spalte email ein-/ausblenden'))).not.toThrow()

    const handle = screen.getByLabelText('Breite von Spalte name ändern')
    fireEvent.mouseDown(handle, { clientX: 100 })
    fireEvent.mouseMove(document, { clientX: 150 })
    expect(() => fireEvent.mouseUp(document)).not.toThrow()
  })
})
