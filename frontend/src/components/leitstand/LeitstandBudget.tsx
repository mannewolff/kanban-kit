import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import type { VerbrauchBudget } from '../../api/nightRunUsage'
import { projectsApi } from '../../api/projects'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { woerterbuch, type Sprache } from '../../i18n/woerterbuch'
import { TEXT_SCHWACH } from '../../theme'
import { VorschauGesperrt } from '../VorschauGesperrt'
import { KlassenMarke } from './LeitstandBausteine'

/**
 * Die Budgetzeile im Bereich „Verbrauch" (Issue #1969, Plan #1962, Fachliche Quelle #1957 AK 1, 5,
 * 7, 9 bis 11, 13, 14): der Stand des **laufenden Monats** gegen das Monatsbudget, die Warnung bei
 * 80 % und 100 %, die Hochrechnung bis Monatsende und der Tag, an dem das Budget voraussichtlich
 * überschritten wird. Sie steht über dem Umschalter, weil sie nicht von ihm abhängt.
 *
 * **Die Zahlen rechnet der Server** — Anteil, Warnstufe, Hochrechnung und Überschreitungstag kommen
 * fertig aus `budget`; die Zeile zeigt sie nur an. Ohne gemeldete Kosten (`spentUsd` null, E2)
 * steht „keine Kosten gemeldet" ohne Anteil, Warnung und Hochrechnung.
 *
 * **Festlegen nur bei `editable`** (echte Rolle OWNER): Eingabefeld, Speichern und Entfernen. Das
 * Feld nimmt Komma und Punkt und sendet mit Punkt (E4); es prüft vorab, was der Server prüft, und
 * zeigt sonst `detail` der Problem-Antwort. Nach Speichern oder Entfernen lädt die Zeile den Stand
 * neu; jeder andere Board-Leitstand lädt ihn beim Öffnen. In der Vorschau „Ansicht als …" sind
 * beide Knöpfe über `VorschauGesperrt` gesperrt.
 */
export type BudgetSchreiben = Pick<typeof projectsApi, 'setMonthlyBudget' | 'removeMonthlyBudget'>

/** Ein Objekt für alle Aufrufe — ein neues je Rendern stieße den Ladeeffekt endlos neu an. */
const STANDARD_SCHREIBEN: BudgetSchreiben = {
  setMonthlyBudget: projectsApi.setMonthlyBudget,
  removeMonthlyBudget: projectsApi.removeMonthlyBudget,
}

type Zustand = { art: 'laden' } | { art: 'fehler' } | { art: 'da'; stand: VerbrauchBudget }

/** Ein Betrag größer 0 mit höchstens zwei Nachkommastellen, Komma oder Punkt (AK 2, E4). */
const GUELTIGER_BETRAG = /^\d+(?:[.,]\d{1,2})?$/

/** Die Eingabe mit Punkt als Dezimaltrenner, oder `null`, wenn sie kein gültiges Budget ist. */
function zumSenden(eingabe: string): string | null {
  const text = eingabe.trim()
  if (!GUELTIGER_BETRAG.test(text)) return null
  const betrag = text.replace(',', '.')
  return Number(betrag) > 0 ? betrag : null
}

/** Ein Dollarbetrag: ganze Beträge ohne, alle anderen mit zwei Nachkommastellen. */
function betrag(usd: number, sprache: Sprache): string {
  const stellen = Number.isInteger(usd) ? 0 : 2
  const zahl = formatierer(sprache).zahl(usd, { minimumFractionDigits: stellen, maximumFractionDigits: stellen })
  return woerterbuch(sprache).LEITSTAND_BUDGET.BETRAG(zahl)
}

/** Ein Kalendertag `JJJJ-MM-TT` als Tag und Monat, „25. Oktober" bzw. „October 25". */
function tagUndMonat(tag: string, sprache: Sprache): string {
  const [jahr, monat, tagImMonat] = tag.split('-').map(Number)
  return new Intl.DateTimeFormat(formatierer(sprache).gebietsschema, { day: 'numeric', month: 'long' }).format(
    new Date(jahr, monat - 1, tagImMonat),
  )
}

