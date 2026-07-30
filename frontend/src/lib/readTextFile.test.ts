import { afterEach, describe, expect, it, vi } from 'vitest'
import { readTextFile } from './readTextFile'

afterEach(() => vi.unstubAllGlobals())

describe('readTextFile', () => {
  it('liefert den Textinhalt der Datei', async () => {
    const file = new File(['## Titel\nText'], 'spec.md', { type: 'text/markdown' })

    await expect(readTextFile(file)).resolves.toBe('## Titel\nText')
  })

  it('bricht mit einem Fehler ab, wenn die Datei nicht lesbar ist', async () => {
    class FailingReader {
      onload: (() => void) | null = null
      onerror: (() => void) | null = null
      result: string | null = null
      readAsText(): void {
        this.onerror?.()
      }
    }
    vi.stubGlobal('FileReader', FailingReader)

    await expect(readTextFile(new Blob(['x']))).rejects.toThrow('Datei konnte nicht gelesen werden.')
  })
})
