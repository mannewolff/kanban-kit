import Box from '@mui/material/Box'
import Button from '@mui/material/Button'
import Link from '@mui/material/Link'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import Typography from '@mui/material/Typography'
import { useState } from 'react'
import type { AuslieferungZuletzt } from '../../api/auslieferung'
import { ApiError, apiErrorMessage } from '../../api/client'
import {
  rueckfragenApi,
  type AntwortAuftrag,
  type Rueckfrage,
  type RueckfrageEintrag,
  type RueckfragenApi,
} from '../../api/rueckfragen'
import type { Woerterbuch } from '../../i18n/de'
import { useT } from '../../i18n/SpracheContext'

type Texte = Woerterbuch['RUECKFRAGE']

/** Was an einer Frage schon entschieden ist, als Satz; `null`, solange nichts entschieden ist. */
function entschieden(t: Texte, e: RueckfrageEintrag): string | null {
  if (e.zustand !== 'BEANTWORTET') {
    return null
  }
  if (e.text !== null) {
    return t.EIGENE(e.text)
  }
  if (e.empfehlungUebernommen) {
    return t.EMPFEHLUNG_UEBERNOMMEN
  }
  const option = e.wahl === null ? undefined : e.optionen[e.wahl - 1]
  return option === undefined ? null : t.GEWAEHLT(option.text)
}

/** Ob die Möglichkeit mit dieser Nummer gewählt ist. */
function istGewaehlt(e: RueckfrageEintrag, nummer: number): boolean {
  return e.zustand === 'BEANTWORTET' && e.wahl === nummer
}

/**
 * Eine Frage oder ein Weg: Frage, Empfehlung, Begründung, Knöpfe je Möglichkeit und — solange die
 * Rückfrage offen ist — „Anders entscheiden“ und „Später“. Ein Weg kennt keine eigene Antwort: Der
 * Kunde wählt genau einen der angebotenen Wege.
 */
function Eintrag({
  eintrag,
  bedienbar,
  gesperrt,
  onAntwort,
}: Readonly<{
  eintrag: RueckfrageEintrag
  /** Ob sich die Frage beantworten lässt — nicht bei einer Freigabe oder einer geschlossenen Rückfrage. */
  bedienbar: boolean
  gesperrt: boolean
  onAntwort: (auftrag: AntwortAuftrag) => Promise<boolean>
}>) {
  const t = useT().RUECKFRAGE
  const [eigene, setEigene] = useState<string | null>(null)
  const istWeg = eintrag.art === 'WEG'
  const satz = entschieden(t, eintrag)

  const waehle = (nummer: number) =>
    void onAntwort(istWeg ? { weg: nummer } : { antworten: [{ nr: eintrag.nr, wahl: nummer }] })

  const sendeEigene = async (text: string) => {
    if (await onAntwort({ antworten: [{ nr: eintrag.nr, text }] })) {
      setEigene(null)
    }
  }

  return (
    <Box data-testid={`rueckfrage-eintrag-${eintrag.nr}`}>
      <Typography variant="body2" fontWeight={600}>
        {eintrag.frage}
      </Typography>
      <Typography variant="body2">{t.EMPFEHLUNG(eintrag.empfehlung)}</Typography>
      {eintrag.grund !== '' && (
        <Typography variant="body2" color="text.secondary">
          {t.BEGRUENDUNG(eintrag.grund)}
        </Typography>
      )}
      {istWeg && bedienbar && (
        <Typography variant="caption" color="text.secondary">
          {t.WEG_HINWEIS}
        </Typography>
      )}
      {eintrag.optionen.length > 0 && (bedienbar || satz !== null) && (
        <Stack
          role="group"
          aria-label={t.MOEGLICHKEITEN(eintrag.frage)}
          direction="row"
          spacing={1}
          useFlexGap
          flexWrap="wrap"
          sx={{ mt: 0.5 }}
        >
          {eintrag.optionen.map((option, i) => {
            const gewaehlt = istGewaehlt(eintrag, i + 1)
            return (
              <Button
                key={option.text}
                size="small"
                variant={gewaehlt ? 'contained' : 'outlined'}
                aria-pressed={gewaehlt}
                title={option.hinweis ?? undefined}
                disabled={!bedienbar || gesperrt}
                onClick={() => waehle(i + 1)}
              >
                {option.text}
              </Button>
            )
          })}
        </Stack>
      )}
      {satz !== null && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          {satz}
        </Typography>
      )}
      {eintrag.zustand === 'ZURUECKGESTELLT' && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          {t.ZURUECKGESTELLT}
        </Typography>
      )}
      {bedienbar && eigene !== null && (
        <Stack spacing={1} sx={{ mt: 1 }}>
          <TextField
            label={t.EIGENE_ANTWORT}
            size="small"
            multiline
            minRows={2}
            value={eigene}
            onChange={(ev) => setEigene(ev.target.value)}
          />
          <Stack direction="row" spacing={1}>
            <Button
              size="small"
              variant="contained"
              disabled={gesperrt || eigene.trim() === ''}
              onClick={() => void sendeEigene(eigene.trim())}
            >
              {t.ABSENDEN}
            </Button>
            <Button size="small" onClick={() => setEigene(null)}>
              {t.ABBRECHEN}
            </Button>
          </Stack>
        </Stack>
      )}
      {bedienbar && eigene === null && (
        <Stack direction="row" spacing={1} sx={{ mt: 0.5 }}>
          {!istWeg && (
            <Button size="small" disabled={gesperrt} onClick={() => setEigene('')}>
              {t.ANDERS_ENTSCHEIDEN}
            </Button>
          )}
          <Button
            size="small"
            disabled={gesperrt}
            onClick={() => void onAntwort({ antworten: [{ nr: eintrag.nr, zurueckstellen: true }] })}
          >
            {t.SPAETER}
          </Button>
        </Stack>
      )}
    </Box>
  )
}

