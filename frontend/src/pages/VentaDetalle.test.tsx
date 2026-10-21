import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ventaService, type VentaDetalle as Venta, type VentaResumen } from '@/services/ventaService'
import { iniciarSesionComo } from '@/test/sesion'
import VentaDetalle from './VentaDetalle'
import Ventas from './Ventas'

vi.mock('@/services/ventaService', () => ({
  ventaService: { listar: vi.fn(), obtener: vi.fn(), descargarComprobante: vi.fn(), devolver: vi.fn(), anular: vi.fn() },
}))
const obtenerMock = vi.mocked(ventaService.obtener)
const devolverMock = vi.mocked(ventaService.devolver)

const persona = { id: 2, username: 'compras', nombre: 'Compras Nexora' }
const VENTA: Venta = {
  id: 5, numero: 5, numeroVisible: 'V-00005', estado: 'CONFIRMADA', vendidaEn: '2026-10-20T15:00:00Z', vendidaPor: persona,
  subtotal: 30000, descuentoLineas: 0, descuentoGeneral: 0, baseGravable: 30000, impuestos: 5700, total: 35700,
  impuestosPorTarifa: [{ tratamiento: 'GRAVADO', tarifa: 19, base: 30000, impuesto: 5700 }],
  lineas: [{
    id: 9, linea: 1, productoId: 1, sku: 'CAM-01', nombre: 'Camisa', cantidad: 3, precioUnitario: 10000, subtotal: 30000,
    descuentoLinea: 0, descuentoGeneral: 0, baseGravable: 30000, tratamientoIva: 'GRAVADO', tarifaIva: 19, impuesto: 5700,
    total: 35700, cantidadDevuelta: 0, pendienteDeDevolver: 3,
  }],
  pagos: [{ medio: 'TARJETA_DEBITO', valor: 35700, referencia: 'AUT-1' }], devoluciones: [], totalDevuelto: 0,
}
const CON_COSTOS: Venta = {
  ...VENTA,
  lineas: [{ ...VENTA.lineas[0]!, costoUnitario: 7000, metodoCosto: 'PROMEDIO_PONDERADO', utilidad: 9000, margen: 30 }],
  rentabilidad: { ingresoNeto: 30000, costo: 21000, utilidad: 9000, margen: 30, recargo: 42.86, costoCompleto: true },
}

function renderizar(url: string) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <Routes>
        <Route path="/ventas" element={<Ventas />} />
        <Route path="/ventas/:id" element={<VentaDetalle />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('Historial y detalle de ventas', () => {
  beforeEach(() => vi.clearAllMocks())

  it('el trabajador ve sus ventas y el detalle sin costos ni acciones de gestión', async () => {
    iniciarSesionComo('TRABAJADOR')
    const resumen: VentaResumen = { id: 5, numero: 5, numeroVisible: 'V-00005', estado: 'CONFIRMADA', vendidaEn: VENTA.vendidaEn, vendidaPor: persona, unidades: 3, descuentos: 0, baseGravable: 30000, impuestos: 5700, total: 35700 }
    vi.mocked(ventaService.listar).mockResolvedValue({ content: [resumen], page: { size: 20, number: 0, totalElements: 1, totalPages: 1 } })
    obtenerMock.mockResolvedValue(VENTA)
    vi.mocked(ventaService.descargarComprobante).mockResolvedValue('V-00005.pdf')
    const user = userEvent.setup()
    renderizar('/ventas')
    expect(await screen.findByRole('heading', { name: 'Mis ventas' })).toBeInTheDocument()
    await user.click(await screen.findByRole('link', { name: 'V-00005' }))
    expect(await screen.findByRole('heading', { name: /V-00005/ })).toBeInTheDocument()
    expect(screen.queryByLabelText('Rentabilidad bruta')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Registrar devolución/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Anular/ })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /Comprobante PDF/ }))
    expect(await screen.findByText('Comprobante V-00005.pdf descargado.')).toBeInTheDocument()
  })

  it('el administrador comercial ve la rentabilidad y registra una devolución parcial', async () => {
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    obtenerMock.mockResolvedValue(CON_COSTOS)
    devolverMock.mockResolvedValue({ ...CON_COSTOS, estado: 'DEVUELTA_PARCIALMENTE' })
    const user = userEvent.setup()
    renderizar('/ventas/5')
    expect(await screen.findByLabelText('Rentabilidad bruta')).toHaveTextContent(/42,86 %/)
    await user.click(screen.getByRole('button', { name: /Registrar devolución/ }))
    await user.type(screen.getByLabelText('Unidades a devolver de Camisa'), '1')
    await user.click(screen.getByRole('checkbox'))
    await user.type(screen.getByLabelText('Motivo'), 'Talla equivocada')
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Registrar devolución' }))
    await waitFor(() =>
      expect(devolverMock).toHaveBeenCalledWith(5, expect.objectContaining({
        motivo: 'Talla equivocada',
        lineas: [{ detalleVentaId: 9, cantidad: 1, reingresaInventario: false }],
      })),
    )
    expect(await screen.findByText('Devolución registrada.')).toBeInTheDocument()
  })

  it('no permite devolver más de lo pendiente', async () => {
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    obtenerMock.mockResolvedValue(CON_COSTOS)
    const user = userEvent.setup()
    renderizar('/ventas/5')
    await user.click(await screen.findByRole('button', { name: /Registrar devolución/ }))
    await user.type(screen.getByLabelText('Unidades a devolver de Camisa'), '4')
    await user.type(screen.getByLabelText('Motivo'), 'Demasiadas')
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Registrar devolución' }))
    expect(await screen.findByText('Indica al menos una cantidad válida.')).toBeInTheDocument()
    expect(devolverMock).not.toHaveBeenCalled()
  })
})
