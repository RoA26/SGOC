import { describe, expect, it } from 'vitest'
import type { ProductoVenta } from '@/services/ventaService'
import { aPedido, carritoReducer, excedeExistencias, faltaPorPagar, validarDescuento, type LineaCarrito } from './carrito'

const camisa: ProductoVenta = {
  id: 1, sku: 'CAM-01', nombre: 'Camisa', precioVenta: 10000, tratamientoIva: 'GRAVADO', tarifaIva: 19,
  precioConIva: 11900, existencias: 3, estadoStock: 'NORMAL',
}
const arroz: ProductoVenta = { ...camisa, id: 2, sku: 'ARR-01', nombre: 'Arroz', tratamientoIva: 'EXCLUIDO', tarifaIva: 0 }

describe('carrito de la caja', () => {
  it('agrega, suma en la misma línea, cambia cantidades y quita', () => {
    let c: LineaCarrito[] = []
    c = carritoReducer(c, { tipo: 'agregar', producto: camisa })
    c = carritoReducer(c, { tipo: 'agregar', producto: camisa })
    c = carritoReducer(c, { tipo: 'agregar', producto: arroz, cantidad: 4 })
    expect(c.map((l) => [l.producto.sku, l.cantidad])).toEqual([['CAM-01', 2], ['ARR-01', 4]])
    c = carritoReducer(c, { tipo: 'cantidad', productoId: 1, cantidad: 5 })
    expect(c[0]!.cantidad).toBe(5)
    expect(excedeExistencias(c[0]!)).toBe(true)
    // Cantidad 0 o inválida retira la línea (antes de confirmar).
    c = carritoReducer(c, { tipo: 'cantidad', productoId: 2, cantidad: 0 })
    expect(c).toHaveLength(1)
    c = carritoReducer(c, { tipo: 'quitar', productoId: 1 })
    expect(c).toEqual([])
  })

  it('envía solo producto, cantidad y descuento pedido (nunca precios ni impuestos)', () => {
    let c = carritoReducer([], { tipo: 'agregar', producto: camisa, cantidad: 2 })
    c = carritoReducer(c, { tipo: 'descuento', productoId: 1, descuento: { tipo: 'PORCENTAJE', valor: 10 } })
    c = carritoReducer(c, { tipo: 'agregar', producto: arroz })
    const pedido = aPedido(c)
    expect(pedido).toEqual([
      { productoId: 1, cantidad: 2, descuento: { tipo: 'PORCENTAJE', valor: 10 } },
      { productoId: 2, cantidad: 1 },
    ])
    expect(JSON.stringify(pedido)).not.toMatch(/precio|total|iva|impuesto/i)
  })

  it('valida los descuentos antes de pedir la cotización', () => {
    expect(validarDescuento('PORCENTAJE', '')).toBeNull()
    expect(validarDescuento('PORCENTAJE', '10')).toBeNull()
    expect(validarDescuento('PORCENTAJE', '12,5')).toBeNull()
    expect(validarDescuento('PORCENTAJE', '101')).toMatch(/100 %/)
    expect(validarDescuento('VALOR', '-5')).toMatch(/mayor que 0/)
    expect(validarDescuento('VALOR', 'abc')).toMatch(/mayor que 0/)
    expect(validarDescuento('VALOR', '1.234')).toMatch(/2 decimales/)
  })

  it('calcula lo que falta por pagar sin errores de coma flotante', () => {
    expect(faltaPorPagar(23920.1, [{ valor: 20000 }, { valor: 3920.1 }])).toBe(0)
    expect(faltaPorPagar(11900, [{ valor: 5000 }])).toBe(6900)
    expect(faltaPorPagar(100, [{ valor: Number.NaN }])).toBe(100)
  })
})
