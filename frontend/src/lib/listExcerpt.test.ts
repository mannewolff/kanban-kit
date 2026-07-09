import { describe, expect, it } from 'vitest'
import { stripMarkdown } from './listExcerpt'

describe('stripMarkdown', () => {
  it('macht aus Markdown eine einzeilige Vorschau', () => {
    expect(stripMarkdown('# Titel\nText **fett** und `code`')).toBe('Titel Text fett und code')
  })

  it('kollabiert Whitespace und trimmt', () => {
    expect(stripMarkdown('  a\n\n  b  ')).toBe('a b')
  })
})
