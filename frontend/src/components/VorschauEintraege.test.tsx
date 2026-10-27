import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import type { VorschauEintrag } from '../api/auth'
import { formatierer } from '../i18n/format'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { VorschauEintraege } from './VorschauEintraege'

const beendet: VorschauEintrag = {
  adminName: 'Ada',
  adminKontoGeloescht: false,
  gestartetAm: '2026-10-26T09:00:00Z',
  laeuftAbAm: '2026-10-26T09:30:00Z',
  beendetAm: '2026-10-26T09:12:00Z',
  endgrund: 'BEENDET',
}
const de = formatierer('de').datumUhrzeit

describe('VorschauEintraege', () => {
  it('nennt je Eintrag Admin, Start, Ende und Endgrund', async () => {
    renderMitSprache(<VorschauEintraege laden={vi.fn().mockResolvedValue([beendet])} />)

    const eintrag = await screen.findByRole('listitem')
    expect(eintrag).toHaveTextContent('Ada')
    expect(eintrag).toHaveTextContent(`Start ${de('2026-10-26T09:00:00Z')}`)
    expect(eintrag).toHaveTextContent(`Ende ${de('2026-10-26T09:12:00Z')}`)
    expect(eintrag).toHaveTextContent('beendet')
    expect(eintrag).not.toHaveTextContent('Konto gelöscht')
  })

  it('vermerkt einen gelöschten Admin mit Namen und „Konto gelöscht“', async () => {
    const geloescht = { ...beendet, adminKontoGeloescht: true, endgrund: 'ABLAUF' as const }
    renderMitSprache(<VorschauEintraege laden={vi.fn().mockResolvedValue([geloescht])} />)

    const eintrag = await screen.findByRole('listitem')
    expect(eintrag).toHaveTextContent('Ada (Konto gelöscht)')
    expect(eintrag).toHaveTextContent('nach 30 Minuten abgelaufen')
  })

  it('zeigt eine laufende Ansicht ohne Ende als „läuft noch“', async () => {
    const laeuft = { ...beendet, beendetAm: null, endgrund: null }
    renderMitSprache(<VorschauEintraege laden={vi.fn().mockResolvedValue([laeuft])} />)

    const eintrag = await screen.findByRole('listitem')
    expect(eintrag).toHaveTextContent('läuft noch')
    expect(eintrag).not.toHaveTextContent('Ende')
  })

  it('sagt, wenn es keine Einträge gibt', async () => {
    renderMitSprache(<VorschauEintraege laden={vi.fn().mockResolvedValue([])} />)
    expect(await screen.findByText('Bisher hat kein Admin die App als dieses Konto angesehen.')).toBeInTheDocument()
    expect(screen.queryByRole('list')).not.toBeInTheDocument()
  })

  it('meldet einen Ladefehler', async () => {
    renderMitSprache(<VorschauEintraege laden={vi.fn().mockRejectedValue(new Error('netz'))} />)
    expect(await screen.findByText('Die Einträge ließen sich nicht laden.')).toBeInTheDocument()
  })

  it('beschriftet die Einträge auf Englisch', async () => {
    const geloescht = { ...beendet, adminKontoGeloescht: true }
    renderMitSprache(<VorschauEintraege laden={vi.fn().mockResolvedValue([geloescht])} />, 'en')

    const eintrag = await screen.findByRole('listitem')
    const en = formatierer('en').datumUhrzeit
    expect(eintrag).toHaveTextContent('Ada (account deleted)')
    expect(eintrag).toHaveTextContent(`Start ${en('2026-10-26T09:00:00Z')}`)
    expect(eintrag).toHaveTextContent(`End ${en('2026-10-26T09:12:00Z')}`)
    expect(eintrag).toHaveTextContent('ended')
  })
})