/**
 * Der Abschluss einer Freigabe-Rückfrage (Plan #2206 E11): Link zur Vorschau und der Knopf, der genau
 * den gezeigten Stand freigibt; danach „Freigabe erteilt“, nach der Veröffentlichung dieses Stands
 * „Veröffentlicht unter …“. Eine überholte Rückfrage bietet keinen Knopf mehr.
 */
function Freigabe({
  rueckfrage,
  ueberholt,
  zuletzt,
  gesperrt,
  onFreigeben,
}: Readonly<{
  rueckfrage: Rueckfrage
  ueberholt: boolean
  zuletzt: AuslieferungZuletzt | null
  gesperrt: boolean
  onFreigeben: () => void
}>) {
  const t = useT().RUECKFRAGE
  const veroeffentlicht = zuletzt?.veroeffentlichung
  const adresse = veroeffentlicht?.revision === rueckfrage.commit ? veroeffentlicht.url : null

  let stand
  if (ueberholt) {
    stand = <Typography variant="body2">{t.NEUERE_VORSCHAU}</Typography>
  } else if (adresse !== null) {
    stand = (
      <Typography variant="body2">
        {t.VEROEFFENTLICHT_UNTER}{' '}
        <Link href={adresse} target="_blank" rel="noopener noreferrer">
          {adresse}
        </Link>
      </Typography>
    )
  } else if (rueckfrage.zustand === 'BEANTWORTET') {
    stand = <Typography variant="body2">{t.FREIGABE_ERTEILT}</Typography>
  } else {
    stand = (
      <Box>
        <Button size="small" variant="contained" disabled={gesperrt} onClick={onFreigeben}>
          {t.FREIGEBEN}
        </Button>
      </Box>
    )
  }

  return (
    <Stack spacing={1}>
      {rueckfrage.vorschau !== null && (
        <Link href={rueckfrage.vorschau} target="_blank" rel="noopener noreferrer" variant="body2">
          {t.VORSCHAU_ANSEHEN}
        </Link>
      )}
      {stand}
    </Stack>
  )
}

