import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import CircularProgress from '@mui/material/CircularProgress'
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
import { LineChart, MarkElement, type MarkElementProps } from '@mui/x-charts/LineChart'
import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import { boardsApi, type Board } from '../api/boards'
import { cardsApi, type Card } from '../api/cards'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { CardDetailModal } from '../components/CardDetailModal'
import { MetricTile } from '../components/MetricTile'
import { useSnackbar } from '../components/SnackbarProvider'
import {
  dashboardApi,
  type BoardDashboardKpis,
  type ColumnDwell,
  type OutlierCard,
  type WeeklyThroughput,
} from '../api/dashboard'
import { formatDuration } from '../lib/formatDuration'
import { useProjectName } from '../lib/useProjectName'

/**
 * Spalte mit der längsten gemessenen Verweildauer — der Engpass, den die Kacheln markieren.
 * `null`, solange keine Spalte eine Datenbasis hat: eine fehlende Messung ist kein Nullwert und
 * darf deshalb auch nicht als „längste Spalte“ gewinnen.
 *
 * Ebenfalls `null` bei genau einer gemessenen Spalte: „längste“ ist ein Vergleich, und ein
 * Vergleich braucht zwei Werte. Auf einem frischen Board wäre die einzige gemessene Spalte sonst
 * automatisch der Engpass — eine Aussage ohne Inhalt, die zu falschen Schlüssen einlädt.
 */
