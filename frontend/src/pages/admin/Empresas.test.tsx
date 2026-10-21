import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { empresaService } from '@/features/empresa/empresaApi'
import { useEmpresaSeleccionadaStore } from '@/features/empresa/empresaSeleccionadaStore'
import type { Empresa } from '@/features/empresa/types'
import { iniciarSesionComo } from '@/test/sesion'
import Empresas from './Empresas'

vi.mock('@/features/empresa/empresaApi', () => ({ empresaService: { listar: vi.fn() } }))
const listarMock = vi.mocked(empresaService.listar)

const base = { activa: true, codigoEmpresa: 'XXXX-XXXX-XXXX-XXXX', creadoEn: '2026-10-21T10:00:00Z' }
const EMPRESAS: Empresa[] = [
  { ...base, id: 3, nombre: 'Unisen', codigo: 'UNIS01', nit: '901000000-1', demo: false },
  { ...base, id: 9, nombre: 'Mercado Verde S.A.S.', codigo: 'MVSA01', nit: '999100000001', demo: true },
]

function renderizar() {
  return render(
    <MemoryRouter initialEntries={['/admin/empresas']}>
      <Routes>
        <Route path="/admin/empresas" element={<Empresas />} />
        <Route path="/" element={<p>Panel supervisado</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Empresas (SUPER_ADMIN): empresas demo', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    iniciarSesionComo('SUPER_ADMIN')
    listarMock.mockResolvedValue({ content: EMPRESAS, page: { size: 20, number: 0, totalElements: 2, totalPages: 1 } })
  })

  it('marca como «Demo» solo las empresas de demostración', async () => {
    renderizar()
    const tabla = await screen.findByRole('table', { name: 'Empresas registradas' })
    const filas = within(tabla).getAllByRole('row').slice(1)
    expect(filas).toHaveLength(2)
    const [filaReal, filaDemo] = filas as [HTMLElement, HTMLElement]
    expect(within(filaReal).getByText('Unisen')).toBeInTheDocument()
    expect(within(filaReal).queryByText('Demo')).not.toBeInTheDocument()
    expect(within(filaDemo).getByText('Mercado Verde S.A.S.')).toBeInTheDocument()
    expect(within(filaDemo).getByText('Demo')).toBeInTheDocument()
  })

  it('al supervisar una empresa demo conserva la marca para el aviso de supervisión', async () => {
    renderizar()
    await userEvent.click(await screen.findByRole('button', { name: 'Supervisar Mercado Verde S.A.S.' }))
    expect(await screen.findByText('Panel supervisado')).toBeInTheDocument()
    expect(useEmpresaSeleccionadaStore.getState().empresa).toEqual({ id: 9, nombre: 'Mercado Verde S.A.S.', codigo: 'MVSA01', demo: true })
  })
})
