import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { analiticaService, type AnaliticaGlobal, type FiltroGlobal } from '@/services/analiticaService'
import { DashboardGlobal } from './DashboardGlobal'

vi.mock('@/services/analiticaService', () => ({ analiticaService: { global: vi.fn(), ordenesPorEmpresa: vi.fn() } }))
const globalMock = vi.mocked(analiticaService.global)
const porEmpresaMock = vi.mocked(analiticaService.ordenesPorEmpresa)

const POR_ESTADO = { BORRADOR: 0, EMITIDA: 1, RECIBIDA_PARCIALMENTE: 0, RECIBIDA: 0, CANCELADA: 0 }
const REAL = { id: 1, nombre: 'Unisen', codigo: 'UNIS01', demo: false }
const DEMO = { id: 9, nombre: 'Mercado Verde S.A.S.', codigo: 'MVSA01', demo: true }

function respuesta(filtro: FiltroGlobal): AnaliticaGlobal {
  const alcance = filtro.alcance ?? 'REALES'
  const empresas = alcance === 'REALES' ? [REAL] : alcance === 'DEMO' ? [DEMO] : [DEMO, REAL]
  return {
    alcance,
    periodo: { desde: '2026-09-22', hasta: '2026-10-21', zonaHoraria: 'America/Bogota', granularidad: 'DIA' },
    empresas: { total: empresas.length, activas: empresas.length, inactivas: 0 },
    ordenes: { emitidasEnPeriodo: empresas.length, totalHistorico: empresas.length, porEstado: POR_ESTADO },
    topEmpresas: empresas.map((e) => ({ ...e, ordenes: 1, valorComprometido: 1000 })),
    evolucionOrdenes: [{ fecha: '2026-10-21', valor: empresas.length }],
  }
}

function renderizar() {
  return render(
    <MemoryRouter>
      <DashboardGlobal />
    </MemoryRouter>,
  )
}

describe('DashboardGlobal: empresas reales y de demostración', () => {
  beforeAll(() => {
    // jsdom no implementa ResizeObserver (los gráficos lo usan para medir su ancho).
    vi.stubGlobal('ResizeObserver', class { observe() {} unobserve() {} disconnect() {} })
  })

  beforeEach(() => {
    vi.clearAllMocks()
    globalMock.mockImplementation(async (filtro) => respuesta(filtro))
    porEmpresaMock.mockImplementation(async (params) => {
      const fila = params.alcance === 'DEMO' ? DEMO : REAL
      const content = [{ ...fila, activa: true, ordenesEnPeriodo: 1, ordenesAbiertas: 1 }]
      return { content, page: { size: 8, number: 0, totalElements: 1, totalPages: 1 } }
    })
  })

  it('por defecto solo pide y muestra las empresas reales, sin marca demo', async () => {
    renderizar()
    expect(await screen.findByText(/solo empresas reales/)).toBeInTheDocument()
    expect(globalMock).toHaveBeenCalledWith(expect.objectContaining({ alcance: 'REALES' }), expect.anything())
    await waitFor(() =>
      expect(porEmpresaMock).toHaveBeenCalledWith(expect.objectContaining({ alcance: 'REALES' }), expect.anything()),
    )
    expect(screen.getByRole('button', { name: 'Empresas reales' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.queryByText('Demo')).not.toBeInTheDocument()
  })

  it('al elegir «Solo demo» pide ese alcance y marca cada empresa demo', async () => {
    renderizar()
    await screen.findByText(/solo empresas reales/)
    await userEvent.click(screen.getByRole('button', { name: 'Solo demo' }))

    expect(await screen.findByText(/solo empresas ficticias de demostración/)).toBeInTheDocument()
    expect(globalMock).toHaveBeenLastCalledWith(expect.objectContaining({ alcance: 'DEMO' }), expect.anything())
    await waitFor(() =>
      expect(porEmpresaMock).toHaveBeenLastCalledWith(expect.objectContaining({ alcance: 'DEMO' }), expect.anything()),
    )
    const tabla = await screen.findByRole('table', { name: 'Órdenes de compra por empresa' })
    expect(await within(tabla).findByText('Demo')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Solo demo' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('con «Todas» distingue la demo de la real en el ranking', async () => {
    renderizar()
    await screen.findByText(/solo empresas reales/)
    await userEvent.click(screen.getByRole('button', { name: 'Todas' }))

    expect(await screen.findByText(/empresas reales y de demostración juntas/)).toBeInTheDocument()
    expect(screen.getAllByText('Demo').length).toBeGreaterThanOrEqual(1)
    expect(screen.getAllByText('Unisen').length).toBeGreaterThanOrEqual(1)
  })
})
