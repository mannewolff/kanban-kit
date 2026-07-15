import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { Breadcrumbs } from './Breadcrumbs'

describe('Breadcrumbs', () => {
  it('rendert Vorsegmente als Links und das letzte Segment als aktuelle Seite', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs
          items={[
            { label: 'Projekte', to: '/' },
            { label: 'IT-Bildungshaus', to: '/projects/1' },
            { label: 'default' },
          ]}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Projekte' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'IT-Bildungshaus' })).toHaveAttribute(
      'href',
      '/projects/1',
    )
    // Das letzte Segment ist kein Link, sondern die aktuelle Seite.
    expect(screen.queryByRole('link', { name: 'default' })).not.toBeInTheDocument()
    expect(screen.getByText('default')).toHaveAttribute('aria-current', 'page')
  })

  it('rendert ein einzelnes Segment ohne Link', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs items={[{ label: 'Allein' }]} />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('link')).not.toBeInTheDocument()
    expect(screen.getByText('Allein')).toHaveAttribute('aria-current', 'page')
  })
})
