import { ApiError, apiErrorMessage } from '../api/client'
import type { Woerterbuch } from '../i18n/de'

/**
 * Meldung zu einem gescheiterten Schritt der Code-Anmeldung (Plan #1801) — für abgetippten Code und
 * Link aus der Mail gleich, damit beide Wege dieselben Meldungen zeigen.
 */
export function codeFehlermeldung(e: unknown, t: Woerterbuch): string {
  if (e instanceof ApiError && e.status === 429) {
    // Grenze je Adresse bzw. Bremse je Herkunft: vor dem 401-Zweig, damit niemand weiterprobiert,
    // statt zu warten. Der Server nennt den Fall in `detail`.
    return apiErrorMessage(e, t.AUTH.ZU_VIELE_VERSUCHE)
  }
  if (e instanceof ApiError && e.status === 401) {
    // Falsch, abgelaufen, verbraucht, ersetzt oder unbekannte Adresse — bewusst ununterscheidbar (E6).
    return t.AUTH.CODE_UNGUELTIG
  }
  // 403 (gesperrt, wartet auf Freigabe) trägt die konkrete Meldung in `detail`. Gelesen wird
  // `detail` über apiErrorMessage, nie `message`: das fällt bei unlesbarem Body auf den Roh-Body
  // zurück — bei einem Fehler eines vorgelagerten Proxys stünde sonst dessen HTML im Formular.
  return apiErrorMessage(e, t.AUTH.ANMELDUNG_FEHLGESCHLAGEN)
}
