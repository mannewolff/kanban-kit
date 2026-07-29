import Alert from '@mui/material/Alert'
import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ideasApi } from '../api/ideas'
import { projectsApi } from '../api/projects'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { IdeaPlanningBoard } from '../components/IdeaPlanningBoard'
import { NewCardModal, type NewItemInput } from '../components/NewCardModal'
import { useSnackbar } from '../components/SnackbarProvider'
import { SpecImportDialog, type SpecIdea } from '../components/SpecImportDialog'
import { readTextFile } from '../lib/readTextFile'
import { canEditCards } from '../lib/roles'
import { useProjectIdeaEvents } from '../lib/useProjectIdeaEvents'

// Der frühere Liste/Planen-Umschalter ist entfallen (nur noch die Planen-Ansicht). Sein
// localStorage-Zustand wird beim Laden einmalig entfernt, damit kein toter Wert zurückbleibt.
const LEGACY_VIEW_STORAGE_KEY = 'manban.ideasView'

/**
 * Projektweite Ideen-Seite (Projekt-Ebene, Geschwister von „Boards"): zeigt die gestapelte
 * Planen-Ansicht ({@link IdeaPlanningBoard}) mit allen Boards und dem board-losen Ideen-Pool. Oben
 * legt „Idee anlegen" eine board-lose Pool-Idee an; ein Suchfeld darüber filtert den Pool nach Titel.
 */
export function IdeasPage() {
  const { projectId } = useParams()
  const id = Number.parseInt(projectId ?? '', 10)
  const validId = Number.isInteger(id) && id > 0

  const [role, setRole] = useState<string>('VIEWER')
  const [projectName, setProjectName] = useState<string>('')
  const [textFilter, setTextFilter] = useState('')
  const [createOpen, setCreateOpen] = useState(false)
  // Im Browser gelesene Spezifikationsdatei (Name nur zur Anzeige). `null` = keine Vorschau offen;
  // hochgeladen wird die Datei nie, sie existiert hier nur als Text.
  const [spec, setSpec] = useState<{ fileName: string; markdown: string } | null>(null)
  // Reload-Impuls für die Planen-Ansicht: nach dem Anlegen einer Idee und bei Live-Events erhöht,
  // damit der vom IdeaPlanningBoard gehaltene Pool die neue bzw. geänderte Idee zeigt.
  const [refreshKey, setRefreshKey] = useState(0)

  const canEdit = canEditCards(role)
  const notify = useSnackbar()

  // Veralteten Ansichts-Zustand einmalig entfernen (nicht nur ignorieren).
  useEffect(() => {
    try {
      localStorage.removeItem(LEGACY_VIEW_STORAGE_KEY)
    } catch {
      // localStorage nicht verfügbar — kein Hard-Fail
    }
  }, [])

  useEffect(() => {
    if (!validId) {
      return
    }
    let active = true
    void projectsApi.list().then((projects) => {
      if (!active) return
      const project = projects.find((p) => p.id === id)
      if (project) {
        setRole(project.role)
        setProjectName(project.name)
      }
    })
    return () => {
      active = false
    }
  }, [id, validId])

  // Live nachziehen: der Server pusht per SSE, sobald sich der Ideen-Pool des Projekts ändert
  // (Ingest oder andere Nutzer). Der Impuls bewegt refreshKey, das IdeaPlanningBoard lädt neu.
  useProjectIdeaEvents(id, () => setRefreshKey((n) => n + 1))

  const handleCreate = async (input: NewItemInput) => {
    await ideasApi.create(id, { title: input.title, description: input.description })
    setRefreshKey((n) => n + 1)
  }

  // Die Spezifikationsdatei wird ausschließlich im Browser gelesen — kein Upload, kein
  // Objektspeicher, und die Quelldatei bleibt unangetastet (Issue #493).
  const readSpecFile = async (file: File) => {
    try {
      setSpec({ fileName: file.name, markdown: await readTextFile(file) })
    } catch {
      notify('Die Datei konnte nicht gelesen werden.', 'error')
    }
  }

  const handleSpecImport = async (ideas: SpecIdea[]) => {
    const created = await ideasApi.createBatch(id, { ideas })
    setRefreshKey((n) => n + 1)
    notify(`${created.length} ${created.length === 1 ? 'Idee' : 'Ideen'} angelegt.`, 'success')
  }

  if (!validId) {
    return <Alert severity="error">Ungültige Projekt-ID.</Alert>
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/' },
            { label: projectName || 'Projekt', to: `/projects/${id}` },
            { label: 'Ideen' },
          ]}
        />
        {canEdit && (
          <Stack direction="row" spacing={1}>
            {/* Dateiauswahl wie beim Anhang-Upload (CardDetailModal): Button als <label> mit
                verstecktem Input — hier zusätzlich auf Markdown eingeschränkt. */}
            <Button variant="outlined" component="label">
              Spezifikation einlesen
              <input
                hidden
                type="file"
                accept=".md,.markdown,text/markdown"
                aria-label="Markdown-Datei auswählen"
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  // Zurücksetzen, damit dieselbe Datei erneut gewählt werden kann — sonst bleibt
                  // `change` beim zweiten Mal aus, weil sich der Wert nicht ändert.
                  e.target.value = ''
                  if (file) void readSpecFile(file)
                }}
              />
            </Button>
            <Button variant="contained" onClick={() => setCreateOpen(true)}>
              Idee anlegen
            </Button>
          </Stack>
        )}
      </Stack>

      <Box sx={{ mb: 2 }}>
        <TextField
          size="small"
          label="Suche"
          value={textFilter}
          onChange={(e) => setTextFilter(e.target.value)}
          slotProps={{ htmlInput: { 'aria-label': 'Ideen durchsuchen' } }}
        />
      </Box>

      <IdeaPlanningBoard projectId={id} canEdit={canEdit} filter={textFilter} refreshKey={refreshKey} />

      <NewCardModal
        open={createOpen}
        columnName=""
        epics={[]}
        ideaOnly
        onClose={() => setCreateOpen(false)}
        onSubmit={handleCreate}
      />

      <SpecImportDialog
        open={spec !== null}
        fileName={spec?.fileName ?? ''}
        markdown={spec?.markdown ?? ''}
        onClose={() => setSpec(null)}
        onImport={handleSpecImport}
      />
    </Box>
  )
}
