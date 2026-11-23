import Box from '@mui/material/Box'
import { useEffect, useState } from 'react'
import type { Card } from '../../api/cards'
import { commentsApi, type Comment } from '../../api/comments'
import { sacheVon, useKitNamen } from '../../api/kitNamen'
import type { Label } from '../../api/labels'
import { formatierer } from '../../i18n/format'
import { useSprache, useT } from '../../i18n/SpracheContext'
import { KOEPFE, ZEILEN, traegtKopf, zeilenRest } from '../../lib/kitWortformen'
import { TEXT_SCHWACH } from '../../theme'

/**
 * Seit wann die Karte beim Anbieter steht: der Zeitpunkt der Zeile „Seit:“ im jüngsten Kommentar mit
 * dem Kopf „Beim Anbieter“, gleich in welcher Sprache (Issue #2218, Fachplan #2202 Kriterium 5);
 * `null` ohne einen solchen Kommentar oder mit einem unlesbaren Zeitpunkt.
 */
export function beimAnbieterSeit(kommentare: readonly Comment[]): string | null {
  const juengster = kommentare
    .filter((k) => traegtKopf(KOEPFE.beimAnbieter, k.body))
    .reduce<Comment | null>((bisher, k) => (bisher === null || k.createdAt > bisher.createdAt ? k : bisher), null)
  const seit = juengster === null ? undefined : zeilenRest(ZEILEN.seit, juengster.body)
  return seit === undefined || Number.isNaN(Date.parse(seit)) ? null : seit
}

/**
 * „beim Anbieter in Arbeit seit <Zeit>“ an einer Karte mit Label der Sache `ANBIETER` (Issue #2218,
 * Plan #2206 E14). Was beim Anbieter schiefgeht, liefert der Server dem Kunden nicht (#2211, #2214);
 * die Karte zeigt nur diesen Hinweis. Ohne gelesenen Zeitpunkt steht der Hinweis ohne Zeit.
 */
export function AnbieterHinweis({ card, boardLabels }: Readonly<{ card: Card; boardLabels: readonly Label[] }>) {
  const t = useT()
  const { sprache } = useSprache()
  const kitNamen = useKitNamen()
  const beimAnbieter =
    kitNamen.status === 'fertig' &&
    card.labels.some((id) => {
      const label = boardLabels.find((l) => l.id === id)
      return label !== undefined && sacheVon(kitNamen.daten, label.name) === 'ANBIETER'
    })
  const [seit, setSeit] = useState<string | null>(null)

  useEffect(() => {
    if (!beimAnbieter) return
    let aktiv = true
    commentsApi.list(card.id).then(
      (kommentare) => {
        if (aktiv) setSeit(beimAnbieterSeit(kommentare))
      },
      () => {},
    )
    return () => {
      aktiv = false
    }
  }, [beimAnbieter, card.id])

  if (!beimAnbieter) return null
  return (
    <Box component="span" sx={{ display: 'block', fontSize: 10.5, color: TEXT_SCHWACH }}>
      {seit === null ? t.ANBIETER.IN_ARBEIT : t.ANBIETER.IN_ARBEIT_SEIT(formatierer(sprache).datumUhrzeit(seit))}
    </Box>
  )
}
