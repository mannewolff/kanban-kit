import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { renderMitSprache } from '../../i18n/renderMitSprache'
import { MobilRahmen } from './MobilRahmen'

const auth = vi.hoisted(() => ({ logout: vi.fn() }))
vi.mock('../../auth/AuthContext', () => ({ useAuth: () => auth }))

function seite(pfad = '/mobil') {
  return renderMitSprache(
    <MemoryRouter initialEntries={[pfad]}>
      <Routes>
        <Route path="/mobil" element={<MobilRahmen />}>
          <Route index element={<p>Ideenseite</p>} />
          <Route path="laeufe" element={<p>Läufeseite</p>} />
          <Route path="laeufe/:laufId" element={<p>Laufseite</p>} />
          <Route path="einstellungen" element={<p>Einstellungsseite</p>} />
        </Route>
        <Route path="/login" element={<p>Anmeldung</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

/** Die Kopfangaben für den Startbildschirm; der Dokumentkopf ist nur über das DOM erreichbar. */
const kopf = () => {
  const finde = (selektor: string) =>
    // eslint-disable-next-line testing-library/no-node-access -- der Dokumentkopf hat keine Rollen
    document.head.querySelector(selektor)
  return {
    manifest: finde('link[rel="manifest"]'),
    themeColor: finde('meta[name="theme-color"]'),
    capable: finde('meta[name="apple-mobile-web-app-capable"]'),
  }
}

describe('MobilRahmen', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('setzt beim Einhängen die Kopfangaben für den Startbildschirm und entfernt sie beim Aushängen', () => {
    expect(kopf().manifest).toBeNull()
    const { unmount } = seite()
    const { manifest, themeColor, capable } = kopf()
    expect(manifest).toHaveAttribute('href', '/mobil-manifest.json')
    expect(themeColor?.getAttribute('content')).toMatch(/^#[0-9A-Fa-f]{6}$/)
    expect(capable).toHaveAttribute('content', 'yes')
    unmount()
    expect(kopf()).toEqual({ manifest: null, themeColor: null, capable: null })
  })

  it('zeigt die Seite des Pfads und wechselt über die Leiste unten', async () => {
    seite()
    expect(screen.getByText('Ideenseite')).toBeInTheDocument()
    const leiste = screen.getByRole('navigation', { name: 'Mobile Navigation' })
    expect(leiste).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Läufe' }))
    expect(screen.getByText('Läufeseite')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Einstellungen' }))
    expect(screen.getByText('Einstellungsseite')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('link', { name: 'Idee' }))
    expect(screen.getByText('Ideenseite')).toBeInTheDocument()
  })

  it('markiert „Läufe“ auch beim geöffneten Lauf', () => {
    seite('/mobil/laeufe/12')
    expect(screen.getByText('Laufseite')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Läufe' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Idee' })).not.toHaveAttribute('aria-current')
  })

  it('markiert den aktiven Eintrag der Leiste', () => {
    seite('/mobil/einstellungen')
    expect(screen.getByRole('link', { name: 'Einstellungen' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('link', { name: 'Idee' })).not.toHaveAttribute('aria-current')
  })

  it('meldet ab und führt zur Anmeldung', async () => {
    auth.logout.mockResolvedValue(undefined)
    seite()
    await userEvent.click(screen.getByRole('button', { name: 'Abmelden' }))
    await waitFor(() => expect(auth.logout).toHaveBeenCalledTimes(1))
    expect(await screen.findByText('Anmeldung')).toBeInTheDocument()
  })

  it('bleibt angemeldet und sagt es, wenn das Abmelden scheitert', async () => {
    auth.logout.mockRejectedValue(new TypeError('Failed to fetch'))
    seite()
    await userEvent.click(screen.getByRole('button', { name: 'Abmelden' }))
    expect(await screen.findByText('Abmelden fehlgeschlagen.')).toBeInTheDocument()
    expect(screen.getByText('Ideenseite')).toBeInTheDocument()
  })

  it('bietet keinen Knopf zum Verschieben, Bearbeiten, Archivieren oder Löschen', () => {
    seite()
    const beschriftungen = [...screen.getAllByRole('button'), ...screen.getAllByRole('link')].map(
      (e) => e.textContent ?? '',
    )
    expect(beschriftungen).toEqual(expect.arrayContaining(['Abmelden', 'Idee', 'Läufe', 'Einstellungen']))
    for (const text of beschriftungen) {
      expect(text).not.toMatch(/verschieb|bearbeit|archiv|lösch/i)
    }
    expect(beschriftungen).toHaveLength(4)
  })
})
