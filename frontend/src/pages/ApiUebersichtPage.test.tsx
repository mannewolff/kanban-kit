import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { openapiApi } from '../api/openapi'
import { ABGEBROCHEN, AUSPROBIER_HEADER, HINWEIS_AENDERND } from '../lib/apiAusprobieren'
import { ApiUebersichtPage } from './ApiUebersichtPage'

// Rolle je Test umschaltbar: Default USER (Nicht-Admin, Seite rein lesend wie in #1410).
const authState = vi.hoisted(() => ({ user: { platformRole: 'USER' } as { platformRole: string } }))
vi.mock('../auth/AuthContext', () => ({ useAuth: () => authState }))

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
    authState.user = { platformRole: 'USER' }
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

  it('Nicht-Admin: supportedSubmitMethods [] ohne Interceptor und ohne Erklärsatz', async () => {
    renderPage()
    await screen.findByTestId('swagger-ui')

    expect(swaggerProps.zuletzt?.supportedSubmitMethods).toEqual([])
    expect(swaggerProps.zuletzt).not.toHaveProperty('requestInterceptor')
    expect(screen.queryByText(/Aufrufe mit Anmeldung laufen mit Ihrer Sitzung/)).not.toBeInTheDocument()
  })

  describe('als Plattform-Admin', () => {
    type Anfrage = { url: string; method: string; headers: Record<string, string> }
    type Interceptor = (anfrage: Anfrage) => Anfrage | Promise<Anfrage>
    type Execute = (
      original: (props: { method: string }) => React.JSX.Element,
    ) => (props: { method: string }) => React.JSX.Element

    beforeEach(() => {
      authState.user = { platformRole: 'ADMIN' }
    })

    async function ladeAlsAdmin() {
      renderPage()
      await screen.findByTestId('swagger-ui')
      return swaggerProps.zuletzt as Record<string, unknown>
    }

    it('bietet alle Methoden zum Ausprobieren an', async () => {
      const props = await ladeAlsAdmin()

      expect(props.supportedSubmitMethods).toEqual([
        'get',
        'put',
        'post',
        'delete',
        'options',
        'head',
        'patch',
        'trace',
      ])
      expect(props.spec).toEqual(spec)
    })

    it('Admin: persistAuthorization false, Interceptor gesetzt', async () => {
      const props = await ladeAlsAdmin()

      expect(props.persistAuthorization).toBe(false)
      expect(props.requestInterceptor).toEqual(expect.any(Function))
    })

    it('blendet den Authorize-Dialog nicht aus und setzt das Plugin für execute', async () => {
      const props = await ladeAlsAdmin()

      const plugins = props.plugins as { wrapComponents: Record<string, unknown> }[]
      expect(plugins).toHaveLength(1)
      expect(plugins[0].wrapComponents).not.toHaveProperty('authorizeBtn')
      expect(plugins[0].wrapComponents.execute).toEqual(expect.any(Function))
    })

    it('zeigt den Erklärsatz über der Swagger-Fläche', async () => {
      await ladeAlsAdmin()

      const satz = screen.getByText(
        'Aufrufe mit Anmeldung laufen mit Ihrer Sitzung; ein Projekt-Token geben Sie unter ‚Authorize‘ bei projektToken an.',
      )
      expect(satz.compareDocumentPosition(screen.getByTestId('swagger-ui'))).toBe(
        Node.DOCUMENT_POSITION_FOLLOWING,
      )
    })

    it('GET: gibt die markierte Anfrage ohne Rückfrage zurück', async () => {
      const props = await ladeAlsAdmin()
      const interceptor = props.requestInterceptor as Interceptor

      const ergebnis = interceptor({ url: '/api/projects', method: 'GET', headers: { Accept: 'x' } })

      expect(ergebnis).toEqual({
        url: '/api/projects',
        method: 'GET',
        headers: { Accept: 'x', [AUSPROBIER_HEADER]: '1' },
      })
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('POST + Absenden → markierte Anfrage', async () => {
      const props = await ladeAlsAdmin()
      const interceptor = props.requestInterceptor as Interceptor

      let ergebnis!: Anfrage | Promise<Anfrage>
      act(() => {
        ergebnis = interceptor({
          url: 'https://localhost/api/projects?x=1',
          method: 'POST',
          headers: {},
        })
      })

      const dialog = await screen.findByRole('dialog', { name: 'Echte Daten ändern?' })
      expect(dialog).toHaveTextContent('POST')
      expect(dialog).toHaveTextContent('/api/projects?x=1')
      await userEvent.click(screen.getByRole('button', { name: 'Absenden' }))

      await expect(ergebnis).resolves.toEqual({
        url: 'https://localhost/api/projects?x=1',
        method: 'POST',
        headers: { [AUSPROBIER_HEADER]: '1' },
      })
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('POST + Abbrechen → ABGEBROCHEN', async () => {
      const props = await ladeAlsAdmin()
      const interceptor = props.requestInterceptor as Interceptor

      let ergebnis!: Anfrage | Promise<Anfrage>
      act(() => {
        ergebnis = interceptor({ url: '/api/projects/7', method: 'DELETE', headers: {} })
      })
      await screen.findByRole('dialog')
      const abgewiesen = expect(ergebnis).rejects.toThrow(ABGEBROCHEN)
      await userEvent.click(screen.getByRole('button', { name: 'Abbrechen' }))

      await abgewiesen
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    })

    it('Hinweis bei post, nicht bei get', async () => {
      const props = await ladeAlsAdmin()
      const plugins = props.plugins as { wrapComponents: { execute: Execute } }[]
      const Original = ({ method }: { method: string }) => <button type="button">Ausführen {method}</button>
      const Umhuellt = plugins[0].wrapComponents.execute(Original)

      const { unmount } = render(<Umhuellt method="post" />)
      const hinweis = screen.getByText(HINWEIS_AENDERND)
      const knopf = screen.getByRole('button', { name: 'Ausführen post' })
      expect(hinweis.compareDocumentPosition(knopf)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
      unmount()

      render(<Umhuellt method="get" />)
      expect(screen.getByRole('button', { name: 'Ausführen get' })).toBeInTheDocument()
      expect(screen.queryByText(HINWEIS_AENDERND)).not.toBeInTheDocument()
    })
  })
})
