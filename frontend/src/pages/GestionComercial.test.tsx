import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { comercialService, type ProductoComercial } from '@/services/comercialService'
import { iniciarSesionComo } from '@/test/sesion'
import GestionComercial from './GestionComercial'

vi.mock('@/services/comercialService', () => ({
  comercialService: {
    productos: vi.fn(), cambiarCondiciones: vi.fn(), historial: vi.fn(), politica: vi.fn(), guardarPolitica: vi.fn(),
    crearAutorizacion: vi.fn(), autorizaciones: vi.fn(),
  },
}))
const productosMock = vi.mocked(comercialService.productos)
const cambiarMock = vi.mocked(comercialService.cambiarCondiciones)

const TORNILLO: ProductoComercial = {
  id: 1, sku: 'TOR-01', nombre: 'Tornillo', activo: true, precioCompraReferencia: 900, precioVenta: 1000,
  tratamientoIva: 'GRAVADO', tarifaIva: 19, precioConIva: 1190, costoPromedio: 750, utilidadUnitaria: 250, margen: 25,
  recargo: 33.33, bajoCosto: false, existencias: 10, estadoStock: 'NORMAL',
}
const SIN_COSTO: ProductoComercial = {
  id: 2, sku: 'SIN-01', nombre: 'Sin costo', activo: true, precioCompraReferencia: 500, bajoCosto: false, existencias: 0, estadoStock: 'AGOTADO',
}
const pagina = (content: ProductoComercial[]) => ({ content, page: { size: 20, number: 0, totalElements: content.length, totalPages: 1 } })

function renderizar() {
  return render(
    <MemoryRouter>
      <GestionComercial />
    </MemoryRouter>,
  )
}

describe('Gestión comercial', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    productosMock.mockResolvedValue(pagina([TORNILLO, SIN_COSTO]))
  })

  it('muestra precio, costo y rentabilidad, y lo desconocido como no disponible', async () => {
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    renderizar()
    await screen.findByText('Tornillo')
    const filas = screen.getAllByRole('row')
    expect(within(filas[1]!).getByText(/1\.000/)).toBeInTheDocument()
    expect(within(filas[1]!).getByText(/25 % · 33,33 %/)).toBeInTheDocument()
    expect(within(filas[2]!).getByText('Sin precio')).toBeInTheDocument()
    expect(within(filas[2]!).getByText('No disponible')).toBeInTheDocument()
  })

  it('el administrador cambia un precio con confirmación y advertencia bajo el costo', async () => {
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    cambiarMock.mockResolvedValue({ ...TORNILLO, precioVenta: 700, bajoCosto: true, utilidadUnitaria: -50 })
    const user = userEvent.setup()
    renderizar()
    await user.click(await screen.findByRole('button', { name: 'Cambiar precio de Tornillo' }))
    const precio = screen.getByLabelText('Precio de venta sin IVA (COP)')
    await user.clear(precio)
    await user.type(precio, '700')
    expect(screen.getByText(/por debajo del costo promedio/)).toBeInTheDocument()
    await user.type(screen.getByLabelText('Motivo (opcional)'), 'Liquidación')
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(await screen.findByText(/Pulsa «Confirmar cambio»/)).toBeInTheDocument()
    expect(cambiarMock).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Confirmar cambio' }))
    await waitFor(() =>
      expect(cambiarMock).toHaveBeenCalledWith(1, { precioVenta: 700, tratamientoIva: 'GRAVADO', tarifaIva: 19, motivo: 'Liquidación' }),
    )
    expect(await screen.findByText(/queda por debajo del costo/)).toBeInTheDocument()
  })

  it('un producto excluido se envía con tarifa 0 y los errores del servidor se muestran', async () => {
    iniciarSesionComo('ADMINISTRADOR_COMERCIAL')
    cambiarMock.mockRejectedValue(new AxiosError('x', 'ERR_BAD_REQUEST', undefined, undefined, {
      status: 400, data: { detail: 'Datos inválidos', errors: { tarifaIva: 'Tarifa no válida.' } }, statusText: '', headers: {}, config: {},
    } as AxiosResponse))
    const user = userEvent.setup()
    renderizar()
    await user.click(await screen.findByRole('button', { name: 'Cambiar precio de Tornillo' }))
    await user.selectOptions(screen.getByLabelText('Tratamiento del IVA'), 'EXCLUIDO')
    expect(screen.queryByLabelText('Tarifa del IVA (%)')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Guardar' }))
    await user.click(await screen.findByRole('button', { name: 'Confirmar cambio' }))
    await waitFor(() => expect(cambiarMock).toHaveBeenCalledWith(1, expect.objectContaining({ tratamientoIva: 'EXCLUIDO', tarifaIva: 0 })))
    expect(await screen.findByText('Tarifa no válida.', { selector: '[role="alert"]' })).toBeInTheDocument()
  })

  it('el SUPER_ADMIN supervisando consulta sin poder cambiar precios', async () => {
    iniciarSesionComo('SUPER_ADMIN', 1)
    renderizar()
    await screen.findByText('Tornillo')
    expect(screen.queryByRole('button', { name: /Cambiar precio/ })).not.toBeInTheDocument()
  })
})
