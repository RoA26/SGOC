import { productoService, type Producto } from '@/services/productoService'
import { crearCatalogoStore, MAX_OPCIONES } from './catalogoStore'

/**
 * Productos activos, por nombre. Alimenta el selector de las líneas de una solicitud y
 * decide si se puede crear una (hace falta al menos un producto).
 */
export const useProductosStore = crearCatalogoStore<Producto>(async (signal) => {
  const { content } = await productoService.listar({ page: 0, size: MAX_OPCIONES, sort: 'nombre,asc' }, signal)
  return content
})
