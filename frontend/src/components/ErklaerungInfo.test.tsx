import { fireEvent, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { TabletQuerProvider } from '../lib/tabletQuer'
import { setzeMedien } from '../test/setup'
import { ErklaerungOffenProvider } from './Erklaerung'
import { ErklaerungInfo } from './ErklaerungInfo'

/** Ein Eintrag mit eigener Aktion — etwa eine Option der Label-Auswahl — samt Info-Symbol daneben. */
const zeigen = (sprache: 'de' | 'en' = 'de') => {
  const eintragKlick = vi.fn()
  const nachbarKlick = vi.fn()
  renderMitSprache(
    <TabletQuerProvider>
      <ErklaerungOffenProvider>
        {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- Attrappe eines auswählbaren Eintrags */}
        <div onClick={eintragKlick}>
          <button type="button" onClick={nachbarKlick}>
            kit:clarify
          </button>
          <ErklaerungInfo name="kit:clarify" title="Die Erklärung des Labels" />
        </div>
      </ErklaerungOffenProvider>
    </TabletQuerProvider>,
    sprache,
  )
  return { eintragKlick, nachbarKlick }
}

describe('ErklaerungInfo auf dem Tablet im Querformat (Plan #1707, E7)', () => {
  it('zeigt ein Info-Symbol mit Beschriftung, dessen aria-expanded dem Zustand folgt', async () => {
    setzeMedien({ tabletQuer: true })
    zeigen()

    const info = screen.getByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('kit:clarify') })
    expect(info).toHaveAttribute('aria-expanded', 'false')

    fireEvent.click(info)
    const tooltip = await screen.findByRole('tooltip')
    expect(tooltip).toHaveTextContent('Die Erklärung des Labels')
    expect(info).toHaveAttribute('aria-expanded', 'true')
    expect(info).toHaveAttribute('aria-describedby', tooltip.id)

    fireEvent.click(info)
    await waitFor(() => expect(screen.queryByRole('tooltip')).not.toBeInTheDocument())
    expect(info).toHaveAttribute('aria-expanded', 'false')
  })

  it('löst beim Antippen die Aktion des Nachbarn nicht aus', async () => {
    setzeMedien({ tabletQuer: true })
    const { eintragKlick, nachbarKlick } = zeigen()

    fireEvent.click(screen.getByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('kit:clarify') }))

    expect(await screen.findByRole('tooltip')).toBeInTheDocument()
    expect(eintragKlick).not.toHaveBeenCalled()
    expect(nachbarKlick).not.toHaveBeenCalled()
  })

  it('beschriftet das Symbol auf Englisch', () => {
    setzeMedien({ tabletQuer: true })
    zeigen('en')

    expect(screen.getByRole('button', { name: en.ERKLAERUNG_INFO.ZEIGEN('kit:clarify') })).toBeInTheDocument()
  })
})

describe('ErklaerungInfo am Desktop', () => {
  it('rendert nichts', () => {
    zeigen()

    expect(screen.getAllByRole('button')).toHaveLength(1)
    expect(screen.queryByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('kit:clarify') })).not.toBeInTheDocument()
  })
})
