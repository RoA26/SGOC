import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ROLES_GESTION } from '@/features/auth/permisos'
import { RutaConRol } from '@/features/auth/RutaConRol'
import { entregaService, type Entrega } from '@/services/entregaService'
import { ordenCompraService } from '@/services/ordenCompraService'
import { iniciarSesionComo } from '@/test/sesion'
import Entregas from './Entregas'

vi.mock('@/services/entregaService', () => ({ entregaService: { listar: vi.fn(), resumen: vi.fn(), detalle: vi.fn() } }))
vi.mock('@/services/ordenCompraService', async (original) => {
  const real = await original<typeof import('@/services/ordenCompraService')>()
  return { ...real, ordenCompraService: { programarEntrega: vi.fn() } }
})
vi.mock('@/store/proveedoresStore', () => {
  const estado = { items: [{ id: 1, nit: '900111111-1', razonSocial: 'Aceros Andinos' }], cargar: async () => {} }
  return { useProveedoresStore: (selector: (s: typeof estado) => unknown) => selector(estado) }
})
const listarMock = vi.mocked(entregaService.listar)
const resumenMock = vi.mocked(entregaService.resumen)
const programarMock = vi.mocked(ordenCompraService.programarEntrega)

const usuario = { id: 1, username: 'admin', nombre: 'Admin' }
const ENTREGAS: Entrega[] = [
  {
    ordenId: 1, numero: 1, estadoOrden: 'EMITIDA', situacion: 'SIN_PROGRAMAR', proveedor: { id: 1, razonSocial: 'Aceros Andinos' },
    diasRetraso: 0, unidadesPedidas: 5, unidadesRecibidas: 0, unidadesPendientes: 5, recepciones: 0, incidencias: 0, alertas: ['SIN_PROGRAMAR'],
  },
  {
    ordenId: 2, numero: 2, estadoOrden: 'RECIBIDA_PARCIALMENTE', situacion: 'PROGRAMADA', proveedor: { id: 1, razonSocial: 'Aceros Andinos' },
    programacion: { version: 1, fechaEstimada: '2099-01-10', horaDesde: '08:00', horaHasta: '12:00', confirmadaProveedor: true, registradaPor: usuario, registradaEn: '2026-10-10T10:00:00Z' },
    diasRetraso: 0, unidadesPedidas: 8, unidadesRecibidas: 3, unidadesPendientes: 5, recepciones: 1, incidencias: 2, alertas: ['INCIDENCIAS'],
  },
]
const pagina = (content: Entrega[]) => ({ content, page: { size: 15, number: 0, totalElements: content.length, totalPages: 1 } })

function renderizar(url = '/entregas') {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route element={<RutaConRol roles={ROLES_GESTION} />}>
          <Route path="/entregas" element={<Entregas />} />
        </Route>
        <Route path="/recepcion/:id" element={<p>Vista de recepción</p>} />
        <Route path="/" element={<p>Inicio</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Entregas (supervisión del gerente)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listarMock.mockResolvedValue(pagina(ENTREGAS))
    resumenMock.mockResolvedValue({
      pendientes: 2, paraHoy: 0, retrasadas: 0, sinProgramar: 1, parciales: 1, conIncidencias: 1, requierenIntervencion: 2,
    })
  })

  it('muestra cifras, situación, discrepancias y filtra por vista, fechas y proveedor', async () => {
    iniciarSesionComo('ADMIN_EMPRESA')
    const user = userEvent.setup()
    renderizar()
    expect(await screen.findByRole('link', { name: /Requieren intervención/ })).toHaveTextContent('2')
    const filas = await screen.findAllByRole('row')
    expect(within(filas[1]!).getByText('Sin programar')).toBeInTheDocument()
    expect(within(filas[2]!).getByText(/Con incidencias \(2\)/)).toBeInTheDocument()
    expect(within(filas[2]!).getByText(/Confirmada por el proveedor/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Retrasadas' }))
    await waitFor(() => expect(listarMock).toHaveBeenLastCalledWith(expect.objectContaining({ vista: 'RETRASADAS' }), expect.anything()))
    await user.type(screen.getByLabelText('Llegada desde'), '2026-11-01')
    await user.selectOptions(screen.getByRole('combobox', { name: 'Proveedor' }), '1')
    await waitFor(() =>
      expect(listarMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ vista: 'RETRASADAS', desde: '2026-11-01', proveedorId: 1 }),
        expect.anything(),
      ),
    )
  })

  it('programa una entrega y exige motivo al reprogramar', async () => {
    iniciarSesionComo('ADMIN_EMPRESA')
    programarMock.mockResolvedValue({ ...ENTREGAS[1]!.programacion!, version: 2 })
    const user = userEvent.setup()
    renderizar()
    await user.click(await screen.findByRole('button', { name: /Reprogramar OC-00002/ }))
    const dialogo = screen.getByRole('dialog', { name: /Reprogramar entrega · OC-00002/ })
    expect(within(dialogo).getByText(/Vigente \(versión 1\)/)).toBeInTheDocument()
    const guardar = within(dialogo).getByRole('button', { name: 'Guardar nueva fecha' })
    expect(guardar).toBeDisabled()
    await user.type(within(dialogo).getByLabelText('Motivo del cambio'), 'El transportador se retrasa')
    await user.click(within(dialogo).getByRole('radio', { name: /Todo el día/ }))
    await user.click(guardar)
    await waitFor(() => expect(programarMock).toHaveBeenCalledTimes(1))
    expect(programarMock).toHaveBeenCalledWith(2, expect.objectContaining({
      fechaEstimada: '2099-01-10', horaDesde: null, horaHasta: null, confirmadaProveedor: true, motivo: 'El transportador se retrasa',
    }))
    expect(await screen.findByText(/reprogramada \(versión 2\)/)).toBeInTheDocument()
  })

  it('el SUPER_ADMIN supervisando consulta sin programar; el trabajador no entra', async () => {
    iniciarSesionComo('SUPER_ADMIN', 1)
    const { unmount } = renderizar()
    expect(await screen.findByRole('link', { name: /Recepción de OC-00001/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Programar/ })).not.toBeInTheDocument()
    unmount()

    iniciarSesionComo('TRABAJADOR')
    renderizar()
    expect(await screen.findByText('Inicio')).toBeInTheDocument()
    expect(listarMock).toHaveBeenCalledTimes(1) // solo la consulta anterior del SUPER_ADMIN
  })
})
