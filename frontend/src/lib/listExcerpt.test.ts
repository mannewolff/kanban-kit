import { describe, expect, it } from 'vitest'
import { clampExcerptWidth, EXCERPT_DEFAULT_PCT, EXCERPT_MAX_PCT, EXCERPT_MIN_PCT, stripMarkdown } from './listExcerpt'

describe('clampExcerptWidth', () => {
  it('klemmt in den erlaubten Bereich', () => {
    expect(clampExcerptWidth(5)).toBe(EXCERPT_MIN_PCT)
    expect(clampExcerptWidth(90)).toBe(EXCERPT_MAX_PCT)
    expect(clampExcerptWidth(40)).toBe(40)
  })
  it('fällt bei NaN auf den Default', () => {
    expect(clampExcerptWidth(Number.NaN)).toBe(EXCERPT_DEFAULT_PCT)
  })
})

describe('stripMarkdown', () => {
  it('macht aus Markdown eine einzeilige Vorschau', () => {
    expect(stripMarkdown('# Titel\nText **fett** und `code`')).toBe('Titel Text fett und code')
  })

  it('kollabiert Whitespace und trimmt', () => {
    expect(stripMarkdown('  a\n\n  b  ')).toBe('a b')
  })
})
