/**
 * Datos de ejemplo de la página pública. Reproducen la compra de la seed de demostración del
 * backend (empresa ficticia Mercado Verde S.A.S., perfil `demo`): ninguno es un cliente real.
 * Los totales se calculan a partir de las líneas, nunca se escriben aparte.
 */

export const EMPRESA_DEMO = 'Mercado Verde S.A.S. (demo)'
export const PROVEEDOR_DEMO = { razonSocial: 'Lácteos La Pradera S.A.S. (demo)', nit: '999200000012' }
export const SOLICITANTE_DEMO = 'Laura Torres'
export const ADMINISTRADOR_DEMO = 'Carlos Mendoza'
/** Código de barras de ejemplo (EAN-13 ficticio de la seed, prefijo interno 20). */
export const CODIGO_BARRAS_DEMO = '2010000000045'

export interface LineaDemo {
  producto: string
  sku: string
  cantidad: number
  /** Costo unitario en COP enteros. */
  costo: number
  recibido: number
  /** Existencias después de la recepción y mínimo configurado. */
  saldo: number
  minimo: number
}

export const LINEAS_DEMO: readonly LineaDemo[] = [
  { producto: 'Leche entera 1 L', sku: 'MV-LEC-1L', cantidad: 12, costo: 3_400, recibido: 6, saldo: 66, minimo: 15 },
  { producto: 'Huevos (cubeta x30)', sku: 'MV-HUE-30', cantidad: 4, costo: 14_000, recibido: 2, saldo: 8, minimo: 8 },
]

export const subtotal = (linea: LineaDemo): number => linea.cantidad * linea.costo
export const TOTAL_DEMO = LINEAS_DEMO.reduce((total, linea) => total + subtotal(linea), 0)
export const UNIDADES_PEDIDAS = LINEAS_DEMO.reduce((total, linea) => total + linea.cantidad, 0)
export const UNIDADES_RECIBIDAS = LINEAS_DEMO.reduce((total, linea) => total + linea.recibido, 0)

/** Incidencia de la recepción parcial de la seed. */
export const INCIDENCIA_DEMO = { tipo: 'Faltante', producto: 'Leche entera 1 L', cantidad: 2, nota: 'Faltaron dos unidades en la remisión' }

/** Venta de ejemplo para el comprobante interno (precios de venta de la seed; ambos productos
 * son exentos de IVA, así que el total es la suma de las líneas). */
export const VENTA_DEMO = {
  numero: 'V-00001',
  lineas: [
    { producto: 'Leche entera 1 L', cantidad: 2, precio: 4_200 },
    { producto: 'Huevos (cubeta x30)', cantidad: 1, precio: 17_500 },
  ],
  recibido: 30_000,
}
export const TOTAL_VENTA_DEMO = VENTA_DEMO.lineas.reduce((total, linea) => total + linea.cantidad * linea.precio, 0)
export const CAMBIO_VENTA_DEMO = VENTA_DEMO.recibido - TOTAL_VENTA_DEMO

/** Fecha fija del ejemplo (no la de hoy: es un documento de muestra). */
export const FECHA_DEMO = '22 de octubre de 2026'