export function LeitstandBudget({
  projectId,
  laden,
  schreiben = STANDARD_SCHREIBEN,
}: Readonly<{
  projectId: number
  laden: (projectId: number) => Promise<VerbrauchBudget>
  schreiben?: BudgetSchreiben
}>) {
  const t = useT().LEITSTAND_BUDGET
  // Stryker disable next-line ObjectLiteral,StringLiteral: gleichwertig — der Effekt setzt vor jeder Anzeige der Zeile selbst 'laden'
  const [zustand, setZustand] = useState<Zustand>({ art: 'laden' })
  const [stand, setStand] = useState(0)
  // Stryker disable next-line ArithmeticOperator,ArrayDeclaration: gleichwertig — jeder neue Wert stößt das Laden an, und neuLaden liest keinen Wert aus dem Rendern
  const neuLaden = useCallback(() => setStand((n) => n + 1), [])

  useEffect(() => {
    let aktiv = true
    setZustand({ art: 'laden' })
    laden(projectId).then(
      (wert) => {
        if (aktiv) setZustand({ art: 'da', stand: wert })
      },
      () => {
        if (aktiv) setZustand({ art: 'fehler' })
      },
    )
    return () => {
      aktiv = false
    }
  }, [laden, projectId, stand])

  return (
    <Box data-testid="budgetzeile" role="group" aria-label={t.ARIA} sx={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      {zustand.art === 'laden' && <Typography color="text.secondary">{t.LAEDT}</Typography>}
      {zustand.art === 'fehler' && <Typography color="text.secondary">{t.FEHLER}</Typography>}
      {zustand.art === 'da' && (
        <>
          <Stand stand={zustand.stand} />
          {zustand.stand.editable && (
            <Festlegen
              projectId={projectId}
              mitBudget={zustand.stand.budgetUsd !== null}
              schreiben={schreiben}
              danach={neuLaden}
            />
          )}
        </>
      )}
    </Box>
  )
}

/** Was die Zeile über den laufenden Monat sagt — jede Aussage ein eigener Satz. */
function Stand({ stand }: Readonly<{ stand: VerbrauchBudget }>) {
  const t = useT()
  const tb = t.LEITSTAND_BUDGET
  const { sprache } = useSprache()
  const { budgetUsd, spentUsd, percent } = stand
  const budgetText = budgetUsd === null ? null : betrag(budgetUsd, sprache)

  return (
    <Box data-testid="budgetzeile-stand" sx={{ display: 'flex', alignItems: 'baseline', gap: '6px 14px', flexWrap: 'wrap', fontSize: 12.5 }}>
      {spentUsd === null ? (
        <Box component="span">{budgetText === null ? tb.OHNE_KOSTEN : tb.OHNE_KOSTEN_MIT_BUDGET(budgetText)}</Box>
      ) : (
        <>
          {budgetText !== null && percent !== null && (
            <Box component="span" sx={{ fontWeight: 600 }}>
              {tb.STAND(betrag(spentUsd, sprache), budgetText, percent)}
            </Box>
          )}
          {stand.warning === 'EIGHTY' && <KlassenMarke melder="bernst">{tb.WARNUNG_ACHTZIG}</KlassenMarke>}
          {stand.warning === 'REACHED' && <KlassenMarke melder="zinnob">{tb.WARNUNG_ERREICHT}</KlassenMarke>}
          {stand.tooEarly && <Box component="span" sx={{ color: 'text.secondary' }}>{tb.ZU_FRUEH}</Box>}
          {stand.forecastUsd !== null && (
            <Box component="span" sx={{ color: 'text.secondary' }}>
              {tb.HOCHRECHNUNG(betrag(stand.forecastUsd, sprache))}
            </Box>
          )}
          {stand.exceedDate !== null && (
            <Box component="span" sx={{ color: TEXT_SCHWACH }}>
              {tb.UEBERSCHRITTEN(tagUndMonat(stand.exceedDate, sprache))}
            </Box>
          )}
        </>
      )}
      {stand.coverage === 'PARTIAL' && (
        <Box component="span" sx={{ color: 'text.secondary' }}>
          {t.VERBRAUCH_ZEITRAUM.TEILWEISE}
        </Box>
      )}
      {stand.editable && budgetUsd === null && (
        <Box component="span" sx={{ color: 'text.secondary' }}>
          {tb.FESTLEGEN_HINWEIS}
        </Box>
      )}
    </Box>
  )
}

/** Eingabefeld mit Speichern und Entfernen — nur für die echte Rolle OWNER (AK 1). */
function Festlegen({
  projectId,
  mitBudget,
  schreiben,
  danach,
}: Readonly<{ projectId: number; mitBudget: boolean; schreiben: BudgetSchreiben; danach: () => void }>) {
  const t = useT().LEITSTAND_BUDGET
  const [eingabe, setEingabe] = useState('')
  const [meldung, setMeldung] = useState<string | null>(null)

  const ausfuehren = (aufruf: () => Promise<void>, fehlerText: string) => {
    setMeldung(null)
    aufruf().then(
      () => {
        setEingabe('')
        danach()
      },
      (fehler: unknown) => setMeldung(fehler instanceof ApiError && fehler.detail ? fehler.detail : fehlerText),
    )
  }

  const speichern = (ereignis: FormEvent) => {
    ereignis.preventDefault()
    const betragText = zumSenden(eingabe)
    if (betragText === null) {
      setMeldung(t.UNGUELTIG)
      return
    }
    ausfuehren(() => schreiben.setMonthlyBudget(projectId, betragText), t.SPEICHERN_FEHLER)
  }

  return (
    <Box component="form" noValidate onSubmit={speichern} sx={{ display: 'flex', alignItems: 'flex-start', gap: '8px', flexWrap: 'wrap' }}>
      <TextField
        size="small"
        label={t.EINGABE}
        value={eingabe}
        onChange={(ereignis) => setEingabe(ereignis.target.value)}
        error={meldung !== null}
        helperText={meldung ?? undefined}
        slotProps={{ htmlInput: { inputMode: 'decimal' } }}
      />
      <VorschauGesperrt>
        <Button type="submit" variant="outlined" size="small">
          {t.SPEICHERN}
        </Button>
      </VorschauGesperrt>
      {mitBudget && (
        <VorschauGesperrt>
          <Button
            variant="text"
            size="small"
            onClick={() => ausfuehren(() => schreiben.removeMonthlyBudget(projectId), t.ENTFERNEN_FEHLER)}
          >
            {t.ENTFERNEN}
          </Button>
        </VorschauGesperrt>
      )}
    </Box>
  )
}
