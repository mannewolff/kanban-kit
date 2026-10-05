import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { openapiApi } from '../api/openapi'
import { ApiUebersichtPage } from './ApiUebersichtPage'

vi.mock('../api/openapi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/openapi')>()),
  openapiApi: { lade: vi.fn() },
}))

// Attrappe statt echter Swagger UI (Entscheidung im Issue #1410): Sie macht die übergebenen Props
// sichtbar, denn die verantwortet die Seite — das Rendern der Spezifikation verantwortet Swagger UI.
const swaggerProps = vi.hoisted(() => ({ zuletzt: null as Record<string, unknown> | null }))
vi.mock('swagger-ui-react', () => ({
  default: (props: Record<string, unknown>) => {
    swaggerProps.zuletzt = props
    return <div data-testid="swagger-ui">{JSON.stringify(props.spec)}</div>
  },
}))

const mOpenapi = openapiApi as unknown as { lade: ReturnType<typeof vi.fn> }

const spec = { openapi: '3.1.0', info: { title: 'kanban-kit', version: '2.20.0' }, paths: {} }

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/administration/api']}>
      <Routes>
        <Route path="/administration/api" element={<ApiUebersichtPage />} />
        <Route path="/administration" element={<p>Administrationsseite</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ApiUebersichtPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    swaggerProps.zuletzt = null
    mOpenapi.lade.mockResolvedValue(spec)
  })

  it('zeigt Überschrift und führt mit dem Zurück-Knopf in die Administration', async () => {
    renderPage()
    await screen.findByTestId('swagger-ui')

    expect(screen.getByRole('heading', { level: 1, name: 'API-Schnittstelle' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Zurück zur Administration' }))

    expect(await screen.findByText('Administrationsseite')).toBeInTheDocument()
  })

  it('bietet die Beschreibung als JSON und YAML zum Herunterladen an', async () => {
    renderPage()
    await screen.findByTestId('swagger-ui')

    const json = screen.getByRole('link', { name: 'OpenAPI als JSON herunterladen' })
    expect(json).toHaveAttribute('href', '/api/openapi')
    expect(json).toHaveAttribute('download')
    const yaml = screen.getByRole('link', { name: 'OpenAPI als YAML herunterladen' })
    expect(yaml).toHaveAttribute('href', '/api/openapi.yaml')
    expect(yaml).toHaveAttribute('download')
  })

  it('zeigt den Ladezustand, solange die Spezifikation aussteht', () => {
    mOpenapi.lade.mockReturnValue(new Promise(() => {}))

    renderPage()

    expect(screen.getByRole('progressbar', { name: 'API-Beschreibung wird geladen' })).toBeInTheDocument()
    expect(screen.queryByTestId('swagger-ui')).not.toBeInTheDocument()
  })

  it('übergibt Swagger UI die geladene Spezifikation, ohne Ausprobieren', async () => {
    renderPage()

    expect(await screen.findByTestId('swagger-ui')).toHaveTextContent('"openapi":"3.1.0"')
    expect(mOpenapi.lade).toHaveBeenCalledTimes(1)
    expect(swaggerProps.zuletzt?.spec).toEqual(spec)
    expect(swaggerProps.zuletzt?.supportedSubmitMethods).toEqual([])
    expect(swaggerProps.zuletzt?.docExpansion).toBe('list')
    expect(swaggerProps.zuletzt).not.toHaveProperty('url')
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('blendet den Authorize-Dialog über ein Plugin aus', async () => {
    renderPage()
    await screen.findByTestId('swagger-ui')

    const plugins = swaggerProps.zuletzt?.plugins as {
      wrapComponents: { authorizeBtn: () => () => unknown }
    }[]
    expect(plugins).toHaveLength(1)
    expect(plugins[0].wrapComponents.authorizeBtn()()).toBeNull()
  })

  it('zeigt die Server-Meldung, wenn das Laden scheitert', async () => {
    mOpenapi.lade.mockRejectedValue(new ApiError(500, 'x', undefined, 'Beschreibung nicht verfügbar.'))

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('Beschreibung nicht verfügbar.')
    expect(screen.queryByTestId('swagger-ui')).not.toBeInTheDocument()
    expect(screen.queryByRole('progressbar')).not.toBeInTheDocument()
  })

  it('fällt ohne Server-Meldung auf den eigenen Fehlertext zurück', async () => {
    mOpenapi.lade.mockRejectedValue(new ApiError(502, '<html>Bad Gateway</html>'))

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Die API-Beschreibung konnte nicht geladen werden.',
    )
  })
})
