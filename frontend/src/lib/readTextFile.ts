/**
 * Liest eine im Browser gewählte Datei als Text ein — ohne sie hochzuladen (Issue #493).
 *
 * Bewusst über `FileReader` statt über das kürzere `Blob.text()`: Letzteres ist in der
 * Testumgebung (jsdom) nicht implementiert, der Lesepfad wäre dort also nur mit einem Polyfill
 * prüfbar — und ein Polyfill im Test-Setup würde genau die Stelle verdecken, die getestet werden
 * soll. `FileReader` ist in jsdom vorhanden und in allen Zielbrowsern seit jeher verfügbar.
 *
 * Das Promise bricht mit einem Fehler ab, wenn die Datei nicht lesbar ist (z. B. Verzeichnis,
 * entzogene Berechtigung); der Aufrufer meldet das dem Nutzer.
 */
export function readTextFile(file: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('Datei konnte nicht gelesen werden.'))
    reader.readAsText(file)
  })
}
