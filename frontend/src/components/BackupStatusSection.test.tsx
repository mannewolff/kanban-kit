import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { BackupKindStatus, BackupStatus, BackupVerdict } from '../api/backup'
import { ApiError } from '../api/client'
import { AMPEL } from '../theme'
import { BackupStatusSection } from './BackupStatusSection'

/**
 * Die Statuskachel der Sicherung (Issue #833, Plan #825 E6/E11). Geprüft wird, was der Betreiber
 * ohne Suchen erkennt: den Zustand — auch ohne Farbwahrnehmung —, das Alter der letzten gelungenen
 * Sicherung, das Ziel, und die beiden Hinweise, die sonst niemand ausspricht.
 */

function art(kind: BackupKindStatus['kind'], letzterErfolg: string | null): BackupKindStatus {
  return {
    kind,
    lastStartedAt: letzterErfolg,
    lastFinishedAt: letzterErfolg,
    lastOutcome: letzterErfolg === null ? null : 'ERFOLG',
    detail: null,
    bytes: 4096,
    lastSuccessAt: letzterErfolg,
    ageSeconds: 3600,
    warnAfterSeconds: 172_800,
    stale: false,
  }
}

function stand(felder: Partial<BackupStatus> = {}): BackupStatus {
  return {
    verdict: 'OK',
    enabled: true,
    alertMailEnabled: true,
    targetLabel: 'Nextcloud',
    // Absichtlich nicht chronologisch: der jüngste Erfolg zählt, nicht der zuletzt gelieferte.
    kinds: [art('BASIS', '2026-09-23T03:00:00Z'), art('WAL', '2026-09-23T01:00:00Z')],
    ...felder,
  }
}

function api(ergebnis: Promise<BackupStatus>) {
  return { getStatus: vi.fn().mockReturnValue(ergebnis) }
}

function zeige(felder: Partial<BackupStatus> = {}) {
  render(<BackupStatusSection api={api(Promise.resolve(stand(felder)))} />)
}

describe('BackupStatusSection', () => {
  it('zeigt beim Laden einen Hinweis und noch keinen Zustand', () => {
    render(<BackupStatusSection api={api(new Promise(() => {}))} />)

    expect(screen.getByRole('heading', { name: 'Sicherung' })).toBeInTheDocument()
    expect(screen.getByLabelText('Stand der Sicherung wird geladen')).toBeInTheDocument()
    expect(screen.queryByTestId('sicherung-ampel')).not.toBeInTheDocument()
  })

  it('zeigt Zustand, letzten gelungenen Lauf und Ziel', async () => {
    zeige()

    expect(await screen.findByText('Läuft')).toBeInTheDocument()
    // Der jüngste Erfolg über alle Arten, in deutscher Ortszeit (Tests laufen in Europe/Berlin).
    expect(screen.getByText('Letzter gelungener Lauf: 23.09.2026, 05:00')).toBeInTheDocument()
    expect(screen.getByText('Ziel: Nextcloud')).toBeInTheDocument()
  })

  it('sagt es, wenn es noch keinen gelungenen Lauf gibt', async () => {
    zeige({ verdict: 'VERALTET', kinds: [art('BASIS', null), art('WAL', null)] })

    expect(await screen.findByText('Letzter gelungener Lauf: noch keiner')).toBeInTheDocument()
  })

  const ampeln: ReadonlyArray<readonly [BackupVerdict, string, string]> = [
    ['OK', 'Läuft', AMPEL.green],
    ['VERALTET', 'Veraltet', AMPEL.yellow],
    ['FEHLGESCHLAGEN', 'Fehlgeschlagen', AMPEL.red],
    ['ABGESCHALTET', 'Abgeschaltet', AMPEL.grey],
  ]

  it.each(ampeln)('%s brennt in der eigenen Farbe und nennt sich beim Namen', async (verdict, text, farbe) => {
    zeige({ verdict, enabled: verdict !== 'ABGESCHALTET' })

    expect(await screen.findByText(text)).toBeInTheDocument()
    expect(screen.getByTestId('sicherung-ampel')).toHaveStyle({ backgroundColor: farbe })
  })

  it.each(ampeln)('%s steht im zugänglichen Namen des Bereichs, nicht nur in der Farbe', async (verdict, text) => {
    zeige({ verdict, enabled: verdict !== 'ABGESCHALTET' })

    expect(await screen.findByRole('region', { name: `Sicherung — ${text}` })).toBeInTheDocument()
    // Der Ampelpunkt selbst ist reine Dekoration und trägt nichts zum Namen bei.
    expect(screen.getByTestId('sicherung-ampel')).toHaveAttribute('aria-hidden', 'true')
  })

  it('weist bei abgeschalteter Sicherung unübersehbar darauf hin und verlinkt die Betriebsdoku', async () => {
    zeige({ verdict: 'ABGESCHALTET', enabled: false, alertMailEnabled: false })

    const hinweis = await screen.findByRole('alert')
    expect(hinweis).toHaveTextContent('Keine Sicherung eingerichtet')
    expect(screen.getByRole('link', { name: 'Sicherung einrichten' })).toHaveAttribute(
      'href',
      '/docs/backup',
    )
  })

  it('sagt bei abgeschaltetem Mailversand, dass der Alarm nur ins Protokoll geht', async () => {
    zeige({ alertMailEnabled: false })

    const hinweis = await screen.findByRole('alert')
    expect(hinweis).toHaveTextContent('Der Mailversand ist abgeschaltet')
    expect(hinweis).toHaveTextContent('Diese Ansicht ist der verlässliche Weg.')
  })

  it('schweigt zum Mailversand, solange er eingeschaltet ist', async () => {
    zeige()

    expect(await screen.findByText('Läuft')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('zeigt die Server-Meldung, wenn der Abruf abgelehnt wird', async () => {
    render(
      <BackupStatusSection
        api={api(Promise.reject(new ApiError(403, 'Forbidden', undefined, 'Kein Admin-Zugriff.')))}
      />,
    )

    expect(await screen.findByText('Kein Admin-Zugriff.')).toBeInTheDocument()
    expect(screen.queryByTestId('sicherung-ampel')).not.toBeInTheDocument()
  })

  it('zeigt ohne Server-Meldung den eigenen Text', async () => {
    render(<BackupStatusSection api={api(Promise.reject(new TypeError('Failed to fetch')))} />)

    expect(await screen.findByText('Stand der Sicherung konnte nicht geladen werden.')).toBeInTheDocument()
  })

  it('schreibt nach dem Verlassen der Seite keinen Zustand mehr', async () => {
    const fehler = vi.spyOn(console, 'error').mockImplementation(() => {})
    let liefern: (s: BackupStatus) => void = () => {}
    let scheitern: (grund: Error) => void = () => {}
    const { unmount } = render(
      <BackupStatusSection api={api(new Promise((resolve) => (liefern = resolve)))} />,
    )
    const { unmount: unmountNachFehler } = render(
      <BackupStatusSection api={api(new Promise((_, reject) => (scheitern = reject)))} />,
    )
    unmount()
    unmountNachFehler()

    liefern(stand())
    scheitern(new Error('zu spät'))
    await Promise.resolve()

    expect(fehler).not.toHaveBeenCalled()
    fehler.mockRestore()
  })
})