/**
 * Eine Rückfrage der KI im Kartendialog (Issue #2217, Plan #2206 E8, E11, E14). Den Stand liefert
 * der Server; jeder Klick schickt eine Antwort, und die Karte zeigt danach den Stand aus der Antwort
 * des Servers. „Alles übernehmen“ übernimmt alle Empfehlungen, eine Freigabe-Rückfrage hat statt der
 * Einzelwahl den Knopf „Veröffentlichen freigeben“.
 *
 * @param gesperrt alle Knöpfe gesperrt — in der „Ansicht als XY“ (Plan #1914, E15)
 * @param zuletzt jüngste Vorschau und Veröffentlichung des Projekts, für „Veröffentlicht unter …“
 */
export function RueckfrageKarte({
  cardId,
  rueckfrage,
  zuletzt = null,
  gesperrt = false,
  api = rueckfragenApi,
  onBeantwortet,
}: Readonly<{
  cardId: number
  rueckfrage: Rueckfrage
  zuletzt?: AuslieferungZuletzt | null
  gesperrt?: boolean
  api?: Pick<RueckfragenApi, 'antworten'>
  onBeantwortet?: (stand: Rueckfrage) => void
}>) {
  const alleT = useT()
  const t = alleT.RUECKFRAGE
  const [stand, setStand] = useState(rueckfrage)
  const [uebermittelt, setUebermittelt] = useState(false)
  const [abgewiesenUeberholt, setAbgewiesenUeberholt] = useState(false)
  const [fehler, setFehler] = useState<string | null>(null)
  const [sendet, setSendet] = useState(false)

  const istFreigabe = stand.commit !== null
  const ueberholt = stand.zustand === 'UEBERHOLT' || abgewiesenUeberholt
  const offen = stand.zustand === 'OFFEN' && !ueberholt
  const gesperrtJetzt = gesperrt || sendet

  const antworte = async (auftrag: AntwortAuftrag): Promise<boolean> => {
    setSendet(true)
    setFehler(null)
    let uebernommen = false
    try {
      const neu = await api.antworten(cardId, stand.kommentarId, auftrag)
      setStand(neu)
      setUebermittelt(true)
      onBeantwortet?.(neu)
      uebernommen = true
    } catch (e) {
      if (auftrag.freigeben && e instanceof ApiError && e.status === 409) {
        setAbgewiesenUeberholt(true)
      } else {
        setFehler(apiErrorMessage(e, alleT.FEHLER_UNBEKANNT))
      }
    }
    setSendet(false)
    return uebernommen
  }

  return (
    <Box data-testid="rueckfrage-karte" sx={{ border: 1, borderColor: 'divider', borderRadius: 1, p: 1.5 }}>
      <Typography variant="subtitle2" gutterBottom>
        {t.TITEL}
      </Typography>
      <Stack spacing={1.5}>
        {stand.eintraege.map((e) => (
          <Eintrag
            key={e.nr}
            eintrag={e}
            bedienbar={offen && !istFreigabe}
            gesperrt={gesperrtJetzt}
            onAntwort={antworte}
          />
        ))}
        {offen && !istFreigabe && (
          <Box>
            <Button size="small" variant="contained" disabled={gesperrtJetzt} onClick={() => void antworte({ ja: true })}>
              {t.ALLES_UEBERNEHMEN}
            </Button>
          </Box>
        )}
        {istFreigabe && (
          <Freigabe
            rueckfrage={stand}
            ueberholt={ueberholt}
            zuletzt={zuletzt}
            gesperrt={gesperrtJetzt}
            onFreigeben={() => void antworte({ ja: true, freigeben: true })}
          />
        )}
        {uebermittelt && !istFreigabe && <Typography variant="body2">{t.UEBERMITTELT}</Typography>}
        {fehler !== null && (
          <Typography role="alert" variant="body2" color="error">
            {t.FEHLER(fehler)}
          </Typography>
        )}
      </Stack>
    </Box>
  )
}
