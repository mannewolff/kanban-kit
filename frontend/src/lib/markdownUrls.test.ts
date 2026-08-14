import { describe, expect, it } from 'vitest'
import { safeImageSrc, safeLinkHref } from './markdownUrls'

describe('safeLinkHref', () => {
  it('laesst https und mailto durch', () => {
    expect(safeLinkHref('https://example.org/a?b=1')).toBe('https://example.org/a?b=1')
    expect(safeLinkHref('mailto:a@b.c')).toBe('mailto:a@b.c')
  })

  it('blockiert javascript, data und http', () => {
    expect(safeLinkHref('javascript:alert(1)')).toBeUndefined()
    expect(safeLinkHref('data:text/html,<script>alert(1)</script>')).toBeUndefined()
    expect(safeLinkHref('http://example.org')).toBeUndefined()
  })

  it('blockiert relative, leere und nicht parsebare URLs', () => {
    expect(safeLinkHref('/admin')).toBeUndefined()
    expect(safeLinkHref('')).toBeUndefined()
    expect(safeLinkHref(undefined)).toBeUndefined()
    expect(safeLinkHref('https://')).toBeUndefined()
  })
})

describe('safeImageSrc', () => {
  it('laesst nur https durch', () => {
    expect(safeImageSrc('https://example.org/b.png')).toBe('https://example.org/b.png')
    expect(safeImageSrc('mailto:a@b.c')).toBeUndefined()
    expect(safeImageSrc('javascript:alert(1)')).toBeUndefined()
    expect(safeImageSrc('data:image/svg+xml,<svg onload="alert(1)"/>')).toBeUndefined()
    expect(safeImageSrc('/logo.png')).toBeUndefined()
    expect(safeImageSrc(undefined)).toBeUndefined()
  })
})
