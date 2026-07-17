import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AttachmentPreview } from './AttachmentPreview'

describe('AttachmentPreview', () => {
  it('rendert ein Bild inline', () => {
    render(
      <AttachmentPreview
        filename="bild.png"
        contentType="image/png"
        url="blob:bild"
        downloadHref="/api/attachments/1"
        onClose={vi.fn()}
      />,
    )

    const img = screen.getByAltText('bild.png')
    expect(img.tagName).toBe('IMG')
    expect(img).toHaveAttribute('src', 'blob:bild')
  })

  it('rendert Nicht-Bilder (z. B. PDF) in einem iframe', () => {
    render(
      <AttachmentPreview
        filename="doc.pdf"
        contentType="application/pdf"
        url="blob:doc"
        downloadHref="/api/attachments/2"
        onClose={vi.fn()}
      />,
    )

    expect(screen.queryByAltText('doc.pdf')).not.toBeInTheDocument()
    expect(screen.getByTitle('doc.pdf').tagName).toBe('IFRAME')
  })

  it('bietet einen Download-Link und schließt über den Button', () => {
    const onClose = vi.fn()
    render(
      <AttachmentPreview
        filename="doc.pdf"
        contentType="application/pdf"
        url="blob:doc"
        downloadHref="/api/attachments/2"
        onClose={onClose}
      />,
    )

    expect(screen.getByRole('link', { name: 'Herunterladen' })).toHaveAttribute(
      'href',
      '/api/attachments/2',
    )
    fireEvent.click(screen.getByRole('button', { name: 'Schließen' }))
    expect(onClose).toHaveBeenCalled()
  })
})
