import { useState } from 'react'
import type { Attachment, AttachmentsApi } from '../api/attachments'
import { apiErrorMessage } from '../api/client'
import { useT } from '../i18n/SpracheContext'

/** Ein angekommener Anhang samt Vorschau-Adresse; `null`, wenn es keine gibt oder ihr Abruf scheiterte. */
export interface HochgeladenerAnhang {
  anhang: Attachment
  vorschauUrl: string | null
}

/**
 * Das Hochladen eines Anhangs an eine Karte, aus dem Kartendetail herausgelöst, damit der Dialog
 * „Idee einstellen“ denselben Weg nimmt (Issue #2220, Plan #2206 E13).
 *
 * `hochladen` liefert `null`, wenn der Upload scheitert, und setzt dann `fehler`. Upload und
 * Vorschau-Abruf sind zwei Aufrufe: Scheitert nur die Vorschau, ist der Anhang angekommen und kommt
 * zurück — ein erneuter Versuch wäre ein zweiter, unnötiger Anhang. Mit `vorschau: false` holt es
 * auch bei einem Bild keine.
 */
export function useAnhangHochladen(api: Pick<AttachmentsApi, 'upload' | 'fetchBlob'>) {
  const t = useT()
  const [fehler, setFehler] = useState<string | null>(null)

  const hochladen = async (
    cardId: number,
    datei: File,
    { vorschau = true }: { vorschau?: boolean } = {},
  ): Promise<HochgeladenerAnhang | null> => {
    setFehler(null)
    let anhang: Attachment
    try {
      anhang = await api.upload(cardId, datei)
    } catch (error_: unknown) {
      setFehler(apiErrorMessage(error_, t.KARTE.UPLOAD_FEHLER))
      return null
    }
    if (!vorschau || !anhang.contentType.startsWith('image/')) {
      return { anhang, vorschauUrl: null }
    }
    try {
      const blob = await api.fetchBlob(anhang.id)
      return { anhang, vorschauUrl: URL.createObjectURL(blob) }
    } catch (error_: unknown) {
      setFehler(apiErrorMessage(error_, t.KARTE.VORSCHAU_NACH_UPLOAD_FEHLER))
      return { anhang, vorschauUrl: null }
    }
  }

  return { fehler, setFehler, hochladen }
}
