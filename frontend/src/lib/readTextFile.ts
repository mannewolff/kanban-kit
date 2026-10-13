import { woerterbuch, type Sprache } from '../i18n/woerterbuch'

/**
 * Liest eine im Browser gewählte Datei als Text ein — ohne sie hochzuladen (Issue #493).
 *
 * Bewusst über `FileReader` statt über das kürzere `Blob.text()`: Letzteres ist in der
 * Testumgebung (jsdom) nicht implementiert, der Lesepfad wäre dort also nur mit einem Polyfill
 * prüfbar — und ein Polyfill im Test-Setup würde genau die Stelle verdecken, die getestet werden
 * soll. `FileReader` ist in jsdom vorhanden und in allen Zielbrowsern seit jeher verfügbar.
 *
 * Das Promise bricht mit einem Fehler ab, wenn die Datei nicht lesbar ist (z. B. Verzeichnis,
 * entzogene Berechtigung); der Aufrufer meldet das dem Nutzer. Der Fehlertext folgt `sprache`.
 */
export function readTextFile(file: Blob, sprache: Sprache = 'de'): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error(woerterbuch(sprache).DATEI_NICHT_LESBAR))
    reader.readAsText(file)
  })
}
