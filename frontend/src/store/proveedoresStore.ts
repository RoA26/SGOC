import type { ProveedorResumen } from '@/services/productoService'
import { proveedorService } from '@/services/proveedorService'
import { crearCatalogoStore, MAX_OPCIONES } from './catalogoStore'

/**
 * Proveedores activos, por razón social. Decide si se puede crear un producto (hace falta al
 * menos uno) y alimenta el selector del formulario de producto.
 */
export const useProveedoresStore = crearCatalogoStore<ProveedorResumen>(async (signal) => {
  const { content } = await proveedorService.listar({ page: 0, size: MAX_OPCIONES, sort: 'razonSocial,asc' }, signal)
  return content.map(({ id, nit, razonSocial }) => ({ id, nit, razonSocial }))
})
