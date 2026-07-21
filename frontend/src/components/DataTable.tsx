import ViewColumnIcon from '@mui/icons-material/ViewColumn'
import Box from '@mui/material/Box'
import Checkbox from '@mui/material/Checkbox'
import IconButton from '@mui/material/IconButton'
import ListItemText from '@mui/material/ListItemText'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Tooltip from '@mui/material/Tooltip'
import { type ReactNode, useRef, useState } from 'react'

/** Spaltendefinition für {@link DataTable}. */
export interface DataTableColumn<Row> {
  key: string
  header: ReactNode
  render: (row: Row) => ReactNode
  /** Startbreite in Pixeln (bei fehlender Persistenz). */
  defaultWidth?: number
  /** Mindestbreite in Pixeln beim Resizen. */
  minWidth?: number
  align?: 'left' | 'right'
  /** Über das Spalten-Menü aus-/einblendbar (Default: false — immer sichtbar). */
  hideable?: boolean
  /** Rechter Ziehgriff zum Ändern der Breite (Default: false — feste Spalte). */
  resizable?: boolean
}

interface Props<Row> {
  columns: ReadonlyArray<DataTableColumn<Row>>
  rows: ReadonlyArray<Row>
  getRowKey: (row: Row) => string | number
  /** Optionales `data-testid` je Zeile (z. B. um bestehende Test-Hooks zu erhalten). */
  getRowTestId?: (row: Row) => string | undefined
  /** Namensraum für die localStorage-Persistenz (`manban.table.<storageKey>.*`). */
  storageKey: string
}

const widthsStorageKey = (storageKey: string) => `manban.table.${storageKey}.widths`
const hiddenStorageKey = (storageKey: string) => `manban.table.${storageKey}.hidden`

function readWidths(storageKey: string): Record<string, number> {
  try {
    const raw = localStorage.getItem(widthsStorageKey(storageKey))
    return raw ? (JSON.parse(raw) as Record<string, number>) : {}
  } catch {
    return {}
  }
}

function readHidden(storageKey: string): string[] {
  try {
    const raw = localStorage.getItem(hiddenStorageKey(storageKey))
    return raw ? (JSON.parse(raw) as string[]) : []
  } catch {
    return []
  }
}

/**
 * Generische, wiederverwendbare Datentabelle mit persistenten Spaltenbreiten (Drag am rechten Rand)
 * und ein-/ausblendbaren Spalten. Zebra-Streifen kommen global über das Theme (#329). Persistenz per
 * localStorage je `storageKey` (robust gegen gesperrten Storage, wie in BoardListPage/AppShell).
 */
export function DataTable<Row>({
  columns,
  rows,
  getRowKey,
  getRowTestId,
  storageKey,
}: Readonly<Props<Row>>) {
  const [widths, setWidths] = useState<Record<string, number>>(() => readWidths(storageKey))
  const [hidden, setHidden] = useState<string[]>(() => readHidden(storageKey))
  const [menuAnchor, setMenuAnchor] = useState<HTMLElement | null>(null)
  const resizeCleanupRef = useRef<(() => void) | null>(null)

  const hideableColumns = columns.filter((c) => c.hideable)
  const visibleColumns = columns.filter((c) => !hidden.includes(c.key))
  const columnWidth = (col: DataTableColumn<Row>) => widths[col.key] ?? col.defaultWidth

  const persist = (key: string, value: unknown) => {
    try {
      localStorage.setItem(key, JSON.stringify(value))
    } catch {
      // localStorage nicht verfügbar — Zustand bleibt nur für die Sitzung.
    }
  }

  const startResize = (col: DataTableColumn<Row>, event: React.MouseEvent) => {
    event.preventDefault()
    const startX = event.clientX
    const startWidth = columnWidth(col) ?? 150
    const minWidth = col.minWidth ?? 40
    const onMove = (ev: MouseEvent) => {
      const next = Math.max(minWidth, startWidth + (ev.clientX - startX))
      setWidths((current) => ({ ...current, [col.key]: next }))
    }
    const detach = () => {
      document.removeEventListener('mousemove', onMove)
      document.removeEventListener('mouseup', onUp)
      resizeCleanupRef.current = null
    }
    const onUp = () => {
      detach()
      setWidths((current) => {
        persist(widthsStorageKey(storageKey), current)
        return current
      })
    }
    document.addEventListener('mousemove', onMove)
    document.addEventListener('mouseup', onUp)
    resizeCleanupRef.current = detach
  }

  const toggleHidden = (key: string) => {
    setHidden((current) => {
      const next = current.includes(key) ? current.filter((k) => k !== key) : [...current, key]
      persist(hiddenStorageKey(storageKey), next)
      return next
    })
  }

  return (
    <Box>
      {hideableColumns.length > 0 && (
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', mb: 0.5 }}>
          <IconButton
            size="small"
            aria-label="Spalten ein-/ausblenden"
            onClick={(e) => setMenuAnchor(e.currentTarget)}
          >
            <ViewColumnIcon fontSize="small" />
          </IconButton>
          <Menu anchorEl={menuAnchor} open={menuAnchor !== null} onClose={() => setMenuAnchor(null)}>
            {hideableColumns.map((col) => (
              <MenuItem key={col.key} onClick={() => toggleHidden(col.key)}>
                <Checkbox
                  size="small"
                  checked={!hidden.includes(col.key)}
                  slotProps={{ input: { readOnly: true, 'aria-label': `Spalte ${col.key} ein-/ausblenden` } }}
                />
                <ListItemText primary={col.header} />
              </MenuItem>
            ))}
          </Menu>
        </Box>
      )}
      <Table size="small" sx={{ tableLayout: 'fixed' }}>
        <TableHead>
          <TableRow>
            {visibleColumns.map((col) => (
              <TableCell
                key={col.key}
                align={col.align}
                sx={{ width: columnWidth(col), minWidth: col.minWidth, position: 'relative' }}
              >
                {col.header}
                {col.resizable && (
                  <Tooltip title="Breite ziehen">
                    <Box
                      role="separator"
                      aria-label={`Breite von Spalte ${col.key} ändern`}
                      onMouseDown={(e) => startResize(col, e)}
                      sx={{
                        position: 'absolute',
                        top: 0,
                        right: 0,
                        height: '100%',
                        width: 6,
                        cursor: 'col-resize',
                        userSelect: 'none',
                        // Immer sichtbare, dezente Haarlinie am Spaltenrand (Marken-BORDER).
                        '&::before': {
                          content: '""',
                          position: 'absolute',
                          top: 4,
                          bottom: 4,
                          right: 0,
                          width: '1px',
                          bgcolor: 'divider',
                          transition: 'background-color 120ms, width 120ms',
                        },
                        // Kleiner Griffpunkt als „hier ziehen"-Signal, nur beim Überfahren sichtbar.
                        '&::after': {
                          content: '""',
                          position: 'absolute',
                          top: '50%',
                          right: '-1px',
                          transform: 'translateY(-50%)',
                          width: 5,
                          height: 5,
                          borderRadius: '50%',
                          bgcolor: 'primary.main',
                          opacity: 0,
                          transition: 'opacity 120ms',
                        },
                        '&:hover::before': { bgcolor: 'primary.main', width: '2px' },
                        '&:hover::after': { opacity: 1 },
                      }}
                    />
                  </Tooltip>
                )}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row) => (
            <TableRow key={getRowKey(row)} data-testid={getRowTestId?.(row)}>
              {visibleColumns.map((col) => (
                <TableCell key={col.key} align={col.align}>
                  {col.render(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  )
}
