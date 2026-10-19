import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { entregaService, type Entrega, type EntregaDetalle } from '@/services/entregaService'
import { ordenCompraService, type Recepcion } from '@/services/ordenCompraService'
import { iniciarSesionComo } from '@/test/sesion'
import RecepcionMercancia from './RecepcionMercancia'
import RecepcionOrden from './RecepcionOrden'

vi.mock('@/services/entregaService', () => ({ entregaService: { listar: vi.fn(), detalle: vi.fn(), resumen: vi.fn() } }))
vi.mock('@/services/ordenCompraService', async (original) => {
  const real = await original<typeof import('@/services/ordenCompraService')>()
  return {
    ...real,
    ordenCompraService: { misRecepciones: vi.fn(), registrarRecepcion: vi.fn(), obtenerRecepcion: vi.fn() },
  }
})
const listarMock = vi.mocked(entregaService.listar)
const detalleMock = vi.mocked(entregaService.detalle)
const registrarMock = vi.mocked(ordenCompraService.registrarRecepcion)
const misMock = vi.mocked(ordenCompraService.misRecepciones)

const ENTREGA: Entrega = {
  ordenId: 7, numero: 7, estadoOrden: 'EMITIDA', situacion: 'RETRASADA', proveedor: { id: 1, razonSocial: 'Aceros Andinos' },
  programacion: {
    version: 1, fechaEstimada: '2026-10-16', confirmadaProveedor: false, registradaPor: { id: 1, username: 'admin', nombre: 'Admin' },
    registradaEn: '2026-10-10T10:00:00Z',
  },
  diasRetraso: 3, unidadesPedidas: 10, unidadesRecibidas: 0, unidadesPendientes: 10, recepciones: 0, incidencias: 0,
  alertas: ['RETRASADA'],
}

const DETALLE: EntregaDetalle = {
  entrega: ENTREGA,
  lineas: [{ id: 70, producto: { id: 1, sku: 'TOR-01', nombre: 'Tornillo', activo: true }, cantidad: 10, cantidadRecibida: 0, pendiente: 10 }],
  recepciones: [],
  programaciones: [ENTREGA.programacion!],
}

const pagina = <T,>(content: T[]) => ({ content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } })

