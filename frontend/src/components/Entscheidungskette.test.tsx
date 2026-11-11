import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import type {
  AkteurView,
  EntscheidungsketteView,
  GliedView,
  Nachfahre,
  Verweis,
} from '../api/entscheidungskette'
import { renderMitSprache } from '../i18n/renderMitSprache'
import { Entscheidungskette } from './Entscheidungskette'

/** Der Abschnitt „Entscheidungskette“ im Kartendialog (Issue #2118, Plan #2108 E7, E8, E15, E16). */

const KEINER: Verweis = { karte: null, nummer: null, titel: null, oeffenbar: false, kommentar: null }
const MANFRED: AkteurView = { typ: 'MENSCH', name: 'Manfred', modell: null }
const RUNNER: AkteurView = { typ: 'WERKZEUG', name: 'runner-token', modell: 'claude-opus-5-5' }
const ANGABE_LEER = { wert: null, zeitpunkt: null }

const glied = (werte: Partial<GliedView>): GliedView => ({
  art: 'HERKUNFT',
  zustand: 'ERFASST',
  freigegebenVon: null,
  ausgefuehrtVon: null,
  zeitpunkt: null,
  ergebnis: null,
  quelle: KEINER,
  hinweise: [],
  pruefung: null,
  freigabe: null,
  umsetzung: null,
  codeReview: null,
  uebertragung: null,
  veroeffentlichung: null,
  ...werte,
})

const kette = (glieder: GliedView[], nachfahren: Nachfahre[] = []): EntscheidungsketteView => ({
  sichtbar: true,
  art: 'PAKET',
  abschnitte: [{ art: 'PAKET', glieder }],
  nachfahren,
})

const uebertragung = (quelle: string | null) =>
  glied({
    art: 'UEBERTRAGUNG',
    uebertragung: {
      versuch: null,
      commit: 'abc1234',
      basis: null,
      weg: 'BUILD_DIENST',
      prueflauf: { wert: 'gruen', zeitpunkt: null },
      buildDienst: { wert: 'ausstehend', zeitpunkt: null },
      quelle,
      ausgang: 'übertragen',
      freigegebenVon: ANGABE_LEER,
      ausgefuehrtVon: ANGABE_LEER,
    },
  })

/** Rendert den Abschnitt und klappt ihn auf. */
function offen(daten: EntscheidungsketteView, onOpenCard?: (nummer: number) => void) {
  render(<Entscheidungskette kette={daten} onOpenCard={onOpenCard} />)
  fireEvent.click(screen.getByRole('button', { name: 'Entscheidungskette' }))
}

