import {
  Building2,
  ClipboardList,
  FileText,
  House,
  Package,
  PackageCheck,
  Ticket,
  type LucideIcon,
} from 'lucide-react'
import { ROLES_GESTION, type Permisos } from '@/features/auth/permisos'
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
 * Menú del SGOC según el rol y lo que el backend permite hoy. Órdenes de compra y recepciones
 * aparecen como "Próximamente" para mostrar el flujo completo sin simular datos: aún no tienen
 * endpoints.
 */
export function construirNavegacion(rol: Rol | null, permisos: Permisos): GrupoNavegacion[] {
  const gestor = rol !== null && ROLES_GESTION.includes(rol)
  const grupos: GrupoNavegacion[] = [{ titulo: null, items: [{ to: '/', label: 'Inicio', icon: House, end: true }] }]

  const compras: ItemNavegacion[] = [
    { to: '/solicitudes', label: permisos.verTodasLasSolicitudes ? 'Solicitudes' : 'Mis solicitudes', icon: ClipboardList },
    { label: gestor ? 'Órdenes de compra' : 'Órdenes relacionadas', icon: FileText },
  ]
  if (gestor) {
    compras.push({ label: 'Recepciones', icon: PackageCheck })
  }
  grupos.push({ titulo: 'Compras', items: compras })

  if (permisos.verCatalogos) {
    grupos.push({
      titulo: 'Catálogo',
      items: [
        { to: '/productos', label: 'Productos', icon: Package },
        { to: '/proveedores', label: 'Proveedores', icon: Building2 },
      ],
    })
  }
  if (gestor) {
    grupos.push({ titulo: 'Administración', items: [{ to: '/admin/invitaciones', label: 'Invitar usuarios', icon: Ticket }] })
  }
  return grupos
}
