import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Link from '@mui/material/Link'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Typography from '@mui/material/Typography'
import { BarChart } from '@mui/x-charts/BarChart'
import { LineChart } from '@mui/x-charts/LineChart'
import { useEffect, useState } from 'react'
import { Link as RouterLink, useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { dashboardApi, type BoardDashboardKpis } from '../api/dashboard'
import { formatDuration } from '../lib/formatDuration'
import { useProjectName } from '../lib/useProjectName'

function KpiTile({ label, value }: { label: string; value: string }) {
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

  if (!validId) {
    return <Alert severity="error">Ungültige Board-ID.</Alert>
  }

  return (
    <Box>
      <Link component={RouterLink} to={`/boards/${id}`}>
        ← Board
      </Link>
      <Typography variant="h5" sx={{ mt: 1, mb: 2 }}>
        {projectName && (
          <Box component="span" sx={{ color: 'text.secondary', fontWeight: 400 }}>
            {projectName} /{' '}
          </Box>
        )}
        <Box component="span">Dashboard</Box>
      </Typography>

      {!kpis && <Typography color="text.secondary">Kennzahlen werden geladen …</Typography>}

      {kpis && (
        <Stack spacing={3}>
          <Stack direction="row" spacing={2} flexWrap="wrap">
            <KpiTile label="Ø Lead Time" value={formatDuration(kpis.avgLeadTimeSeconds)} />
            <KpiTile label="Ø Cycle Time" value={formatDuration(kpis.avgCycleTimeSeconds)} />
          </Stack>

          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
              Ø Verweildauer je Spalte (Stunden)
            </Typography>
            <BarChart
              height={260}
              xAxis={[{ scaleType: 'band', data: kpis.columnDwell.map((c) => c.columnName) }]}
              series={[
                {
                  data: kpis.columnDwell.map((c) =>
                    c.avgDwellSeconds == null ? 0 : Math.round((c.avgDwellSeconds / 3600) * 10) / 10,
                  ),
                  label: 'Ø Stunden',
                },
              ]}
            />
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
