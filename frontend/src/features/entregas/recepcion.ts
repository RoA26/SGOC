import type { IncidenciaPayload, ProductoLinea, RecepcionPayload } from '@/services/ordenCompraService'

/** Lógica del formulario de recepción (sin React): validación y armado de la petición. */

export interface LineaARecibir {
  id: number
  producto: ProductoLinea
  cantidad: number
  pendiente: number
}

export interface Conteo {
  recibidas: string
  danadas: string
  faltantes: string
}

export type TipoOtra = 'EXCEDENTE' | 'PRODUCTO_INCORRECTO' | 'OTRO'

export interface Otra {
  clave: number
  tipo: TipoOtra
  lineaId: string
  cantidad: string
  descripcion: string
}

/** "" = 0; null si no es un entero no negativo. */
export function entero(texto: string): number | null {
  if (texto.trim() === '') return 0
  const valor = Number(texto)
  return Number.isInteger(valor) && valor >= 0 ? valor : null
}

/** Valida el formulario y arma la petición; las reglas son las mismas que aplica el backend. */
export function construirRecepcion(
  lineas: LineaARecibir[],
  conteos: Record<number, Conteo>,
  otras: Otra[],
): { erroresLinea: Record<number, string>; erroresOtra: Record<number, string>; detalles: RecepcionPayload['detalles']; incidencias: IncidenciaPayload[] } {
  const erroresLinea: Record<number, string> = {}
  const erroresOtra: Record<number, string> = {}
  const detalles: RecepcionPayload['detalles'] = []
  const incidencias: IncidenciaPayload[] = []
  for (const linea of lineas) {
    const conteo = conteos[linea.id] ?? { recibidas: '', danadas: '', faltantes: '' }
    const recibidas = entero(conteo.recibidas)
    const danadas = entero(conteo.danadas)
    const faltantes = entero(conteo.faltantes)
    if (recibidas === null || danadas === null || faltantes === null) {
      erroresLinea[linea.id] = 'Usa números enteros (0 o más).'
      continue
    }
    if (recibidas + danadas + faltantes > linea.pendiente) {
      erroresLinea[linea.id] = `Recibidas, dañadas y faltantes suman ${recibidas + danadas + faltantes}; solo quedan ${linea.pendiente} pendientes. Si llegaron de más, infórmalo como excedente.`
      continue
    }
    if (recibidas > 0) detalles.push({ detalleOrdenId: linea.id, cantidad: recibidas })
    if (danadas > 0) incidencias.push({ tipo: 'DANADO', detalleOrdenId: linea.id, cantidad: danadas })
    if (faltantes > 0) incidencias.push({ tipo: 'FALTANTE', detalleOrdenId: linea.id, cantidad: faltantes })
  }
  for (const otra of otras) {
    const cantidad = otra.cantidad.trim() === '' ? undefined : entero(otra.cantidad)
    const descripcion = otra.descripcion.trim() || undefined
    if (cantidad === null || cantidad === 0) {
      erroresOtra[otra.clave] = 'La cantidad debe ser un entero mayor que 0.'
    } else if (otra.tipo === 'EXCEDENTE' && (!otra.lineaId || cantidad === undefined)) {
      erroresOtra[otra.clave] = 'Indica el producto y cuántas unidades llegaron de más.'
    } else if (otra.tipo !== 'EXCEDENTE' && !descripcion) {
      erroresOtra[otra.clave] = 'Describe la novedad.'
    } else {
      incidencias.push({
        tipo: otra.tipo,
        detalleOrdenId: otra.lineaId ? Number(otra.lineaId) : undefined,
        cantidad,
        descripcion,
      })
    }
  }
  return { erroresLinea, erroresOtra, detalles, incidencias }
}

