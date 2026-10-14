import Chip from '@mui/material/Chip'
import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, test, vi } from 'vitest'
import type { KitNamenZustand } from '../api/kitNamen'
import { de } from '../i18n/de'
import { en } from '../i18n/en'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { TabletQuerProvider } from '../lib/tabletQuer'
import { KIT_NAMEN } from '../test/kitNamen'
import { setzeMedien } from '../test/setup'
import { LabelErklaerung, LabelErklaerungInfo } from './LabelErklaerung'

const kitNamen = vi.hoisted(() => ({ zustand: null as KitNamenZustand | null }))
vi.mock('../api/kitNamen', async (original) => ({
  ...(await original<typeof import('../api/kitNamen')>()),
  useKitNamen: (): KitNamenZustand => kitNamen.zustand ?? { status: 'fertig', daten: KIT_NAMEN },
}))

const zeigen = (name: string, sprache: 'de' | 'en' = 'de', open?: boolean) =>
  renderMitSprache(
    <LabelErklaerung name={name} open={open}>
      <Chip size="small" label={name} />
    </LabelErklaerung>,
    sprache,
  )

/** Das Label als Element, das Tooltip und Fokus trägt — der Chip, nicht sein Text. */
// eslint-disable-next-line testing-library/no-node-access -- ein Chip hat keine Rolle; Tooltip und tabindex sitzen an seiner Wurzel, nicht am Text
const label = (name: string) => screen.getByText(name).closest('.MuiChip-root') as HTMLElement

beforeEach(() => {
  kitNamen.zustand = null
})

test('Szenario: Englische Oberfläche', async () => {
  // Angenommen der Leitstand ist auf Englisch eingestellt
  zeigen('goal:plan', 'en')

  // Wenn der Nutzer mit der Maus über `goal:plan` fährt
  await userEvent.hover(label('goal:plan'))

  // Dann erscheint die Erklärung auf Englisch
  const tooltip = await screen.findByRole('tooltip')
  expect(tooltip).toHaveTextContent(en.LABEL_ERKLAERUNG.SACHEN.ZIEL_PLAN.bedeutung)
  expect(tooltip).toHaveTextContent(en.LABEL_ERKLAERUNG.SACHEN.ZIEL_PLAN.setzt)
  expect(tooltip).not.toHaveTextContent(de.LABEL_ERKLAERUNG.SACHEN.ZIEL_PLAN.bedeutung)
})

