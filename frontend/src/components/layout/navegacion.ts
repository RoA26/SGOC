import {
  Boxes,
  Building,
  Building2,
  ClipboardList,
  FileText,
  House,
  Package,
  PackageCheck,
  Ticket,
  type LucideIcon,
} from 'lucide-react'
import type { Permisos } from '@/features/auth/permisos'
import type { Rol } from '@/features/auth/types'

export interface ItemNavegacion {
  label: string
  icon: LucideIcon
  /** Ruta; sin ella el elemento se muestra como "Próximamente" (el backend aún no lo soporta). */
  to?: string
  end?: boolean
}

export interface GrupoNavegacion {
  titulo: string | null
  items: ItemNavegacion[]
}

/**
 * Menú del SGOC según el rol y lo que el backend permite. Órdenes de compra y recepciones son de
 * gestión (ADMIN_EMPRESA; el SUPER_ADMIN las consulta); el inventario lo consulta toda la empresa.
 * Ocultar una opción no protege nada: el backend autoriza cada petición.
 */
export function construirNavegacion(rol: Rol | null, permisos: Permisos): GrupoNavegacion[] {
  const grupos: GrupoNavegacion[] = [{ titulo: null, items: [{ to: '/', label: 'Inicio', icon: House, end: true }] }]

  const compras: ItemNavegacion[] = [
    { to: '/solicitudes', label: permisos.verTodasLasSolicitudes ? 'Solicitudes' : 'Mis solicitudes', icon: ClipboardList },
  ]
  if (permisos.verCompras) {
    compras.push({ to: '/ordenes-compra', label: 'Órdenes de compra', icon: FileText })
    compras.push({ to: '/recepciones', label: 'Recepciones', icon: PackageCheck })
  }
  grupos.push({ titulo: 'Compras', items: compras })
  if (rol !== null) {
    grupos.push({ titulo: 'Inventario', items: [{ to: '/inventario', label: 'Existencias', icon: Boxes }] })
  }

  if (permisos.verCatalogos) {
    grupos.push({
      titulo: 'Catálogo',
      items: [
        { to: '/productos', label: 'Productos', icon: Package },
        { to: '/proveedores', label: 'Proveedores', icon: Building2 },
      ],
    })
  }
  // SUPER_ADMIN: empresas y sus administradores. ADMIN_EMPRESA: invitar trabajadores a su empresa.
  if (permisos.gestionarEmpresas) {
    grupos.push({ titulo: 'Administración', items: [{ to: '/admin/empresas', label: 'Empresas', icon: Building }] })
  } else if (permisos.gestionarInvitaciones) {
    grupos.push({ titulo: 'Administración', items: [{ to: '/admin/invitaciones', label: 'Invitar usuarios', icon: Ticket }] })
  }
  return grupos
}
