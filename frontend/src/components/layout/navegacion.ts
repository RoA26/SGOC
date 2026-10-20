import {
  Boxes,
  Building,
  Building2,
  CalendarDays,
  ClipboardList,
  FileText,
  ChartColumn,
  House,
  Receipt,
  ShoppingCart,
  Tags,
  Package,
  PackageCheck,
  PackageOpen,
  Ticket,
  Truck,
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
 * Menú del SGOC según el rol y lo que el backend permite. Órdenes de compra, entregas y recepciones
 * son de gestión (ADMINISTRADOR_COMERCIAL; el SUPER_ADMIN las consulta); el trabajador recibe la mercancía en
 * "Recibir mercancía"; el calendario y el inventario los consulta toda la empresa.
 * Ocultar una opción no protege nada: el backend autoriza cada petición.
 */
export function construirNavegacion(rol: Rol | null, permisos: Permisos): GrupoNavegacion[] {
  const grupos: GrupoNavegacion[] = [{ titulo: null, items: [{ to: '/', label: 'Inicio', icon: House, end: true }] }]

  const compras: ItemNavegacion[] = [
    { to: '/solicitudes', label: permisos.verTodasLasSolicitudes ? 'Solicitudes' : 'Mis solicitudes', icon: ClipboardList },
  ]
  if (permisos.verCompras) {
    compras.push({ to: '/ordenes-compra', label: 'Órdenes de compra', icon: FileText })
    compras.push({ to: '/entregas', label: 'Entregas', icon: Truck })
    compras.push({ to: '/recepciones', label: 'Recepciones', icon: PackageCheck })
  }
  grupos.push({ titulo: 'Compras', items: compras })

  // Ventas: la caja para quien vende; historial (el trabajador, las suyas); la gestión comercial y
  // los reportes (con costos y rentabilidad) solo para la gestión dentro de una empresa.
  const ventas: ItemNavegacion[] = []
  if (permisos.vender) ventas.push({ to: '/pos', label: 'Punto de venta', icon: ShoppingCart })
  if (permisos.verVentas) {
    ventas.push({ to: '/ventas', label: permisos.verComercial ? 'Ventas' : 'Mis ventas', icon: Receipt })
  }
  if (permisos.verComercial) {
    ventas.push({ to: '/reportes', label: 'Reportes', icon: ChartColumn })
    ventas.push({ to: '/comercial', label: 'Gestión comercial', icon: Tags })
  }
  if (ventas.length > 0) grupos.push({ titulo: 'Ventas', items: ventas })

  // Operación diaria. El trabajador recibe la mercancía; el gerente programa y supervisa desde Entregas.
  const operacion: ItemNavegacion[] = []
  if (permisos.registrarRecepciones && !permisos.supervisarEntregas) {
    operacion.push({ to: '/recepcion', label: 'Recibir mercancía', icon: PackageOpen })
  }
  if (permisos.verCalendario) {
    operacion.push({ to: '/calendario', label: 'Calendario', icon: CalendarDays })
  }
  if (operacion.length > 0) {
    grupos.push({ titulo: 'Operación', items: operacion })
  }
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
  // SUPER_ADMIN: empresas y sus administradores. ADMINISTRADOR_COMERCIAL: invitar trabajadores a su empresa.
  if (permisos.gestionarEmpresas) {
    grupos.push({ titulo: 'Administración', items: [{ to: '/admin/empresas', label: 'Empresas', icon: Building }] })
  } else if (permisos.gestionarInvitaciones) {
    grupos.push({ titulo: 'Administración', items: [{ to: '/admin/invitaciones', label: 'Invitar usuarios', icon: Ticket }] })
  }
  return grupos
}
