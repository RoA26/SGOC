import { api } from '@/lib/axios'
import type { EstadoStock } from './inventarioService'

export interface ResumenVentas {
  numeroVentas: number
  ventasAnuladas: number
  numeroDevoluciones: number
  unidadesVendidas: number
  ingresoBruto: number
  devoluciones: number
  ingresoNeto: number
  impuestosNetos: number
  totalCobradoNeto: number
  descuentos: number
  ventaPromedio?: number
  /** Ausentes si falta algún costo histórico (no disponible, no cero). */
  costo?: number
  utilidadBruta?: number
  margenBruto?: number
  recargo?: number
  costoCompleto: boolean
  ingresoSinCosto: number
  lineasBajoCosto: number
}

export interface ProductoVendido {
  productoId: number
  sku: string
  nombre: string
  unidades: number
  ingresoNeto: number
  costo?: number
  utilidad?: number
  margen?: number
}

export interface ReporteVentas {
  periodo: { desde: string; hasta: string; zonaHoraria: string }
  metodoValoracion: string
  valoracionProvisional: boolean
  resumen: ResumenVentas
  serie: { fecha: string; ventas: number; ingresoNeto: number }[]
  masVendidos: ProductoVendido[]
  mayorUtilidad: ProductoVendido[]
  porVendedor: { usuarioId: number; nombre: string; ventas: number; ingresoNeto: number; descuentos: number }[]
  ventasBajoCosto: { ventaId: number; numeroVisible: string; vendidaEn: string; sku: string; nombre: string; cantidad: number; ingreso: number; costo: number; perdida: number }[]
  bajoInventario: { productoId: number; sku: string; nombre: string; existencias: number; stockMinimo: number; estado: EstadoStock }[]
}

export interface MisVentas {
  dia: string
  ventas: number
  unidades: number
  total: number
}

export const reporteService = {
  async ventas(params: { desde?: string; hasta?: string }, signal?: AbortSignal): Promise<ReporteVentas> {
    const { data } = await api.get<ReporteVentas>('/v1/reportes/ventas', { params, signal })
    return data
  },

  async misVentas(signal?: AbortSignal): Promise<MisVentas> {
    const { data } = await api.get<MisVentas>('/v1/reportes/ventas/mias', { signal })
    return data
  },
}
