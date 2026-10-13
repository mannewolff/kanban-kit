import TextField from '@mui/material/TextField'
import { useState } from 'react'
import { useSprache, useT } from '../i18n/SpracheContext'
import type { Sprache } from '../i18n/woerterbuch'

/**
 * Die Eingabe-Schreibweise je Sprache (Kriterium 9 der Quelle #1637): Deutsch `TT.MM.JJJJ`,
 * Englisch `MM/DD/YYYY`. Tag und Monat dürfen einstellig getippt werden.
 */
const SCHREIBWEISE: Readonly<Record<Sprache, { muster: RegExp; teile: (a: string, b: string) => [string, string] }>> = {
  de: { muster: /^(\d{1,2})\.(\d{1,2})\.(\d{4})$/, teile: (a, b) => [a, b] },
  en: { muster: /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/, teile: (a, b) => [b, a] },
}

const zweistellig = (wert: number) => String(wert).padStart(2, '0')

/**
 * Liest eine Eingabe in der Schreibweise der Sprache: `YYYY-MM-DD`, leer für eine leere Eingabe,
 * `null` für alles, was kein Kalenderdatum dieser Schreibweise ist (etwa der 31.02.).
 */
export function isoAusEingabe(eingabe: string, sprache: Sprache): string | null {
  const text = eingabe.trim()
  if (text === '') {
    return ''
  }
  const { muster, teile } = SCHREIBWEISE[sprache]
  const treffer = muster.exec(text)
  if (treffer === null) {
    return null
  }
  const [tagText, monatText] = teile(treffer[1], treffer[2])
  const tag = Number(tagText)
  const monat = Number(monatText)
  const jahr = Number(treffer[3])
  // Über UTC zurückgerechnet: Ein Überlauf (31.02. → 03.03.) verrät den Tag, den es nicht gibt.
  const datum = new Date(Date.UTC(jahr, monat - 1, tag))
  if (datum.getUTCFullYear() !== jahr || datum.getUTCMonth() !== monat - 1 || datum.getUTCDate() !== tag) {
    return null
  }
  return `${treffer[3]}-${zweistellig(monat)}-${zweistellig(tag)}`
}

/** Schreibt einen `YYYY-MM-DD`-Wert in der Schreibweise der Sprache; leer bei leerem oder fremdem Wert. */
export function eingabeAusIso(iso: string, sprache: Sprache): string {
  const treffer = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso)
  if (treffer === null) {
    return ''
  }
  const [, jahr, monat, tag] = treffer
  return sprache === 'de' ? `${tag}.${monat}.${jahr}` : `${monat}/${tag}/${jahr}`
}

/**
 * Datumsfeld in der Schreibweise der eingestellten Sprache statt des nativen `type="date"`, das
 * das Format des Browsers zeigt (Plan #1638, A8). Nach außen gilt weiter `YYYY-MM-DD` oder leer:
 * Eine gültige Eingabe geht sofort hinaus, eine leere als leer. Eine ungültige geht nicht hinaus —
 * der zuletzt gültige Wert bleibt, bis die Eingabe stimmt —, und nach dem Verlassen des Felds
 * steht die Meldung daran; während des Tippens bleibt sie aus, weil jede halbe Eingabe ungültig ist.
 */
export function DatumFeld({
  label,
  value,
  onChange,
}: Readonly<{
  label: string
  /** `YYYY-MM-DD` oder leer. */
  value: string
  onChange: (value: string) => void
}>) {
  const { sprache } = useSprache()
  const t = useT()
  const [text, setText] = useState(() => eingabeAusIso(value, sprache))
  const [bezug, setBezug] = useState({ value, sprache })
  const [verlassen, setVerlassen] = useState(false)

  // Ein von außen gesetzter Wert (Öffnen, Zurücksetzen) ersetzt die Eingabe — nicht aber der eigene,
  // gerade hinausgegebene: Sonst würde `1.8.2026` beim Tippen zu `01.08.2026` umgeschrieben.
  if (bezug.value !== value || bezug.sprache !== sprache) {
    setBezug({ value, sprache })
    if (isoAusEingabe(text, sprache) !== value) {
      setText(eingabeAusIso(value, sprache))
      setVerlassen(false)
    }
  }

  const ungueltig = verlassen && isoAusEingabe(text, sprache) === null

  return (
    <TextField
      label={label}
      value={text}
      placeholder={t.DATUMSFELD.MUSTER}
      onChange={(e) => {
        const neu = e.target.value
        setText(neu)
        const iso = isoAusEingabe(neu, sprache)
        if (iso === null) {
          return
        }
        setVerlassen(false)
        if (iso !== value) {
          onChange(iso)
        }
      }}
      onBlur={() => setVerlassen(true)}
      error={ungueltig}
      helperText={ungueltig ? t.DATUMSFELD.UNGUELTIG : undefined}
      slotProps={{
        htmlInput: { 'aria-label': label, inputMode: 'numeric' },
        inputLabel: { shrink: true },
      }}
      sx={{ maxWidth: 200 }}
    />
  )
}
