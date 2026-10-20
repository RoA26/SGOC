import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { reporteService } from '@/services/reporteService'
import { ventaService, type Cotizacion, type ProductoVenta, type VentaDetalle } from '@/services/ventaService'
import { iniciarSesionComo } from '@/test/sesion'
import PuntoVenta from './PuntoVenta'

vi.mock('@/services/ventaService', () => ({
  ventaService: { catalogo: vi.fn(), porCodigo: vi.fn(), cotizar: vi.fn(), confirmar: vi.fn(), descargarComprobante: vi.fn() },
}))
vi.mock('@/services/reporteService', () => ({ reporteService: { misVentas: vi.fn() } }))

const catalogoMock = vi.mocked(ventaService.catalogo)
const cotizarMock = vi.mocked(ventaService.cotizar)
const confirmarMock = vi.mocked(ventaService.confirmar)
const porCodigoMock = vi.mocked(ventaService.porCodigo)

const CAMISA: ProductoVenta = {
  id: 1, sku: 'CAM-01', nombre: 'Camisa', precioVenta: 10000, tratamientoIva: 'GRAVADO', tarifaIva: 19,
  precioConIva: 11900, existencias: 5, estadoStock: 'NORMAL',
}
const ARROZ: ProductoVenta = { ...CAMISA, id: 2, sku: 'ARR-01', nombre: 'Arroz', precioVenta: 2500, tratamientoIva: 'EXCLUIDO', tarifaIva: 0, precioConIva: 2500 }

/** Cotización «del backend» para el pedido recibido: precio × cantidad, IVA 19 % en la camisa. */
function cotizacionPara(lineas: { productoId: number; cantidad: number; descuento?: { valor: number } }[], extra: Partial<Cotizacion> = {}): Cotizacion {
  const filas = lineas.map((l, i) => {
    const p = l.productoId === 1 ? CAMISA : ARROZ
    const base = p.precioVenta * l.cantidad
    const impuesto = p.tratamientoIva === 'GRAVADO' ? base * 0.19 : 0
    return {
      linea: i + 1, productoId: p.id, sku: p.sku, nombre: p.nombre, cantidad: l.cantidad, precioUnitario: p.precioVenta,
      subtotal: base, descuentoLinea: 0, descuentoGeneral: 0, baseGravable: base, tratamientoIva: p.tratamientoIva,
      tarifaIva: p.tarifaIva, impuesto, total: base + impuesto, porcentajeDescuento: 0, disponibles: p.existencias,
      suficiente: l.cantidad <= p.existencias, bajoCosto: false,
    }
  })
  const base = filas.reduce((s, f) => s + f.baseGravable, 0)
  const impuestos = filas.reduce((s, f) => s + f.impuesto, 0)
  return {
    lineas: filas, subtotal: base, descuentoLineas: 0, descuentoGeneral: 0, baseGravable: base, impuestos, total: base + impuestos,
    impuestosPorTarifa: [], descuentoMaximo: 0, limiteDescuento: 10, existenciasSuficientes: filas.every((f) => f.suficiente),
    requiereAutorizacion: false, motivosAutorizacion: [], hayBajoCosto: false, ...extra,
  }
}

const VENTA: VentaDetalle = {
  id: 77, numero: 1, numeroVisible: 'V-00001', estado: 'CONFIRMADA', vendidaEn: '2026-10-20T15:00:00Z',
  vendidaPor: { id: 2, username: 'trabajador', nombre: 'Persona TRABAJADOR' }, subtotal: 10000, descuentoLineas: 0,
  descuentoGeneral: 0, baseGravable: 10000, impuestos: 1900, total: 11900, impuestosPorTarifa: [], lineas: [
    { id: 1, linea: 1, productoId: 1, sku: 'CAM-01', nombre: 'Camisa', cantidad: 1, precioUnitario: 10000, subtotal: 10000, descuentoLinea: 0,
      descuentoGeneral: 0, baseGravable: 10000, tratamientoIva: 'GRAVADO', tarifaIva: 19, impuesto: 1900, total: 11900, cantidadDevuelta: 0, pendienteDeDevolver: 1 },
  ],
  pagos: [{ medio: 'EFECTIVO', valor: 11900, recibido: 20000, cambio: 8100 }], devoluciones: [], totalDevuelto: 0,
}

