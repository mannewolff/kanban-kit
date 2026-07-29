import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { LineChart } from '@mui/x-charts/LineChart'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { MetricTile } from '../components/MetricTile'
import { dashboardApi, type BoardDashboardKpis, type ColumnDwell } from '../api/dashboard'
import { formatDuration } from '../lib/formatDuration'
import { useProjectName } from '../lib/useProjectName'

/**
 * Spalte mit der längsten gemessenen Verweildauer — der Engpass, den die Kacheln markieren.
 * `null`, solange keine Spalte eine Datenbasis hat: eine fehlende Messung ist kein Nullwert und
 * darf deshalb auch nicht als „längste Spalte“ gewinnen.
 */
function longestDwellColumnId(columns: readonly ColumnDwell[]): number | null {
  let bestId: number | null = null
  let bestSeconds = -1
  for (const column of columns) {
    const seconds = column.avgDwellSeconds
    if (seconds != null && seconds > bestSeconds) {
      bestSeconds = seconds
      bestId = column.columnId
    }
  }
  return bestId
}

function KpiTile({ label, value }: Readonly<{ label: string; value: string }>) {
  return (
    <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 160 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="h5" sx={{ fontWeight: 600 }}>
        {value}
      </Typography>
    </Paper>
  )
}

export function DashboardPage() {
  const { boardId } = useParams()
  const id = Number.parseInt(boardId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0
  const [board, setBoard] = useState<Board | null>(null)
  const [kpis, setKpis] = useState<BoardDashboardKpis | null>(null)

  useEffect(() => {
    if (!validId) {
      return
    }
    let active = true
    void boardsApi.get(id).then((b) => {
      if (active) setBoard(b)
    })
    void dashboardApi.get(id).then((k) => {
      if (active) setKpis(k)
    })
    return () => {
      active = false
    }
  }, [id, validId])

  const projectName = useProjectName(board?.projectId ?? null)
  const longestColumnId = kpis == null ? null : longestDwellColumnId(kpis.columnDwell)

  if (!validId) {
    return <Alert severity="error">Ungültige Board-ID.</Alert>
  }

  return (
    <Box>
      <Box sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/' },
            ...(board && projectName ? [{ label: projectName, to: `/projects/${board.projectId}` }] : []),
            ...(board ? [{ label: board.name, to: `/boards/${id}` }] : []),
            { label: 'Dashboard' },
          ]}
        />
      </Box>

      {!kpis && <Typography color="text.secondary">Kennzahlen werden geladen …</Typography>}

      {kpis && (
        <Stack spacing={3}>
          <Stack direction="row" spacing={2} flexWrap="wrap">
            <KpiTile label="Ø Lead Time" value={formatDuration(kpis.avgLeadTimeSeconds)} />
            <KpiTile label="Ø Cycle Time" value={formatDuration(kpis.avgCycleTimeSeconds)} />
          </Stack>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
              Ø Verweildauer je Spalte
            </Typography>
            {/* Reihenfolge unverändert übernehmen: `columnDwell` kommt positionssortiert vom Backend. */}
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))' }}>
              {kpis.columnDwell.map((c) => (
                <MetricTile
                  key={c.columnId}
                  label={c.columnName}
                  value={formatDuration(c.avgDwellSeconds)}
                  sample={c.sampleCount}
                  noMeasurement={c.avgDwellSeconds == null}
                  emphasis={c.columnId === longestColumnId ? 'längste Spalte' : undefined}
                />
              ))}
            </Box>
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
              Durchsatz je Woche (abgeschlossene Karten)
            </Typography>
            <LineChart
              height={260}
              xAxis={[
                {
                  scaleType: 'point',
                  data: kpis.throughput.map((w) =>
                    new Date(w.weekStart).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit' }),
                  ),
                },
              ]}
              series={[{ data: kpis.throughput.map((w) => w.doneCount), label: 'Fertig' }]}
            />
          </Paper>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
              Ausreißer (über 7 Tage in einer Spalte)
            </Typography>
            {kpis.outliers.length === 0 ? (
              <Typography color="text.secondary">Keine Ausreißer.</Typography>
            ) : (
              <TableContainer>
                <Table size="small" aria-label="Ausreißer-Karten">
                  <TableHead>
                    <TableRow>
                      <TableCell>#</TableCell>
                      <TableCell>Titel</TableCell>
                      <TableCell>Spalte</TableCell>
                      <TableCell align="right">Verweildauer</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {kpis.outliers.map((o) => (
                      <TableRow key={`${o.cardId}-${o.columnName}-${o.dwellSeconds}`}>
                        <TableCell>{o.number}</TableCell>
                        <TableCell>{o.title}</TableCell>
                        <TableCell>{o.columnName}</TableCell>
                        <TableCell align="right">{formatDuration(o.dwellSeconds)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Paper>
        </Stack>
      )}
    </Box>
  )
}