function longestDwellColumnId(columns: readonly ColumnDwell[]): number | null {
  let bestId: number | null = null
  let bestSeconds = -1
  let measured = 0
  for (const column of columns) {
    const seconds = column.avgDwellSeconds
    if (seconds != null) {
      measured += 1
      if (seconds > bestSeconds) {
        bestSeconds = seconds
        bestId = column.columnId
      }
    }
  }
  return measured >= 2 ? bestId : null
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
 * Der ruhige Hinweis statt der großen Zahl greift nur, wenn **beide** Zeiten ohne Datenbasis sind.
 * Heute fallen sie zusammen (beide setzen eine fertige Karte voraus), aber die Cycle Time hängt
 * zusätzlich an einer „Ready“-artigen Spalte — verschiebt sich diese Definition, darf ein
 * vorhandener Wert nicht still hinter dem Hinweis verschwinden.
 *
 * Die Cycle Time nutzt dieselbe {@link MetricTile} wie die Spalten-Kacheln: der Leerwert-Zustand
 * hängt damit auch hier allein an der Stichprobengröße und sieht überall gleich aus.
 */
function MetricHeadline({ kpis }: Readonly<{ kpis: BoardDashboardKpis }>) {
  const noLeadTime = kpis.avgLeadTimeSeconds == null
  if (noLeadTime && kpis.avgCycleTimeSeconds == null) {
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
      {/*
        `flex-start` statt `baseline`: die Cycle Time steht als eigene Kachel mit Rahmen daneben,
        und eine Kachel richtet sich an ihrer Oberkante aus, nicht an der Grundlinie der 56-px-Zahl.
      */}
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={{ xs: 2, sm: 5 }}
        alignItems={{ sm: 'flex-start' }}
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
            sx={{
              fontSize: { xs: 48, sm: 56 },
              fontWeight: noLeadTime ? 400 : 700,
              color: noLeadTime ? 'text.secondary' : 'text.primary',
              lineHeight: 1.1,
            }}
          >
            {formatDuration(kpis.avgLeadTimeSeconds)}
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            {noLeadTime
              ? 'Vom Anlegen der Karte bis fertig — noch keine abgeschlossene Karte.'
              : `Vom Anlegen der Karte bis fertig — Durchschnitt aus ${sampleBasis(kpis.leadTimeSampleCount)}.`}
          </Typography>
        </Box>
        <MetricTile
          label="Ø Cycle Time"
          value={formatDuration(kpis.avgCycleTimeSeconds)}
          sample={kpis.cycleTimeSampleCount}
        />
      </Stack>
    </Paper>
  )
}

/**
 * Wochenbeginn als Datum **mit Jahr**, z. B. „01.06.26“. Ohne Jahr sind zwei Punkte über einen
 * Jahreswechsel hinweg nicht unterscheidbar — „28.07.“ kann dann zwei verschiedene Wochen meinen.
 * Zweistellig, weil dieselbe Zeichenkette auf der X-Achse zwölfmal nebeneinander steht.
 */
function formatWeekStart(weekStart: string): string {
  return new Date(weekStart).toLocaleDateString('de-DE', {
    day: '2-digit',
    month: '2-digit',
    year: '2-digit',
  })
}

/**
 * Indizes der Wochen, deren Wert direkt am Diagramm steht. Jeden der zwölf Punkte zu beschriften
 * ergibt einen Zahlensalat, der die Linie überdeckt — beschriftet werden deshalb genau die drei
 * Punkte, die man ohnehin abliest: der erste (Ausgangslage), der letzte (aktueller Stand) und das
 * Maximum (die beste Woche). Bei Gleichstand gewinnt die frühere Woche: sonst wanderte die
 * Markierung mit jeder neuen Woche gleichen Werts nach rechts und sähe aus wie eine Veränderung.
 */
function labeledWeekIndices(counts: readonly number[]): ReadonlySet<number> {
  if (counts.length === 0) {
    return new Set()
  }
  let maxIndex = 0
  counts.forEach((count, index) => {
    if (count > counts[maxIndex]) {
      maxIndex = index
    }
  })
  return new Set([0, maxIndex, counts.length - 1])
}

/**
 * Erzeugt die `mark`-Slot-Komponente des Diagramms: ein Punkt wie gehabt, für die ausgewählten
 * Wochen zusätzlich der Wert als Text darüber. Der Slot bekommt vom Chart nur den Index — die
 * Werte kommen deshalb über den Abschluss herein, nicht über Props.
 */
function makeThroughputMark(counts: readonly number[]) {
  const labeled = labeledWeekIndices(counts)
  function ThroughputMark({ dataIndex, ...markProps }: Readonly<MarkElementProps>) {
    return (
      <g>
        <MarkElement dataIndex={dataIndex} {...markProps} />
        {labeled.has(dataIndex) && (
          <text
            data-testid="throughput-value"
            x={markProps.x}
            y={markProps.y}
            dy={-10}
            textAnchor="middle"
            fontSize={12}
            fontWeight={700}
            fill="currentColor"
          >
            {counts[dataIndex]}
          </text>
        )}
      </g>
    )
  }
  return ThroughputMark
}

/**
 * Der Durchsatz je Woche: Linie mit beschrifteten Eckpunkten und dieselbe Aussage als Tabelle.
 * Die Tabelle ist dauerhaft sichtbar und nicht aufklappbar — sie bleibt mit zwölf Zeilen kurz,
 * und ein eingeklapptes Element wäre für alle, die den Textzugang brauchen, eine zusätzliche
 * Hürde statt einer Alternative.
 */
function ThroughputSection({ throughput }: Readonly<{ throughput: readonly WeeklyThroughput[] }>) {
  const counts = useMemo(() => throughput.map((w) => w.doneCount), [throughput])
  const Mark = useMemo(() => makeThroughputMark(counts), [counts])

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
        Durchsatz je Woche (abgeschlossene Karten)
      </Typography>
      {throughput.length === 0 ? (
        <Typography color="text.secondary">
          Noch keine abgeschlossene Karte in den letzten Wochen.
        </Typography>
      ) : (
        <>
          <LineChart
            height={260}
            xAxis={[{ scaleType: 'point', data: throughput.map((w) => formatWeekStart(w.weekStart)) }]}
            series={[{ data: counts, label: 'Fertig' }]}
            slots={{ mark: Mark }}
            // Legende aus: Sie benennt bei einer einzigen Serie nur, was die Überschrift schon
            // sagt. Der Serienname bleibt trotzdem gesetzt — der Tooltip braucht ihn.
            slotProps={{ legend: { hidden: true } }}
          />
          <TableContainer sx={{ mt: 1 }}>
            <Table size="small" aria-label="Durchsatz je Woche">
              <TableHead>
                <TableRow>
                  <TableCell>Woche ab</TableCell>
                  <TableCell align="right">Fertige Karten</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {throughput.map((w) => (
                  <TableRow key={w.weekStart}>
                    <TableCell>{formatWeekStart(w.weekStart)}</TableCell>
                    <TableCell align="right">{w.doneCount}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
    </Paper>
  )
}

/**
 * Die Ausreißer-Tabelle — und der Weg von dort zur Karte. Wer hier eine klemmende Karte sieht,
 * will sie ansehen, ohne sie sich zu merken und auf dem Board zu suchen.
 *
 * Ein Ausreißer trägt nur `cardId`, und es gibt keinen Endpoint, der eine einzelne Karte lädt
 * (unter `/api/cards/{id}/…` liegen nur activity, comments, attachments). Deshalb wird beim ersten
 * Klick die Kartenliste des Boards geholt und die Karte darin gesucht; danach bleibt sie für
 * weitere Klicks liegen. Die Kennzahlen der Seite sind ohnehin eine Momentaufnahme — eine zweite
 * Abfrage je Klick brächte keine frischere Aussage.
 *
 * Die Zeile trägt bewusst **kein** `role="button"`: Das nähme der Datentabelle ihre Semantik und
 * damit Screenreadern die Zuordnung von Zelle zu Spaltenüberschrift. Fokussierbar ist stattdessen
 * die Kartennummer in der ersten Zelle; die Maus darf weiterhin die ganze Zeile treffen.
 */
function OutlierSection({
  boardId,
  projectId,
  outliers,
}: Readonly<{ boardId: number; projectId?: number; outliers: readonly OutlierCard[] }>) {
  const notify = useSnackbar()
  const [cards, setCards] = useState<Card[] | null>(null)
  const [busyCardId, setBusyCardId] = useState<number | null>(null)
  const [detail, setDetail] = useState<{ card: Card; columnName: string } | null>(null)

  const openCard = async (outlier: OutlierCard) => {
    setBusyCardId(outlier.cardId)
    try {
      const list = cards ?? (await cardsApi.list(boardId))
      setCards(list)
      const card = list.find((c) => c.id === outlier.cardId)
      if (card) {
        setDetail({ card, columnName: outlier.columnName })
      } else {
        // Die Kennzahlen können älter sein als das Board: Die Karte kann inzwischen gelöscht,
        // archiviert oder in den Ideen-Pool verschoben sein. Das zu sagen ist ehrlicher als ein
        // leeres Modal — und nennt die Nummer, damit klar ist, welche Zeile gemeint war.
        notify(
          `Karte ${outlier.number} ist auf diesem Board nicht mehr zu finden — vermutlich gelöscht, archiviert oder in den Ideen-Pool verschoben.`,
          'warning',
        )
      }
    } catch {
      notify('Karte konnte nicht geladen werden.', 'error')
    } finally {
      setBusyCardId(null)
    }
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
        Ausreißer (über 7 Tage in einer Spalte)
      </Typography>
      {outliers.length === 0 ? (
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
              {outliers.map((o) => {
                const busy = busyCardId === o.cardId
                return (
                  <TableRow
                    key={`${o.cardId}-${o.columnName}-${o.dwellSeconds}`}
                    hover
                    aria-busy={busy}
                    onClick={() => void openCard(o)}
                    sx={{ cursor: 'pointer' }}
                  >
                    <TableCell>
                      <Stack direction="row" spacing={0.5} alignItems="center">
                        <Link
                          component="button"
                          type="button"
                          underline="hover"
                          aria-label={`Karte ${o.number} öffnen: ${o.title}`}
                          // Die Zeile hört auf denselben Klick — ohne Stopp öffnete der Auslöser
                          // die Karte zweimal und lüde die Liste doppelt.
                          onClick={(e) => {
                            e.stopPropagation()
                            void openCard(o)
                          }}
                        >
                          {o.number}
                        </Link>
                        {busy && <CircularProgress size={12} aria-label="Karte wird geladen" />}
                      </Stack>
                    </TableCell>
                    <TableCell>{o.title}</TableCell>
                    <TableCell>{o.columnName}</TableCell>
                    <TableCell align="right">{formatDuration(o.dwellSeconds)}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
      {detail && (
        <CardDetailModal
          card={detail.card}
          canEdit={false}
          projectId={projectId}
          columnName={detail.columnName}
          onClose={() => setDetail(null)}
        />
      )}
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
                  emphasis={c.columnId === longestColumnId ? 'längste Spalte' : undefined}
                />
              ))}
            </Box>
          </Paper>

          <ThroughputSection throughput={kpis.throughput} />

          <OutlierSection boardId={id} projectId={board?.projectId} outliers={kpis.outliers} />
        </Stack>
      )}
    </Box>
  )
}
