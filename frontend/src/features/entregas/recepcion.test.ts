import { describe, expect, it } from 'vitest'
import { construirRecepcion, type LineaARecibir } from './recepcion'

const producto = (id: number) => ({ id, sku: `SKU-${id}`, nombre: `Producto ${id}`, activo: true })
const lineas: LineaARecibir[] = [
  { id: 10, producto: producto(1), cantidad: 10, pendiente: 8 },
  { id: 20, producto: producto(2), cantidad: 5, pendiente: 5 },
]

describe('armado de la recepción', () => {
  it('separa lo aceptado (inventario) de las incidencias', () => {
    const r = construirRecepcion(
      lineas,
      { 10: { recibidas: '5', danadas: '2', faltantes: '1' }, 20: { recibidas: '', danadas: '', faltantes: '' } },
      [
        { clave: 1, tipo: 'EXCEDENTE', lineaId: '20', cantidad: '3', descripcion: '' },
        { clave: 2, tipo: 'PRODUCTO_INCORRECTO', lineaId: '', cantidad: '', descripcion: 'Llegó una arandela' },
      ],
    )
    expect(r.erroresLinea).toEqual({})
    expect(r.erroresOtra).toEqual({})
    expect(r.detalles).toEqual([{ detalleOrdenId: 10, cantidad: 5 }])
    expect(r.incidencias).toEqual([
      { tipo: 'DANADO', detalleOrdenId: 10, cantidad: 2 },
      { tipo: 'FALTANTE', detalleOrdenId: 10, cantidad: 1 },
      { tipo: 'EXCEDENTE', detalleOrdenId: 20, cantidad: 3, descripcion: undefined },
      { tipo: 'PRODUCTO_INCORRECTO', detalleOrdenId: undefined, cantidad: undefined, descripcion: 'Llegó una arandela' },
    ])
  })

  it('no permite superar lo pendiente ni números no enteros', () => {
    const r = construirRecepcion(
      lineas,
      { 10: { recibidas: '6', danadas: '2', faltantes: '1' }, 20: { recibidas: '2.5', danadas: '', faltantes: '' } },
      [],
    )
    expect(r.erroresLinea[10]).toContain('solo quedan 8')
    expect(r.erroresLinea[20]).toContain('enteros')
    expect(r.detalles).toEqual([])
  })

  it('exige los datos de cada novedad', () => {
    const r = construirRecepcion(lineas, {}, [
      { clave: 1, tipo: 'EXCEDENTE', lineaId: '', cantidad: '2', descripcion: '' },
      { clave: 2, tipo: 'OTRO', lineaId: '', cantidad: '', descripcion: '  ' },
      { clave: 3, tipo: 'PRODUCTO_INCORRECTO', lineaId: '', cantidad: '0', descripcion: 'x' },
    ])
    expect(Object.keys(r.erroresOtra)).toEqual(['1', '2', '3'])
    expect(r.incidencias).toEqual([])
  })
})