describe('Entscheidungskette', () => {
  it('steht zugeklappt da und klappt per Tastatur auf und zu', async () => {
    render(<Entscheidungskette kette={kette([glied({ ergebnis: 'Fachplan geschrieben' })])} />)
    const knopf = screen.getByRole('button', { name: 'Entscheidungskette' })
    expect(knopf).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Fachplan geschrieben')).not.toBeInTheDocument()

    await userEvent.tab()
    expect(knopf).toHaveFocus()
    await userEvent.keyboard('{Enter}')
    expect(knopf).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByText('Fachplan geschrieben')).toBeInTheDocument()
    expect(knopf).toHaveAttribute('aria-controls')

    await userEvent.keyboard(' ')
    expect(knopf).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByText('Fachplan geschrieben')).not.toBeInTheDocument()
  })

  it('zeigt ein erfasstes Glied mit Akteur, Zeitpunkt, Ergebnis und Quelle', () => {
    offen(
      kette([
        glied({
          art: 'HERKUNFT',
          ausgefuehrtVon: { typ: 'MODELL', name: 'claude-opus-5-5', modell: null },
          zeitpunkt: '2026-11-10T10:00:00Z',
          ergebnis: 'Plan geschrieben',
          quelle: { karte: 81, nummer: 12, titel: 'Der Plan', oeffenbar: true, kommentar: null },
        }),
      ]),
    )
    const eintrag = screen.getByTestId('kettenglied-HERKUNFT')
    expect(eintrag).toHaveTextContent('Herkunft')
    expect(eintrag).toHaveTextContent('Von:claude-opus-5-5 (Modell)')
    expect(eintrag).toHaveTextContent('Ergebnis:Plan geschrieben')
    expect(eintrag).toHaveTextContent('2026')
    expect(eintrag).not.toHaveTextContent('nicht erfasst')
  })

  it('nennt fehlende Angaben eines erfassten Glieds „nicht erfasst“ und zeigt die übrigen', () => {
    offen(kette([glied({ art: 'CODE_REVIEW', ergebnis: 'ohne Befund', codeReview: { funde: { kritisch: 0, blocker: 0, wichtig: 1, hinweis: 2 } } })]))
    const eintrag = screen.getByTestId('kettenglied-CODE_REVIEW')
    expect(eintrag).toHaveTextContent('Von:nicht erfasst')
    expect(eintrag).toHaveTextContent('Zeitpunkt:nicht erfasst')
    expect(eintrag).toHaveTextContent('Quelle:nicht erfasst')
    expect(eintrag).toHaveTextContent('Ergebnis:ohne Befund')
    expect(eintrag).toHaveTextContent('Funde:0 kritisch, 0 Blocker, 1 wichtig, 2 Hinweise')
  })

  it('zeigt ein fehlendes Glied als „nicht erfasst“ und ein nicht vorgesehenes als „nicht vorgesehen“', () => {
    offen(
      kette([
        glied({ art: 'IDEE', zustand: 'NICHT_ERFASST' }),
        glied({ art: 'ISSUE_REVIEW', zustand: 'NICHT_VORGESEHEN' }),
      ]),
    )
    const idee = screen.getByTestId('kettenglied-IDEE')
    expect(idee).toHaveTextContent('Idee')
    expect(idee).toHaveTextContent('nicht erfasst')
    expect(within(idee).queryByText(/Zeitpunkt/)).not.toBeInTheDocument()
    const review = screen.getByTestId('kettenglied-ISSUE_REVIEW')
    expect(review).toHaveTextContent('nicht vorgesehen')
    expect(review).not.toHaveTextContent('nicht erfasst')
  })

  it('kennzeichnet ein von Hand geändertes Glied und eines aus einem anderen Projekt', () => {
    offen(
      kette([
        glied({ art: 'UMSETZUNG', zustand: 'NICHT_ERFASST', hinweise: ['VON_HAND_GEAENDERT'], zeitpunkt: '2026-11-10T10:00:00Z' }),
        glied({ art: 'IDEE', zustand: 'NICHT_ERFASST', hinweise: ['ANDERES_PROJEKT'] }),
      ]),
    )
    const umsetzung = screen.getByTestId('kettenglied-UMSETZUNG')
    expect(umsetzung).toHaveTextContent('von Hand geändert')
    expect(umsetzung).toHaveTextContent('Ergebnis:nicht erfasst')
    expect(screen.getByTestId('kettenglied-IDEE')).toHaveTextContent('anderes Projekt')
  })

  it('zeigt ein noch fehlendes Ergebnis des Build-Diensts als „ausstehend“ mit Weg und Prüflauf', () => {
    offen(kette([uebertragung(null)]))
    const eintrag = screen.getByTestId('kettenglied-UEBERTRAGUNG')
    expect(eintrag).toHaveTextContent('Build-Dienst:ausstehend')
    expect(eintrag).toHaveTextContent('Weg:Build-Dienst')
    expect(eintrag).toHaveTextContent('Prüflauf:gruen')
    expect(eintrag).toHaveTextContent('Ausgang:übertragen')
    expect(eintrag).toHaveTextContent('Commit:abc1234')
  })

  it('trennt Freigabe und Ausführung, wenn sie verschieden sind', () => {
    offen(
      kette([
        glied({
          art: 'FREIGABE',
          freigegebenVon: MANFRED,
          ausgefuehrtVon: RUNNER,
          freigabe: { fall: 'KENNZEICHEN', zugNachReady: '2026-11-10T11:00:00Z' },
        }),
      ]),
    )
    const eintrag = screen.getByTestId('kettenglied-FREIGABE')
    expect(eintrag).toHaveTextContent('Freigegeben von:Manfred (Mensch)')
    expect(eintrag).toHaveTextContent('Ausgeführt von:runner-token (Werkzeug, Modell claude-opus-5-5)')
    expect(eintrag).toHaveTextContent('Fall:Ziel oder Durchziehen-Kennzeichen am Fachplan oder Plan')
  })

  it('nennt den Akteur einmal, wenn Freigabe und Ausführung dieselbe Person sind', () => {
    offen(kette([glied({ art: 'VEROEFFENTLICHUNG', freigegebenVon: MANFRED, ausgefuehrtVon: { ...MANFRED } })]))
    const eintrag = screen.getByTestId('kettenglied-VEROEFFENTLICHUNG')
    expect(eintrag).toHaveTextContent('Freigegeben und ausgeführt von:Manfred (Mensch)')
    expect(eintrag).not.toHaveTextContent('Ausgeführt von:')
  })

  it('nennt eine fehlende Seite einer Freigabe „nicht erfasst“', () => {
    offen(kette([glied({ art: 'UEBERTRAGUNG', ausgefuehrtVon: RUNNER })]))
    const eintrag = screen.getByTestId('kettenglied-UEBERTRAGUNG')
    expect(eintrag).toHaveTextContent('Freigegeben von:nicht erfasst')
    expect(eintrag).toHaveTextContent('Ausgeführt von:runner-token')
  })

  it('öffnet eine sichtbare Karte über den Verweis', () => {
    const onOpenCard = vi.fn()
    offen(kette([glied({ quelle: { karte: 81, nummer: 12, titel: 'Der Plan', oeffenbar: true, kommentar: null } })]), onOpenCard)
    fireEvent.click(screen.getByRole('button', { name: '#12 Der Plan' }))
    expect(onOpenCard).toHaveBeenCalledWith(12)
  })

  it('nennt eine archivierte Karte mit Nummer und Titel, ohne sie zu öffnen', () => {
    const onOpenCard = vi.fn()
    offen(kette([glied({ quelle: { karte: 5, nummer: 5, titel: 'Alte Karte', oeffenbar: false, kommentar: null } })]), onOpenCard)
    const eintrag = screen.getByTestId('kettenglied-HERKUNFT')
    expect(eintrag).toHaveTextContent('Quelle:#5 Alte Karte')
    expect(within(eintrag).queryByRole('button')).not.toBeInTheDocument()
    expect(within(eintrag).queryByRole('link')).not.toBeInTheDocument()
  })

  it('benennt einen Kommentar mit Karte und Zeitpunkt', () => {
    offen(
      kette([
        glied({
          art: 'PRUEFRUNDE',
          zeitpunkt: '2026-11-10T10:00:00Z',
          quelle: { karte: 81, nummer: 12, titel: 'Der Plan', oeffenbar: false, kommentar: 900 },
        }),
      ]),
    )
    expect(screen.getByTestId('kettenglied-PRUEFRUNDE')).toHaveTextContent(/Quelle:Kommentar an #12 Der Plan vom .*2026/)
  })

  it('macht nur einen https-Wert aus „Quelle:“ zum Link', () => {
    offen(kette([uebertragung('https://ci.example.org/runs/7')]))
    const link = screen.getByRole('link', { name: 'https://ci.example.org/runs/7' })
    expect(link).toHaveAttribute('href', 'https://ci.example.org/runs/7')
    expect(link).toHaveAttribute('rel', expect.stringContaining('noopener'))
  })

  it.each(['javascript:alert(1)', 'http://ci.example.org/runs/7', 'kein Link'])('lässt %s als Text stehen', (wert) => {
    offen(kette([uebertragung(wert)]))
    const eintrag = screen.getByTestId('kettenglied-UEBERTRAGUNG')
    expect(eintrag).toHaveTextContent(wert)
    expect(within(eintrag).queryByRole('link')).not.toBeInTheDocument()
  })

  it('zeigt eine Prüfrunde mit Prüfern und ihrer Einarbeitung', () => {
    offen(
      kette([
        glied({
          art: 'PRUEFRUNDE',
          pruefung: {
            art: 'PLAN_REVIEW',
            runde: 2,
            pruefer: [{ nummer: 1, rolle: 'Architektur', modell: 'gpt-astra', funde: { kritisch: 1, blocker: 0, wichtig: 0, hinweis: 0 } }],
            funde: null,
            einarbeitung: glied({ art: 'EINARBEITUNG', zustand: 'NICHT_ERFASST' }),
          },
        }),
      ]),
    )
    const eintrag = screen.getByTestId('kettenglied-PRUEFRUNDE')
    expect(eintrag).toHaveTextContent('Runde:2')
    expect(eintrag).toHaveTextContent('Prüfer 1 (Architektur, gpt-astra): 1 kritisch, 0 Blocker, 0 wichtig, 0 Hinweise')
    expect(eintrag).toHaveTextContent('Funde:nicht erfasst')
    expect(within(eintrag).getByTestId('kettenglied-EINARBEITUNG')).toHaveTextContent('nicht erfasst')
  })

  it('zeigt Umsetzung und Veröffentlichung mit ihren Angaben, den Ausgang wörtlich', () => {
    offen(
      kette([
        glied({
          art: 'UMSETZUNG',
          umsetzung: { ausgang: 'ZURUECKGEZOGEN', beendetAm: null, commit: null, prueflauf: { wert: 'rot', zeitpunkt: '2026-11-10T09:00:00Z' } },
        }),
        glied({
          art: 'VEROEFFENTLICHUNG',
          veroeffentlichung: {
            versuch: null,
            version: '1.4.0',
            commit: 'def5678',
            basis: null,
            ausgang: 'Pull Request angelegt',
            quelle: null,
            freigegebenVon: ANGABE_LEER,
            ausgefuehrtVon: ANGABE_LEER,
          },
        }),
      ]),
    )
    const umsetzung = screen.getByTestId('kettenglied-UMSETZUNG')
    expect(umsetzung).toHaveTextContent('Ausgang:zurückgezogen')
    expect(umsetzung).toHaveTextContent('Commit:nicht erfasst')
    expect(umsetzung).toHaveTextContent(/Prüflauf:rot, .*2026/)
    const veroeffentlichung = screen.getByTestId('kettenglied-VEROEFFENTLICHUNG')
    expect(veroeffentlichung).toHaveTextContent('Version:1.4.0')
    expect(veroeffentlichung).toHaveTextContent('Ausgang:Pull Request angelegt')
  })

  it('zeigt Nachfahren mit Spalte und zuletzt erreichtem Glied', () => {
    const onOpenCard = vi.fn()
    offen(
      {
        sichtbar: true,
        art: 'PLAN',
        abschnitte: [{ art: 'PLAN', glieder: [glied({})] }],
        nachfahren: [
          {
            zustand: 'ERFASST',
            verweis: { karte: 90, nummer: 20, titel: 'Paket A', oeffenbar: true, kommentar: null },
            spalte: 'In review',
            spaltenSchluessel: 'in_review',
            zuletztErreicht: 'UMSETZUNG',
            hinweise: [],
          },
          {
            zustand: 'ERFASST',
            verweis: { karte: 91, nummer: 21, titel: 'Paket B', oeffenbar: true, kommentar: null },
            spalte: null,
            spaltenSchluessel: null,
            zuletztErreicht: null,
            hinweise: ['VON_HAND_GEAENDERT'],
          },
        ],
      },
      onOpenCard,
    )
    expect(screen.getByText('Plan')).toBeInTheDocument()
    const nachfahren = screen.getAllByTestId('kettennachfahre')
    expect(nachfahren[0]).toHaveTextContent('Spalte:In review')
    expect(nachfahren[0]).toHaveTextContent('Zuletzt erreicht:Umsetzung')
    expect(nachfahren[1]).toHaveTextContent('Spalte:nicht erfasst')
    expect(nachfahren[1]).toHaveTextContent('Zuletzt erreicht:nicht erfasst')
    expect(nachfahren[1]).toHaveTextContent('von Hand geändert')
    fireEvent.click(within(nachfahren[0]).getByRole('button', { name: '#20 Paket A' }))
    expect(onOpenCard).toHaveBeenCalledWith(20)
  })

  it('zeigt einen nicht erfassten Nachfahren', () => {
    offen({
      sichtbar: true,
      art: 'FACHPLAN',
      abschnitte: [],
      nachfahren: [{ zustand: 'NICHT_ERFASST', verweis: KEINER, spalte: null, spaltenSchluessel: null, zuletztErreicht: null, hinweise: [] }],
    })
    expect(screen.getByTestId('kettennachfahre')).toHaveTextContent('nicht erfasst')
  })

  it('nennt jede fehlende Angabe einer Übertragung und einer Veröffentlichung „nicht erfasst“', () => {
    offen(
      kette([
        glied({
          art: 'UEBERTRAGUNG',
          ausgefuehrtVon: { typ: 'MENSCH', name: null, modell: null },
          uebertragung: {
            versuch: null,
            commit: null,
            basis: null,
            weg: null,
            prueflauf: ANGABE_LEER,
            buildDienst: ANGABE_LEER,
            quelle: null,
            ausgang: null,
            freigegebenVon: ANGABE_LEER,
            ausgefuehrtVon: ANGABE_LEER,
          },
        }),
        glied({
          art: 'VEROEFFENTLICHUNG',
          veroeffentlichung: {
            versuch: null,
            version: null,
            commit: null,
            basis: null,
            ausgang: null,
            quelle: 'https://ci.example.org/release/3',
            freigegebenVon: ANGABE_LEER,
            ausgefuehrtVon: ANGABE_LEER,
          },
        }),
      ]),
    )
    const uebertragungsglied = screen.getByTestId('kettenglied-UEBERTRAGUNG')
    for (const angabe of ['Weg', 'Prüflauf', 'Build-Dienst', 'Ausgang', 'Commit']) {
      expect(uebertragungsglied).toHaveTextContent(`${angabe}:nicht erfasst`)
    }
    expect(uebertragungsglied).toHaveTextContent('Ausgeführt von:nicht erfasst (Mensch)')
    const veroeffentlichung = screen.getByTestId('kettenglied-VEROEFFENTLICHUNG')
    for (const angabe of ['Version', 'Ausgang', 'Commit']) {
      expect(veroeffentlichung).toHaveTextContent(`${angabe}:nicht erfasst`)
    }
    expect(within(veroeffentlichung).getByRole('link', { name: 'https://ci.example.org/release/3' })).toBeInTheDocument()
  })

  it('nennt einen unbekannten Weg wörtlich', () => {
    offen(kette([{ ...uebertragung(null), uebertragung: { ...uebertragung(null).uebertragung!, weg: 'GRUEN' } }]))
    expect(screen.getByTestId('kettenglied-UEBERTRAGUNG')).toHaveTextContent('Weg:GRUEN')
  })

  it('nennt Prüfer ohne Rolle und Modell mit Nummer und die Funde der Runde', () => {
    offen(
      kette([
        glied({
          art: 'PRUEFRUNDE',
          pruefung: {
            art: 'FACHPLAN_REVIEW',
            runde: 1,
            pruefer: [{ nummer: 2, rolle: null, modell: null, funde: { kritisch: 0, blocker: 0, wichtig: 0, hinweis: 0 } }],
            funde: { kritisch: 0, blocker: 1, wichtig: 0, hinweis: 0 },
            einarbeitung: glied({ art: 'EINARBEITUNG' }),
          },
        }),
      ]),
    )
    const eintrag = screen.getByTestId('kettenglied-PRUEFRUNDE')
    expect(eintrag).toHaveTextContent('Prüfer:Prüfer 2: 0 kritisch')
    expect(eintrag).toHaveTextContent('Funde:0 kritisch, 1 Blocker, 0 wichtig, 0 Hinweise')
  })

  it('nennt einen Verweis ohne Titel mit Nummer und einen Kommentar ohne Zeitpunkt „nicht erfasst“', () => {
    offen(kette([glied({ quelle: { karte: null, nummer: 7, titel: null, oeffenbar: false, kommentar: 3 } })]))
    expect(screen.getByTestId('kettenglied-HERKUNFT')).toHaveTextContent('Quelle:Kommentar an #7 vom nicht erfasst')
  })

  it('kennzeichnet einen nicht vorgesehenen Nachfahren', () => {
    offen({
      sichtbar: true,
      art: 'PLAN',
      abschnitte: [],
      nachfahren: [
        {
          zustand: 'NICHT_VORGESEHEN',
          verweis: { karte: 90, nummer: 20, titel: 'Paket A', oeffenbar: false, kommentar: null },
          spalte: null,
          spaltenSchluessel: null,
          zuletztErreicht: null,
          hinweise: [],
        },
      ],
    })
    const nachfahre = screen.getByTestId('kettennachfahre')
    expect(nachfahre).toHaveTextContent('#20 Paket A · nicht vorgesehen')
    expect(nachfahre).not.toHaveTextContent('Spalte')
  })

  it('spricht Englisch', () => {
    renderMitSprache(
      <Entscheidungskette
        kette={kette([
          glied({ art: 'IDEE', zustand: 'NICHT_ERFASST' }),
          glied({
            art: 'CODE_REVIEW',
            ausgefuehrtVon: RUNNER,
            zeitpunkt: '2026-11-10T10:00:00Z',
            quelle: { karte: 81, nummer: 12, titel: null, oeffenbar: false, kommentar: 900 },
            codeReview: { funde: { kritisch: 0, blocker: 0, wichtig: 1, hinweis: 2 } },
          }),
          glied({
            art: 'PRUEFRUNDE',
            pruefung: {
              art: 'PLAN_REVIEW',
              runde: 1,
              pruefer: [{ nummer: 1, rolle: null, modell: 'fable', funde: { kritisch: 0, blocker: 0, wichtig: 0, hinweis: 0 } }],
              funde: null,
              einarbeitung: glied({ art: 'EINARBEITUNG', zustand: 'NICHT_ERFASST' }),
            },
          }),
        ])}
      />,
      'en',
    )
    fireEvent.click(screen.getByRole('button', { name: 'Decision chain' }))
    expect(screen.getByTestId('kettenglied-IDEE')).toHaveTextContent('not recorded')
    const review = screen.getByTestId('kettenglied-CODE_REVIEW')
    expect(review).toHaveTextContent('Findings:0 critical, 0 blocker, 1 major, 2 notes')
    expect(review).toHaveTextContent('By:runner-token (Tool, model claude-opus-5-5)')
    expect(review).toHaveTextContent(/Source:Comment on #12 from .*2026/)
    expect(screen.getByTestId('kettenglied-PRUEFRUNDE')).toHaveTextContent('Reviewers:Reviewer 1 (fable): 0 critical')
  })
})