function errorHttp(status: number, data: unknown) {
  return new AxiosError('error', 'ERR_BAD_REQUEST', undefined, undefined, { status, data, statusText: '', headers: {}, config: {} } as AxiosResponse)
}

function renderizar() {
  return render(
    <MemoryRouter>
      <PuntoVenta />
    </MemoryRouter>,
  )
}

describe('Punto de venta', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    iniciarSesionComo('TRABAJADOR')
    catalogoMock.mockResolvedValue({ content: [CAMISA, ARROZ], page: { size: 24, number: 0, totalElements: 2, totalPages: 1 } })
    cotizarMock.mockImplementation(async (payload) => cotizacionPara(payload.lineas))
    vi.mocked(reporteService.misVentas).mockResolvedValue({ dia: '2026-10-20', ventas: 0, unidades: 0, total: 0 })
  })

  it('agrega productos, cambia cantidades y muestra el total que calcula el backend', async () => {
    const user = userEvent.setup()
    renderizar()
    await user.click(await screen.findByRole('button', { name: 'Agregar Camisa' }))
    expect(await screen.findByTestId('total-venta')).toHaveTextContent('11.900')
    // Solo viajan producto y cantidad: nunca precios ni impuestos.
    expect(cotizarMock).toHaveBeenLastCalledWith({ lineas: [{ productoId: 1, cantidad: 1 }], descuentoGeneral: undefined, codigoAutorizacion: undefined }, expect.anything())

    await user.click(screen.getByRole('button', { name: 'Sumar una unidad' }))
    await waitFor(() => expect(screen.getByTestId('total-venta')).toHaveTextContent('23.800'))
    await user.click(screen.getByRole('button', { name: 'Agregar Arroz' }))
    await waitFor(() => expect(screen.getByTestId('total-venta')).toHaveTextContent('26.300'))
    expect(screen.getByRole('button', { name: /Cobrar/ })).toHaveTextContent('26.300')

    await user.click(screen.getByRole('button', { name: 'Quitar Arroz' }))
    await waitFor(() => expect(screen.getByTestId('total-venta')).toHaveTextContent('23.800'))
  })

  it('un descuento por encima del límite exige un código de autorización', async () => {
    cotizarMock.mockImplementation(async (payload) =>
      cotizacionPara(payload.lineas, {
        requiereAutorizacion: true,
        motivosAutorizacion: ['El descuento supera tu límite (10 %).'],
        autorizacionValida: payload.codigoAutorizacion ? payload.codigoAutorizacion === 'ABCD-2345' : undefined,
      }),
    )
    const user = userEvent.setup()
    renderizar()
    await user.click(await screen.findByRole('button', { name: 'Agregar Camisa' }))
    await user.click(await screen.findByRole('button', { name: 'Aplicar descuento' }))
    await user.type(screen.getByLabelText('Descuento de Camisa'), '15')
    expect(await screen.findByText(/supera tu límite/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Cobrar/ })).toBeDisabled()
    await waitFor(() =>
      expect(cotizarMock).toHaveBeenLastCalledWith(
        expect.objectContaining({ lineas: [{ productoId: 1, cantidad: 1, descuento: { tipo: 'PORCENTAJE', valor: 15 } }] }),
        expect.anything(),
      ),
    )

    await user.type(screen.getByLabelText('Código de autorización'), 'ABCD-2345')
    expect(await screen.findByText('Código válido para esta venta.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: /Cobrar/ })).toBeEnabled())
  })

  it('rechaza un descuento inválido antes de enviarlo', async () => {
    const user = userEvent.setup()
    renderizar()
    await user.click(await screen.findByRole('button', { name: 'Agregar Camisa' }))
    await user.click(await screen.findByRole('button', { name: 'Aplicar descuento' }))
    await user.type(screen.getByLabelText('Descuento de Camisa'), '150')
    expect(await screen.findByText(/no puede superar el 100 %/)).toBeInTheDocument()
    // El valor inválido no se envía.
    expect(cotizarMock).not.toHaveBeenCalledWith(expect.objectContaining({ lineas: [expect.objectContaining({ descuento: expect.anything() })] }), expect.anything())
  })

  it('si el servidor rechaza por existencias marca la línea y conserva el carrito', async () => {
    confirmarMock.mockRejectedValue(errorHttp(409, { detail: 'Existencias insuficientes', faltantes: [{ productoId: 1, nombre: 'Camisa', solicitadas: 1, disponibles: 0 }] }))
    const user = userEvent.setup()
    renderizar()
    await user.click(await screen.findByRole('button', { name: 'Agregar Camisa' }))
    await waitFor(() => expect(screen.getByRole('button', { name: /Cobrar/ })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: /Cobrar/ }))
    expect(await screen.findByText(/No se registró la venta/)).toBeInTheDocument()
    expect(screen.getByText(/hay 0 disponibles/)).toBeInTheDocument()
    expect(within(screen.getByRole('list', { name: 'Productos del carrito' })).getByText('Camisa')).toBeInTheDocument()
  })

  it('tras un error de red, el reintento usa la misma clave y muestra la venta confirmada', async () => {
    confirmarMock.mockRejectedValueOnce(new AxiosError('Network Error', 'ERR_NETWORK')).mockResolvedValueOnce(VENTA)
    const descargarMock = vi.mocked(ventaService.descargarComprobante).mockResolvedValue('V-00001.pdf')
    const user = userEvent.setup()
    renderizar()
    await user.click(await screen.findByRole('button', { name: 'Agregar Camisa' }))
    await user.type(screen.getByLabelText('Efectivo recibido 1'), '20000')
    expect(await screen.findByText(/Cambio: \$\s?8\.100/)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: /Cobrar/ })).toBeEnabled())
    await user.click(screen.getByRole('button', { name: /Cobrar/ }))
    expect(await screen.findByText(/no se duplicará/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Cobrar/ }))

    expect(await screen.findByRole('heading', { name: 'Venta V-00001 registrada' })).toBeInTheDocument()
    const [primera, segunda] = confirmarMock.mock.calls.map((c) => c[0])
    expect(segunda!.claveIdempotencia).toBe(primera!.claveIdempotencia)
    expect(primera!.pagos).toEqual([{ medio: 'EFECTIVO', valor: 11900, recibido: 20000 }])

    await user.click(screen.getByRole('button', { name: /Descargar comprobante/ }))
    expect(descargarMock).toHaveBeenCalledWith(77)
    expect(await screen.findByText('Comprobante V-00001.pdf descargado.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Nueva venta/ }))
    expect(await screen.findByText(/El carrito está vacío/)).toBeInTheDocument()
  })

  it('el escáner agrega por código y avisa si no existe', async () => {
    porCodigoMock.mockResolvedValueOnce(ARROZ).mockRejectedValueOnce(errorHttp(404, { detail: 'No existe' }))
    const user = userEvent.setup()
    renderizar()
    await user.type(await screen.findByLabelText(/Código de barras o SKU/), '2001000000017{Enter}')
    expect(await screen.findByLabelText('Cantidad de Arroz')).toHaveValue('1')
    await user.type(screen.getByLabelText(/Código de barras o SKU/), 'NO-EXISTE{Enter}')
    expect(await screen.findByText(/Ningún producto tiene el código «NO-EXISTE»/)).toBeInTheDocument()
  })
})
