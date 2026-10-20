import { descargarArchivo } from '@/lib/descargas'
import { api } from '@/lib/axios'
import type { TratamientoIva } from './comercialService'
import type { EstadoStock } from './inventarioService'
import type { UsuarioResumen } from './solicitudService'
import type { Page, PageParams } from './types'

export type TipoDescuento = 'PORCENTAJE' | 'VALOR'
export type MedioPago = 'EFECTIVO' | 'TARJETA_DEBITO' | 'TARJETA_CREDITO' | 'TRANSFERENCIA' | 'OTRO'
export type EstadoVenta = 'CONFIRMADA' | 'DEVUELTA_PARCIALMENTE' | 'DEVUELTA' | 'ANULADA'

/** Producto de la caja: precio vigente (sin IVA), IVA y disponibilidad. Sin costos. */
export interface ProductoVenta {
  id: number
  sku: string
  codigoBarras?: string
  nombre: string
  precioVenta: number
  tratamientoIva: TratamientoIva
  tarifaIva: number
  precioConIva: number
  existencias: number
  estadoStock: EstadoStock
}

export interface Descuento {
  tipo: TipoDescuento
  valor: number
}

/** Lo que se envía por línea: producto, cantidad y descuento pedido. Nunca precios ni impuestos. */
export interface LineaPedido {
  productoId: number
  cantidad: number
  descuento?: Descuento
}

export interface Pago {
  medio: MedioPago
  valor: number
  recibido?: number
  referencia?: string
}

export interface ImpuestoPorTarifa {
  tratamiento: TratamientoIva
  tarifa: number
  base: number
  impuesto: number
}

export interface LineaCotizada {
  linea: number
  productoId: number
  sku: string
  nombre: string
  cantidad: number
  precioUnitario: number
  subtotal: number
  descuentoLinea: number
  descuentoGeneral: number
  baseGravable: number
  tratamientoIva: TratamientoIva
  tarifaIva: number
  impuesto: number
  total: number
  porcentajeDescuento?: number
  disponibles: number
  suficiente: boolean
  bajoCosto: boolean
}

export interface Cotizacion {
  lineas: LineaCotizada[]
  subtotal: number
  descuentoLineas: number
  descuentoGeneral: number
  baseGravable: number
  impuestos: number
  total: number
  impuestosPorTarifa: ImpuestoPorTarifa[]
  descuentoMaximo: number
  limiteDescuento: number
  existenciasSuficientes: boolean
  requiereAutorizacion: boolean
  motivosAutorizacion: string[]
  autorizacionValida?: boolean
  hayBajoCosto: boolean
}

export interface LineaVenta {
  id: number
  linea: number
  productoId: number
  sku: string
  nombre: string
  cantidad: number
  precioUnitario: number
  descuentoTipo?: TipoDescuento
  descuentoValor?: number
  subtotal: number
  descuentoLinea: number
  descuentoGeneral: number
  baseGravable: number
  tratamientoIva: TratamientoIva
  tarifaIva: number
  impuesto: number
  total: number
  cantidadDevuelta: number
  pendienteDeDevolver: number
  /** Solo gestión. */
  costoUnitario?: number
  metodoCosto?: 'PROMEDIO_PONDERADO' | 'DESCONOCIDO'
  utilidad?: number
  margen?: number
  bajoCosto?: boolean
}

export interface PagoVenta {
  medio: MedioPago
  valor: number
  recibido?: number
  cambio?: number
  referencia?: string
}

export interface Devolucion {
  id: number
  tipo: 'DEVOLUCION' | 'ANULACION'
  motivo: string
  baseGravable: number
  impuestos: number
  total: number
  costoTotal?: number
  registradaPor: UsuarioResumen
  registradaEn: string
  lineas: { detalleVentaId: number; linea: number; sku: string; nombre: string; cantidad: number; reingresaInventario: boolean; total: number }[]
}

