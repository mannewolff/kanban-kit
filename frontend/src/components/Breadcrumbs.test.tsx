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

  it('rendert ein Mittelsegment ohne `to` als nicht-aktuelles Nicht-Link-Segment', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs
          items={[{ label: 'A', to: '/' }, { label: 'Mitte' }, { label: 'Ende' }]}
        />
      </MemoryRouter>,
    )

    // 'Mitte' hat kein `to` und ist nicht das letzte Segment: kein Link, aber auch nicht aria-current.
    expect(screen.queryByRole('link', { name: 'Mitte' })).not.toBeInTheDocument()
    expect(screen.getByText('Mitte')).not.toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('Ende')).toHaveAttribute('aria-current', 'page')
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

  it('ist ohne weitere Angaben die Seitenüberschrift', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs items={[{ label: 'Allein' }]} />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Allein')
  })

  it('rendert mit `component` ein anderes Element ohne Überschrift-Rolle', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs items={[{ label: 'Allein' }]} component="span" variant="body2" />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
    expect(screen.getByText('Allein')).toBeInTheDocument()
  })

  it('verlinkt bei `currentPage={false}` auch das letzte Segment und zeichnet keins als aktuelle Seite aus', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs
          items={[{ label: 'IT-Bildungshaus', to: '/projects/9' }, { label: 'Ideen', to: '/projects/9/ideas' }]}
          currentPage={false}
        />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Ideen' })).toHaveAttribute('href', '/projects/9/ideas')
    expect(screen.queryByText('Ideen')).not.toHaveAttribute('aria-current', 'page')
  })

  it('lässt ein letztes Segment ohne `to` auch bei `currentPage={false}` als schlichten Text stehen', () => {
    render(
      <MemoryRouter>
        <Breadcrumbs
          items={[{ label: 'Entwicklung', to: '/boards/2' }, { label: 'In Progress' }]}
          currentPage={false}
        />
      </MemoryRouter>,
    )

    expect(screen.queryByRole('link', { name: 'In Progress' })).not.toBeInTheDocument()
    expect(screen.getByText('In Progress')).not.toHaveAttribute('aria-current', 'page')
  })
})
