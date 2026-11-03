import Box from '@mui/material/Box'
import type { AbschlussView, LaufendView, VergleichView, VoraussageView, VorhabenKostenView } from '../../api/vorhabenKosten'
import { useSprache } from '../../i18n/SpracheContext'
import { formatierer } from '../../i18n/format'
import { woerterbuch } from '../../i18n/woerterbuch'
import { KUPFER, TEXT_SCHWACH } from '../../theme'
import { Erklaerung } from '../Erklaerung'

type Texte = ReturnType<typeof woerterbuch>['LEITSTAND']['VORHABEN_KOSTEN']

/**
 * Die Zeile „Voraussichtliche Kosten“ eines Vorhabens (Issue #2006, Plan #1990, fachlich #1954 AK 3
 * bis 9): laufend die Voraussage mit Spanne, Herkunft und den bisherigen Kosten oder der Satz „zu
 * wenig Daten“, nach dem Abschluss die tatsächlichen Kosten neben beiden festgehaltenen Voraussagen
 * mit ihrer Abweichung. Die Voraussage trägt einen Hinweis mit ihrer Grundlage, der beim Überfahren,
 * beim Fokus oder auf dem Tablet per Antippen erscheint.
 *
 * Ohne fachliche Anforderung, Plan und Paket gibt es keine Voraussage: Die Platte zeigt dann keine
 * Zeile, das Detail mit `immer` nur die bisherigen Kosten.
 */
export function VorhabenKostenZeile({ kosten, immer = false }: Readonly<{ kosten: VorhabenKostenView; immer?: boolean }>) {
  const { sprache } = useSprache()
  const t = woerterbuch(sprache).LEITSTAND.VORHABEN_KOSTEN
  const geld = formatierer(sprache).geld
  let inhalt: { text: string; hinweis: string | null } | null = null
  if (kosten.abschluss !== null) {
    inhalt = { text: abschlussText(t, geld, kosten.abschluss), hinweis: null }
  } else if (kosten.laufend !== null && (kosten.laufend.hatDokumentOderPaket || immer)) {
    inhalt = laufendInhalt(t, geld, kosten.laufend)
  }
  if (inhalt === null) return null
  return (
    <Box data-testid={`kosten-${kosten.number}`} sx={{ pt: '4px', fontSize: 11.5, color: TEXT_SCHWACH }}>
      {inhalt.hinweis === null ? (
        inhalt.text
      ) : (
        <Erklaerung title={inhalt.hinweis} describeChild>
          <Box
            component="span"
            tabIndex={0}
            data-testid={`kosten-hinweis-${kosten.number}`}
            sx={{
              cursor: 'help',
              textDecoration: 'underline',
              textDecorationStyle: 'dotted',
              textUnderlineOffset: 3,
              '&:focus-visible': { outlineWidth: 2, outlineStyle: 'solid', outlineColor: KUPFER, outlineOffset: 2 },
            }}
          >
            {inhalt.text}
          </Box>
        </Erklaerung>
      )}
    </Box>
  )
}

type Geld = (betrag: number) => string

/** „‹Betrag› (unvollständig)“ oder der Betrag allein. */
function betragMitVermerk(t: Texte, geld: Geld, betrag: number, unvollstaendig: boolean): string {
  return unvollstaendig ? `${geld(betrag)} (${t.UNVOLLSTAENDIG})` : geld(betrag) // NOSONAR typescript:S2301 – ein Vermerk am Betrag, keine Wahl zwischen Aktionen; zwei Funktionen verlegten die Verzweigung in beide Aufrufer
}

function herkunft(t: Texte, v: VoraussageView): string {
  if (v.art === 'PAKETE') return t.AUS_PAKETEN(v.paketzahl)
  return v.art === 'ANFORDERUNG' ? t.AUS_ANFORDERUNG_GESCHAETZT : t.VOR_DEM_SCHNITT_GESCHAETZT
}

function voraussageHinweis(t: Texte, v: VoraussageView, unvollstaendig: boolean): string {
  const saetze = [v.art === 'PAKETE' ? t.HINWEIS_PAKETE : t.HINWEIS_ANFORDERUNG]
  if (v.ohneStufe > 0) saetze.push(t.HINWEIS_OHNE_STUFE(v.ohneStufe))
  if (unvollstaendig) saetze.push(t.HINWEIS_UNVOLLSTAENDIG)
  return saetze.join(' ')
}

/** Die laufende Zeile: Voraussage, „zu wenig Daten“ oder — nur im Detail — die bisherigen Kosten allein. */
function laufendInhalt(t: Texte, geld: Geld, l: LaufendView): { text: string; hinweis: string | null } {
  const bisher = t.BISHER(betragMitVermerk(t, geld, l.bisher, l.unvollstaendig))
  const v = l.voraussage
  if (v !== null) {
    const voraussage = t.VORAUSSAGE(geld(v.typisch), geld(v.unten), geld(v.oben))
    return { text: [voraussage, herkunft(t, v), bisher].join(' · '), hinweis: voraussageHinweis(t, v, l.unvollstaendig) }
  }
  if (l.zuWenigDaten !== null) {
    return { text: [t.ZU_WENIG_DATEN, bisher].join(' · '), hinweis: t.GRUND[l.zuWenigDaten] }
  }
  return { text: bisher, hinweis: null }
}

/** „aus der Anforderung 16,00 $ (+13 %)“ bzw. „aus der Anforderung keine Voraussage“. */
function vergleichText(t: Texte, geld: Geld, name: string, v: VergleichView): string {
  if (!v.beziffert || v.typisch === null) return `${name} ${t.KEINE_VORAUSSAGE}`
  const abweichung = v.abweichungProzent === null ? '' : ` (${t.ABWEICHUNG(v.abweichungProzent)})`
  return `${name} ${geld(v.typisch)}${abweichung}`
}

function abschlussText(t: Texte, geld: Geld, a: AbschlussView): string {
  return [
    t.TATSAECHLICH(betragMitVermerk(t, geld, a.tatsaechlich, a.unvollstaendig)),
    vergleichText(t, geld, t.VERGLEICH_ANFORDERUNG, a.anforderung),
    vergleichText(t, geld, t.VERGLEICH_PAKETE, a.pakete),
  ].join(' · ')
}