export interface VentaDetalle {
  id: number
  numero: number
  numeroVisible: string
  estado: EstadoVenta
  vendidaEn: string
  vendidaPor: UsuarioResumen
  clienteNombre?: string
  clienteDocumento?: string
  descuentoGeneralTipo?: TipoDescuento
  descuentoGeneralValor?: number
  subtotal: number
  descuentoLineas: number
  descuentoGeneral: number
  baseGravable: number
  impuestos: number
  total: number
  impuestosPorTarifa: ImpuestoPorTarifa[]
  lineas: LineaVenta[]
  pagos: PagoVenta[]
  autorizacion?: { id: number; descuentoMaximo: number; permiteBajoCosto: boolean; motivo: string; autorizadaPor: UsuarioResumen; autorizadaEn: string }
  devoluciones: Devolucion[]
  totalDevuelto: number
  anuladaPor?: UsuarioResumen
  anuladaEn?: string
  motivoAnulacion?: string
  /** Solo gestión. */
  rentabilidad?: { ingresoNeto: number; costo?: number; utilidad?: number; margen?: number; recargo?: number; costoCompleto: boolean }
}

export interface VentaResumen {
  id: number
  numero: number
  numeroVisible: string
  estado: EstadoVenta
  vendidaEn: string
  vendidaPor: UsuarioResumen
  unidades: number
  descuentos: number
  baseGravable: number
  impuestos: number
  total: number
  clienteNombre?: string
}

export interface CotizacionPayload {
  lineas: LineaPedido[]
  descuentoGeneral?: Descuento
  codigoAutorizacion?: string
}

export interface VentaPayload extends CotizacionPayload {
  claveIdempotencia: string
  pagos: Pago[]
  clienteNombre?: string
  clienteDocumento?: string
}

export interface FiltroVentas {
  desde?: string
  hasta?: string
  estado?: EstadoVenta
  vendedorId?: number
  numero?: number
}

const BASE = '/v1/ventas'

export const ventaService = {
  async catalogo(params: PageParams & { q?: string; disponibles?: boolean }, signal?: AbortSignal): Promise<Page<ProductoVenta>> {
    const { data } = await api.get<Page<ProductoVenta>>(`${BASE}/productos`, { params, signal })
    return data
  },

  async porCodigo(codigo: string): Promise<ProductoVenta> {
    const { data } = await api.get<ProductoVenta>(`${BASE}/productos/codigo`, { params: { codigo } })
    return data
  },

  async cotizar(payload: CotizacionPayload, signal?: AbortSignal): Promise<Cotizacion> {
    const { data } = await api.post<Cotizacion>(`${BASE}/cotizacion`, payload, { signal })
    return data
  },

  /** 201 nueva; 200 si la misma clave ya estaba registrada (reintento): en ambos casos, la venta. */
  async confirmar(payload: VentaPayload): Promise<VentaDetalle> {
    const { data } = await api.post<VentaDetalle>(BASE, payload, { timeout: 30_000 })
    return data
  },

  async listar(params: PageParams & FiltroVentas, signal?: AbortSignal): Promise<Page<VentaResumen>> {
    const { data } = await api.get<Page<VentaResumen>>(BASE, { params, signal })
    return data
  },

  async obtener(id: number, signal?: AbortSignal): Promise<VentaDetalle> {
    const { data } = await api.get<VentaDetalle>(`${BASE}/${id}`, { signal })
    return data
  },

  async descargarComprobante(id: number): Promise<string> {
    const { nombre } = await descargarArchivo(`${BASE}/${id}/comprobante`, `venta-${id}.pdf`)
    return nombre
  },

  async devolver(
    id: number,
    payload: { claveIdempotencia: string; motivo: string; lineas: { detalleVentaId: number; cantidad: number; reingresaInventario: boolean }[] },
  ): Promise<VentaDetalle> {
    const { data } = await api.post<VentaDetalle>(`${BASE}/${id}/devoluciones`, payload)
    return data
  },

  async anular(id: number, payload: { claveIdempotencia: string; motivo: string }): Promise<VentaDetalle> {
    const { data } = await api.post<VentaDetalle>(`${BASE}/${id}/anulacion`, payload)
    return data
  },
}
