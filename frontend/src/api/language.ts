import { istSprache, type Sprache } from '../i18n/woerterbuch'
import { apiFetch } from './client'

/** Liest den Sprachcode aus der Antwort `{ language }`; alles andere ist ein Fehler. */
function parseSprache(data: unknown): Sprache {
  const language = typeof data === 'object' && data !== null ? (data as { language?: unknown }).language : undefined
  if (!istSprache(language)) {
    throw new Error(`Unbekannte Sprache: ${JSON.stringify(language)}`)
  }
  return language
}

/** Die Sprache der Installation (Plan #1638, A2): lesen ohne Anmeldung, setzen als Plattform-Admin. */
export const languageApi = {
  lesen: () => apiFetch('/api/language', {}, parseSprache),
  setzen: (sprache: Sprache) =>
    apiFetch('/api/admin/language', { method: 'PUT', body: JSON.stringify({ language: sprache }) }, parseSprache),
}