describe('LabelErklaerung', () => {
  it('macht ein Systemlabel fokussierbar und beschreibt es, statt es umzubenennen', async () => {
    zeigen('kit:clarify')

    const chip = label('kit:clarify')
    expect(chip).toHaveAttribute('tabindex', '0')
    await userEvent.hover(chip)
    const tooltip = await screen.findByRole('tooltip')
    expect(chip).toHaveAttribute('aria-describedby', tooltip.id)
    expect(chip).not.toHaveAttribute('aria-label')
  })

  it('zeigt jeden Teil der Erklärung als eigenen Absatz in seiner Reihenfolge', async () => {
    zeigen('kit:clarify')

    await userEvent.hover(label('kit:clarify'))

    const absaetze = within(await screen.findByRole('tooltip')).getAllByText(/.+/)
    const k = de.LABEL_ERKLAERUNG.SACHEN.KLAEREN
    expect(absaetze.map((p) => p.tagName)).toEqual(['P', 'P', 'P', 'P', 'P'])
    expect(absaetze.map((p) => p.textContent)).toEqual([
      k.bedeutung,
      k.setzt,
      k.verschwindet,
      de.LABEL_ERKLAERUNG.SCHUTZ.NUR_MENSCH_NIMMT_AB,
      k.handlung,
    ])
  })

  it('lässt ein selbst angelegtes Label unverändert und ohne Tooltip', async () => {
    zeigen('Aktuell')

    const chip = label('Aktuell')
    expect(chip).not.toHaveAttribute('tabindex')
    await userEvent.hover(chip)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it.each<[string, KitNamenZustand]>([
    ['lädt', { status: 'laden' }],
    ['nicht geladen werden konnte', { status: 'fehler', fehler: new Error('weg') }],
  ])('lässt ein Systemlabel unverändert und ohne Tooltip, solange das Register %s', async (_, zustand) => {
    kitNamen.zustand = zustand
    zeigen('kit:clarify')

    const chip = label('kit:clarify')
    expect(chip).not.toHaveAttribute('tabindex')
    await userEvent.hover(chip)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('öffnet das Tooltip gesteuert, ohne Maus und ohne Fokus', async () => {
    zeigen('run:running', 'de', true)

    expect(await screen.findByRole('tooltip')).toHaveTextContent(de.LABEL_ERKLAERUNG.SACHEN.LAUF_LAEUFT.bedeutung)
  })

  it('hält das Tooltip gesteuert geschlossen, auch beim Darüberfahren', async () => {
    zeigen('run:running', 'de', false)

    await userEvent.hover(label('run:running'))

    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it.each(['kit:clarify', 'Aktuell'])('lässt mit eigenerFokus den tabIndex von %s beim Kind und reicht tabIndex durch', (name) => {
    renderMitSprache(
      <LabelErklaerung name={name} eigenerFokus tabIndex={-1}>
        <Chip size="small" label={name} />
      </LabelErklaerung>,
      'de',
    )

    expect(label(name)).toHaveAttribute('tabindex', '-1')
  })

  it('öffnet das Tooltip an der verlangten Seite', async () => {
    renderMitSprache(
      <LabelErklaerung name="kit:clarify" placement="right">
        <Chip size="small" label="kit:clarify" />
      </LabelErklaerung>,
      'de',
    )

    await userEvent.hover(label('kit:clarify'))

    expect(await screen.findByRole('tooltip')).toHaveAttribute('data-popper-placement', expect.stringMatching(/^right/))
  })

  it('öffnet auf dem Tablet quer beim Antippen und reicht das Antippen nicht weiter (Plan #1707, E7)', async () => {
    setzeMedien({ tabletQuer: true })
    const elternKlick = vi.fn()
    renderMitSprache(
      <TabletQuerProvider>
        {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- Attrappe der Karte, nur ihr Klick zählt */}
        <div onClick={elternKlick}>
          <LabelErklaerung name="kit:clarify">
            <Chip size="small" label="kit:clarify" />
          </LabelErklaerung>
        </div>
      </TabletQuerProvider>,
      'de',
    )

    await userEvent.hover(label('kit:clarify'))
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()

    fireEvent.click(label('kit:clarify'))

    expect(await screen.findByRole('tooltip')).toHaveTextContent(de.LABEL_ERKLAERUNG.SACHEN.KLAEREN.bedeutung)
    expect(elternKlick).not.toHaveBeenCalled()
  })

  it('lässt auf dem Tablet quer ein selbst angelegtes Label das Antippen weiterreichen', () => {
    setzeMedien({ tabletQuer: true })
    const elternKlick = vi.fn()
    renderMitSprache(
      <TabletQuerProvider>
        {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- Attrappe der Karte, nur ihr Klick zählt */}
        <div onClick={elternKlick}>
          <LabelErklaerung name="Aktuell">
            <Chip size="small" label="Aktuell" />
          </LabelErklaerung>
        </div>
      </TabletQuerProvider>,
      'de',
    )

    fireEvent.click(label('Aktuell'))

    expect(elternKlick).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
  })

  it('lässt auf dem Tablet quer einem Element mit eigener Aktion das Antippen (Plan #1707, E7)', () => {
    setzeMedien({ tabletQuer: true })
    const elternKlick = vi.fn()
    renderMitSprache(
      <TabletQuerProvider>
        {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions -- Attrappe des Eintrags, nur sein Klick zählt */}
        <div onClick={elternKlick}>
          <LabelErklaerung name="kit:clarify" eigeneAktion>
            <Chip size="small" label="kit:clarify" />
          </LabelErklaerung>
        </div>
      </TabletQuerProvider>,
      'de',
    )

    fireEvent.click(label('kit:clarify'))

    expect(elternKlick).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('tooltip')).not.toBeInTheDocument()
    expect(label('kit:clarify')).not.toHaveAttribute('tabindex')
  })

  it('erklärt ein Element mit eigener Aktion am Desktop beim Darüberfahren wie heute', async () => {
    renderMitSprache(
      <TabletQuerProvider>
        <LabelErklaerung name="kit:clarify" eigeneAktion>
          <Chip size="small" label="kit:clarify" />
        </LabelErklaerung>
        <LabelErklaerungInfo name="kit:clarify" />
      </TabletQuerProvider>,
      'de',
    )

    expect(screen.queryByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('kit:clarify') })).not.toBeInTheDocument()
    await userEvent.hover(label('kit:clarify'))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(de.LABEL_ERKLAERUNG.SACHEN.KLAEREN.bedeutung)
  })

  it('öffnet auf dem Tablet quer per Info-Symbol die Erklärung eines Systemlabels, ein eigenes Label hat keins', () => {
    setzeMedien({ tabletQuer: true })
    renderMitSprache(
      <TabletQuerProvider>
        <LabelErklaerungInfo name="kit:clarify" placement="right" />
        <LabelErklaerungInfo name="Aktuell" />
      </TabletQuerProvider>,
      'de',
    )

    expect(screen.getAllByRole('button')).toHaveLength(1)
    fireEvent.click(screen.getByRole('button', { name: de.ERKLAERUNG_INFO.ZEIGEN('kit:clarify') }))

    const tooltip = screen.getByRole('tooltip')
    expect(tooltip).toHaveTextContent(de.LABEL_ERKLAERUNG.SACHEN.KLAEREN.bedeutung)
    expect(tooltip).toHaveAttribute('data-popper-placement', expect.stringMatching(/^right/))
  })
})
