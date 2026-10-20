import { descargarArchivo } from '@/lib/descargas'
import { api } from '@/lib/axios'
import type { UsuarioResumen } from './solicitudService'
import type { Page, PageParams } from './types'

export type EstadoOrdenCompra = 'BORRADOR' | 'EMITIDA' | 'RECIBIDA_PARCIALMENTE' | 'RECIBIDA' | 'CANCELADA'

/** Producto tal como lo referencia un documento: activo = false si se dio de baja después. */
export interface ProductoLinea {
  id: number
  sku: string
  nombre: string
  codigoBarras?: string
  activo: boolean
}

export interface LineaOrden {
  id: number
  producto: ProductoLinea
  cantidad: number
  costoUnitario: number
  subtotal: number
  cantidadRecibida: number
  pendiente: number
}

/**
 * Una versión de la programación de la entrega (ProgramacionEntregaDTO). La fecha estimada es un
 * día (AAAA-MM-DD, sin hora); la franja solo existe si se acordó: sin ella, "todo el día".
 */
export interface ProgramacionEntrega {
  version: number
  fechaEstimada: string
  horaDesde?: string
  horaHasta?: string
  /** El proveedor la confirmó; si no, es una estimación interna. */
  confirmadaProveedor: boolean
  referenciaProveedor?: string
  observaciones?: string
  motivoCambio?: string
  registradaPor: UsuarioResumen
  registradaEn: string
}

/** OrdenCompraResponseDTO. Emisión, cancelación y entrega solo llegan cuando existen. */
export interface OrdenCompra {
  id: number
  numero: number
  estado: EstadoOrdenCompra
  proveedor: { id: number; nit: string; razonSocial: string; activo: boolean }
  solicitudId?: number
  observaciones?: string
  detalles: LineaOrden[]
  total: number
  unidadesPedidas: number
  unidadesRecibidas: number
  creadoPor: UsuarioResumen
  creadoEn: string
  actualizadoEn: string
  emitidaPor?: UsuarioResumen
  emitidaEn?: string
  canceladaPor?: UsuarioResumen
  canceladaEn?: string
  motivoCancelacion?: string
  entrega?: ProgramacionEntrega
}

export interface LineaOrdenPayload {
  productoId: number
  cantidad: number
  /** Sin valor, el backend usa el precio actual del producto. */
  costoUnitario?: number
}

export interface OrdenCompraPayload {
  proveedorId: number
  observaciones: string | null
  detalles: LineaOrdenPayload[]
}

export type TipoIncidencia = 'FALTANTE' | 'DANADO' | 'EXCEDENTE' | 'PRODUCTO_INCORRECTO' | 'OTRO'

/** Novedad de una entrega: ninguna mueve inventario. */
export interface IncidenciaRecepcion {
  id: number
  tipo: TipoIncidencia
  detalleOrdenId?: number
  producto?: ProductoLinea
  cantidad?: number
  descripcion?: string
}

/** RecepcionResponseDTO. Para el trabajador llega sin costoUnitario ni valor. */
export interface Recepcion {
  id: number
  ordenId: number
  ordenNumero: number
  proveedor: string
  recibidaEn: string
  recibidaPor: UsuarioResumen
  observaciones?: string
  detalles: { id: number; detalleOrdenId: number; producto: ProductoLinea; cantidad: number; costoUnitario?: number }[]
  incidencias: IncidenciaRecepcion[]
  unidades: number
  valor?: number
}

export interface IncidenciaPayload {
  tipo: TipoIncidencia
  detalleOrdenId?: number
  cantidad?: number
  descripcion?: string
}

export interface RecepcionPayload {
  /** Una por intento de registro: reintentar con la misma no duplica la mercancía. */
  claveIdempotencia: string
  observaciones: string | null
  /** Unidades aceptadas (entran al inventario). Puede ir vacío si solo hay incidencias. */
  detalles: { detalleOrdenId: number; cantidad: number }[]
  incidencias: IncidenciaPayload[]
}

export interface FiltroRecepciones {
  desde?: string
  hasta?: string
  proveedorId?: number
  ordenId?: number
  registradaPorId?: number
  conIncidencias?: boolean
}

export interface ProgramacionPayload {
  fechaEstimada: string
  horaDesde: string | null
  horaHasta: string | null
  confirmadaProveedor: boolean
  referenciaProveedor: string | null
  observaciones: string | null
  /** Obligatorio al reprogramar. */
  motivo: string | null
}

/** Versión documental de una orden emitida (DocumentoOrdenDTO). */
export interface DocumentoOrden {
  version: number
  motivo: string
  generadoEn: string
  generadoPor: UsuarioResumen
  huellaSha256: string
}

export type TipoEventoHistorial =
  | 'SOLICITUD_CREADA'
  | 'SOLICITUD_APROBADA'
  | 'ORDEN_CREADA'
  | 'ORDEN_EMITIDA'
  | 'DOCUMENTO_GENERADO'
  | 'ENTREGA_PROGRAMADA'
  | 'ENTREGA_REPROGRAMADA'
  | 'RECEPCION_PARCIAL'
  | 'RECEPCION_FINAL'
  | 'RECEPCION_CON_INCIDENCIAS'
  | 'ORDEN_CANCELADA'

