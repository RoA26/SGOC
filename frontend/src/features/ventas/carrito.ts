import type { Descuento, LineaPedido, ProductoVenta } from '@/services/ventaService'

/**
 * Carrito de la caja. Solo guarda qué se pide (producto, cantidad, descuento pedido); los importes
 * los calcula siempre el backend (cotización y confirmación), con precios e IVA del catálogo.
 */
export interface LineaCarrito {
  producto: ProductoVenta
  cantidad: number
  descuento?: Descuento
}

export const CANTIDAD_MAXIMA = 9999

export type AccionCarrito =
  | { tipo: 'agregar'; producto: ProductoVenta; cantidad?: number }
  | { tipo: 'cantidad'; productoId: number; cantidad: number }
  | { tipo: 'descuento'; productoId: number; descuento?: Descuento }
  | { tipo: 'quitar'; productoId: number }
  | { tipo: 'vaciar' }

/** Agregar un producto que ya está suma unidades (una sola línea por producto, como exige el backend). */
export function carritoReducer(lineas: LineaCarrito[], accion: AccionCarrito): LineaCarrito[] {
  switch (accion.tipo) {
    case 'agregar': {
      const sumar = accion.cantidad ?? 1
      const existente = lineas.find((l) => l.producto.id === accion.producto.id)
      if (existente) {
        return lineas.map((l) =>
          l.producto.id === accion.producto.id
            ? { ...l, producto: accion.producto, cantidad: Math.min(CANTIDAD_MAXIMA, l.cantidad + sumar) }
            : l,
        )
      }
      return [...lineas, { producto: accion.producto, cantidad: Math.min(CANTIDAD_MAXIMA, Math.max(1, sumar)) }]
    }
    case 'cantidad': {
      if (!Number.isInteger(accion.cantidad) || accion.cantidad < 1) {
        return lineas.filter((l) => l.producto.id !== accion.productoId)
      }
      return lineas.map((l) =>
        l.producto.id === accion.productoId ? { ...l, cantidad: Math.min(CANTIDAD_MAXIMA, accion.cantidad) } : l,
      )
    }
    case 'descuento':
      return lineas.map((l) => (l.producto.id === accion.productoId ? { ...l, descuento: accion.descuento } : l))
    case 'quitar':
      return lineas.filter((l) => l.producto.id !== accion.productoId)
    case 'vaciar':
      return []
  }
}

/** Lo que viaja al backend: sin precios, impuestos ni totales. */
export function aPedido(lineas: LineaCarrito[]): LineaPedido[] {
  return lineas.map((l) => ({
    productoId: l.producto.id,
    cantidad: l.cantidad,
    ...(l.descuento && l.descuento.valor > 0 ? { descuento: l.descuento } : {}),
  }))
}

/** Advertencia local (el backend vuelve a comprobarlo con las existencias bloqueadas). */
export function excedeExistencias(linea: LineaCarrito): boolean {
  return linea.cantidad > linea.producto.existencias
}

/** Valida un descuento escrito por quien vende; devuelve el mensaje de error o null. */
export function validarDescuento(tipo: Descuento['tipo'], texto: string): string | null {
  if (texto.trim() === '') return null
  const valor = Number(texto.replace(',', '.'))
  if (!Number.isFinite(valor) || valor <= 0) return 'El descuento debe ser mayor que 0.'
  if (Math.round(valor * 100) !== valor * 100) return 'Máximo 2 decimales.'
  if (tipo === 'PORCENTAJE' && valor > 100) return 'El porcentaje no puede superar el 100 %.'
  return null
}

/** Suma de pagos frente al total calculado por el backend. */
export function faltaPorPagar(total: number, pagos: { valor: number }[]): number {
  const pagado = pagos.reduce((suma, p) => suma + (Number.isFinite(p.valor) ? p.valor : 0), 0)
  return Math.round((total - pagado) * 100) / 100
}
