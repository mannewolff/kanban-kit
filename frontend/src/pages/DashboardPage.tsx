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

/**
 * Datenbasis der Hero-Zahl als Satzteil — dieselbe Aussage wie die Stichprobengröße der
 * Spalten-Kacheln, hier aber in Fließtext, weil sie unter der großen Zahl als Satz gelesen wird.
 */
function sampleBasis(sample: number): string {
  return sample === 1 ? '1 abgeschlossenen Karte' : `${sample} abgeschlossenen Karten`
}

/**
 * Der Kennzahlenkopf des Dashboards: **eine** Zahl führt, die zweite steht kleiner daneben.
 * Zwei gleich große „wichtigste" Zahlen heben sich gegenseitig auf — deshalb ist die Hero-Zahl
 * die Ø Lead Time (vom Anlegen der Karte bis fertig, die Sicht des Auftraggebers). Soll künftig
 * die Cycle Time führen, werden hier die beiden Blöcke getauscht; die Darstellung bleibt.
 *
 * Ohne abgeschlossene Karte liefert das Backend `null` für beide Zeiten (die Cycle Time setzt eine
 * fertige Karte ebenso voraus wie die Lead Time). Dann prangt hier keine Leerangabe in 48 px,
 * sondern ein ruhiger Hinweis.
 */
function MetricHeadline({ kpis }: Readonly<{ kpis: BoardDashboardKpis }>) {
  if (kpis.avgLeadTimeSeconds == null) {
    return (
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Typography color="text.secondary">
          Noch keine abgeschlossene Karte — Lead und Cycle Time entstehen, sobald die erste Karte
          fertig ist.
        </Typography>
      </Paper>
    )
  }

  return (
    <Paper variant="outlined" sx={{ p: 3 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={{ xs: 2, sm: 5 }}
        alignItems={{ sm: 'baseline' }}
      >
        <Box>
          <Typography variant="caption" color="text.secondary">
            Ø Lead Time
          </Typography>
          {/*
            Bewusst ohne `tabular-nums`: Tabellenziffern sind für untereinander stehende Zahlen
            gedacht und lassen eine große Einzelzahl auseinanderfallen. Schriftfamilie bleibt die
            des Themes — nur die Größe trägt die Hervorhebung.
          */}
          <Typography
            component="p"
            data-testid="hero-metric"
            sx={{ fontSize: { xs: 48, sm: 56 }, fontWeight: 700, lineHeight: 1.1 }}
          >
            {formatDuration(kpis.avgLeadTimeSeconds)}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Vom Anlegen der Karte bis fertig — Durchschnitt aus{' '}
            {sampleBasis(kpis.leadTimeSampleCount)}.
          </Typography>
        </Box>
        <Box>
          <Typography variant="caption" color="text.secondary">
            Ø Cycle Time
          </Typography>
          <Typography variant="h6">{formatDuration(kpis.avgCycleTimeSeconds)}</Typography>
        </Box>
      </Stack>
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
          <MetricHeadline kpis={kpis} />

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