/** Hecho de la vida de la orden (EventoHistorialDTO). */
export interface EventoHistorial {
  tipo: TipoEventoHistorial
  instante: string
  usuario?: UsuarioResumen
  descripcion: string
  documento?: DocumentoOrden
  programacion?: ProgramacionEntrega
  recepcion?: Recepcion
  movimientos?: { id: number; detalleRecepcionId: number; producto: ProductoLinea; cantidad: number; saldoResultante: number }[]
}

export interface FiltroOrdenes {
  estado?: EstadoOrdenCompra
  proveedorId?: number
  solicitudId?: number
}

/** Número visible de una orden, igual que en los mensajes del backend: OC-00042. */
export function numeroOrden(numero: number): string {
  return `OC-${String(numero).padStart(5, '0')}`
}

const BASE = '/v1/ordenes-compra'

export const ordenCompraService = {
  async listar(params: PageParams & FiltroOrdenes, signal?: AbortSignal): Promise<Page<OrdenCompra>> {
    const { data } = await api.get<Page<OrdenCompra>>(BASE, { params, signal })
    return data
  },

  async obtener(id: number, signal?: AbortSignal): Promise<OrdenCompra> {
    const { data } = await api.get<OrdenCompra>(`${BASE}/${id}`, { signal })
    return data
  },

  async crear(payload: OrdenCompraPayload): Promise<OrdenCompra> {
    const { data } = await api.post<OrdenCompra>(BASE, payload)
    return data
  },

  /** Solo en BORRADOR (409 en otro estado). */
  async actualizar(id: number, payload: Omit<OrdenCompraPayload, 'proveedorId'>): Promise<OrdenCompra> {
    const { data } = await api.put<OrdenCompra>(`${BASE}/${id}`, payload)
    return data
  },

  async emitir(id: number): Promise<OrdenCompra> {
    const { data } = await api.post<OrdenCompra>(`${BASE}/${id}/emitir`)
    return data
  },

  async cancelar(id: number, motivo: string): Promise<OrdenCompra> {
    const { data } = await api.post<OrdenCompra>(`${BASE}/${id}/cancelar`, { motivo })
    return data
  },

  /** Un borrador por proveedor de los productos de una solicitud aprobada. */
  async crearDesdeSolicitud(solicitudId: number): Promise<OrdenCompra[]> {
    const { data } = await api.post<OrdenCompra[]>(`${BASE}/desde-solicitud/${solicitudId}`)
    return data
  },

  async recepciones(id: number, signal?: AbortSignal): Promise<Recepcion[]> {
    const { data } = await api.get<Recepcion[]>(`${BASE}/${id}/recepciones`, { signal })
    return data
  },

  async registrarRecepcion(id: number, payload: RecepcionPayload): Promise<Recepcion> {
    const { data } = await api.post<Recepcion>(`${BASE}/${id}/recepciones`, payload)
    return data
  },

  async listarRecepciones(params: PageParams & FiltroRecepciones, signal?: AbortSignal): Promise<Page<Recepcion>> {
    const { data } = await api.get<Page<Recepcion>>('/v1/recepciones', { params, signal })
    return data
  },

  /** Las que registró quien pregunta (la identidad la decide el backend por el token). */
  async misRecepciones(params: PageParams & FiltroRecepciones, signal?: AbortSignal): Promise<Page<Recepcion>> {
    const { data } = await api.get<Page<Recepcion>>('/v1/recepciones/mias', { params, signal })
    return data
  },

  async obtenerRecepcion(id: number, signal?: AbortSignal): Promise<Recepcion> {
    const { data } = await api.get<Recepcion>(`/v1/recepciones/${id}`, { signal })
    return data
  },

  /** ADMINISTRADOR_COMERCIAL: programa o reprograma (con motivo) la entrega; cada cambio queda en el historial. */
  async programarEntrega(id: number, payload: ProgramacionPayload): Promise<ProgramacionEntrega> {
    const { data } = await api.put<ProgramacionEntrega>(`${BASE}/${id}/entrega`, payload)
    return data
  },

  async historialEntrega(id: number, signal?: AbortSignal): Promise<ProgramacionEntrega[]> {
    const { data } = await api.get<ProgramacionEntrega[]>(`${BASE}/${id}/entrega/historial`, { signal })
    return data
  },

  async historial(id: number, signal?: AbortSignal): Promise<EventoHistorial[]> {
    const { data } = await api.get<EventoHistorial[]>(`${BASE}/${id}/historial`, { signal })
    return data
  },

  async documentos(id: number, signal?: AbortSignal): Promise<DocumentoOrden[]> {
    const { data } = await api.get<DocumentoOrden[]>(`${BASE}/${id}/documentos`, { signal })
    return data
  },

  /**
   * Descarga el documento de la orden emitida: PDF (formato prioritario para compartir) o Word.
   * Los dos salen de la misma instantánea de la emisión y llevan la misma versión y huella.
   */
  async descargarDocumento(id: number, formato: 'pdf' | 'docx' = 'pdf'): Promise<{ nombre: string; reconstruido: boolean }> {
    const url = formato === 'pdf' ? `${BASE}/${id}/documento/pdf` : `${BASE}/${id}/documento`
    const { nombre, cabeceras } = await descargarArchivo(url, `orden-${id}.${formato}`)
    return { nombre, reconstruido: cabeceras['x-documento-reconstruido'] === 'true' }
  },
}

export { nombreDeArchivo } from '@/lib/descargas'