function renderizar(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/recepcion" element={<RecepcionMercancia />} />
        <Route path="/recepcion/:ordenId" element={<RecepcionOrden />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Recibir mercancía (trabajador)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    iniciarSesionComo('TRABAJADOR')
  })

  it('lista lo pendiente con su fecha estimada y su retraso, y filtra', async () => {
    listarMock.mockResolvedValue(pagina([ENTREGA]))
    const user = userEvent.setup()
    renderizar('/recepcion')
    expect(screen.getByRole('status')).toHaveTextContent('Cargando entregas')
    const tarjeta = await screen.findByRole('link', { name: /OC-00007/ })
    expect(tarjeta).toHaveAttribute('href', '/recepcion/7')
    expect(within(tarjeta).getByText(/Retrasada · 3 días/)).toBeInTheDocument()
    expect(within(tarjeta).getByText(/Estimada, sin confirmar/)).toBeInTheDocument()
    expect(listarMock).toHaveBeenLastCalledWith(expect.objectContaining({ vista: 'PENDIENTES' }), expect.anything())

    await user.click(screen.getByRole('button', { name: 'Retrasadas' }))
    await waitFor(() => expect(listarMock).toHaveBeenLastCalledWith(expect.objectContaining({ vista: 'RETRASADAS' }), expect.anything()))
    // Las vistas de supervisión no se ofrecen al trabajador.
    expect(screen.queryByRole('button', { name: 'Completas' })).not.toBeInTheDocument()
  })

  it('muestra el estado vacío y el error con reintento', async () => {
    listarMock.mockResolvedValueOnce(pagina([]))
    const { unmount } = renderizar('/recepcion')
    expect(await screen.findByText('No hay mercancía por recibir')).toBeInTheDocument()
    unmount()

    listarMock.mockRejectedValueOnce(new Error('caída')).mockResolvedValueOnce(pagina([ENTREGA]))
    const user = userEvent.setup()
    renderizar('/recepcion')
    const alerta = await screen.findByRole('alert')
    await user.click(within(alerta).getByRole('button', { name: 'Reintentar' }))
    expect(await screen.findByRole('link', { name: /OC-00007/ })).toBeInTheDocument()
  })

  it('muestra su historial de recepciones sin costos', async () => {
    const recepcion: Recepcion = {
      id: 3, ordenId: 7, ordenNumero: 7, proveedor: 'Aceros Andinos', recibidaEn: '2026-10-19T15:00:00Z',
      recibidaPor: { id: 2, username: 'compras', nombre: 'Compras' }, detalles: [], incidencias: [{ id: 1, tipo: 'DANADO', cantidad: 2 }], unidades: 4,
    }
    misMock.mockResolvedValue(pagina([recepcion]))
    renderizar('/recepcion?pestana=mias')
    expect(await screen.findByRole('cell', { name: 'OC-00007' })).toBeInTheDocument()
    expect(screen.queryByText(/\$/)).not.toBeInTheDocument()
  })

  it('registra una entrega con daños tras revisar el resumen y muestra el resultado', async () => {
    detalleMock.mockResolvedValue(DETALLE)
    registrarMock.mockResolvedValue({
      id: 11, ordenId: 7, ordenNumero: 7, proveedor: 'Aceros Andinos', recibidaEn: '2026-10-19T15:00:00Z',
      recibidaPor: { id: 2, username: 'compras', nombre: 'Compras Unisen' },
      detalles: [{ id: 1, detalleOrdenId: 70, producto: DETALLE.lineas[0]!.producto, cantidad: 6 }],
      incidencias: [{ id: 5, tipo: 'DANADO', detalleOrdenId: 70, cantidad: 2 }],
      unidades: 6,
    })
    const user = userEvent.setup()
    renderizar('/recepcion/7')

    await user.click(await screen.findByRole('button', { name: 'Registrar lo que llegó' }))
    const revisar = screen.getByRole('button', { name: 'Revisar recepción' })
    expect(revisar).toBeDisabled()

    // Más de lo pendiente: error junto a la línea y sin poder seguir.
    await user.type(screen.getByRole('spinbutton', { name: 'Recibidas de Tornillo' }), '11')
    expect(screen.getByText(/solo quedan 10 pendientes/)).toBeInTheDocument()
    expect(revisar).toBeDisabled()

    await user.clear(screen.getByRole('spinbutton', { name: 'Recibidas de Tornillo' }))
    await user.type(screen.getByRole('spinbutton', { name: 'Recibidas de Tornillo' }), '6')
    await user.type(screen.getByRole('spinbutton', { name: 'Dañadas de Tornillo' }), '2')
    await user.click(screen.getByRole('button', { name: /Producto incorrecto/ }))
    await user.type(screen.getByPlaceholderText('Qué llegó en lugar de lo pedido'), 'Una caja de arandelas')
    await user.click(revisar)

    expect(screen.getByText('Revisa antes de confirmar')).toBeInTheDocument()
    expect(screen.getByText('6 unidades')).toBeInTheDocument()
    expect(screen.getByText(/quedarán 4 pendientes/)).toBeInTheDocument()
    expect(registrarMock).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Confirmar recepción' }))
    await waitFor(() => expect(registrarMock).toHaveBeenCalledTimes(1))
    const [ordenId, payload] = registrarMock.mock.calls[0]!
    expect(ordenId).toBe(7)
    expect(payload.detalles).toEqual([{ detalleOrdenId: 70, cantidad: 6 }])
    expect(payload.incidencias).toEqual([
      { tipo: 'DANADO', detalleOrdenId: 70, cantidad: 2 },
      { tipo: 'PRODUCTO_INCORRECTO', detalleOrdenId: undefined, cantidad: undefined, descripcion: 'Una caja de arandelas' },
    ])
    // La identidad y la fecha las pone el servidor: el cliente no las envía.
    expect(payload).not.toHaveProperty('recibidaPor')
    expect(payload).not.toHaveProperty('recibidaEn')
    expect(payload.claveIdempotencia).toMatch(/^[0-9a-f-]{36}$/)

    expect(await screen.findByRole('region', { name: 'Resultado de la recepción' })).toHaveTextContent(
      /Recepción registrada .* por Compras Unisen/,
    )
  })

  it('reintenta con la misma clave si la confirmación falla', async () => {
    detalleMock.mockResolvedValue(DETALLE)
    registrarMock.mockRejectedValueOnce(new Error('red')).mockResolvedValueOnce({
      id: 12, ordenId: 7, ordenNumero: 7, proveedor: 'Aceros Andinos', recibidaEn: '2026-10-19T15:00:00Z',
      recibidaPor: { id: 2, username: 'compras', nombre: 'Compras' }, detalles: [], incidencias: [], unidades: 10,
    })
    const user = userEvent.setup()
    renderizar('/recepcion/7')
    await user.click(await screen.findByRole('button', { name: 'Registrar lo que llegó' }))
    await user.click(screen.getByRole('button', { name: 'Llegó todo lo pendiente' }))
    await user.click(screen.getByRole('button', { name: 'Revisar recepción' }))
    await user.click(screen.getByRole('button', { name: 'Confirmar recepción' }))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reintentar' }))
    await waitFor(() => expect(registrarMock).toHaveBeenCalledTimes(2))
    expect(registrarMock.mock.calls[1]![1].claveIdempotencia).toBe(registrarMock.mock.calls[0]![1].claveIdempotencia)
  })
})
