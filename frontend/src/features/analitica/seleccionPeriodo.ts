import { hoyNegocio, restarDias } from '@/lib/periodo'
import type { FiltroPeriodo as Filtro } from '@/services/analiticaService'

export type SeleccionPeriodo = { tipo: 'preset'; dias: number } | { tipo: 'personalizado'; desde: string; hasta: string }

export const PERIODO_POR_DEFECTO: SeleccionPeriodo = { tipo: 'preset', dias: 30 }

/** Parámetros para el backend. Los presets no envían `hasta`: el backend usa hoy en Colombia. */
export function aFiltro(seleccion: SeleccionPeriodo): Filtro {
  return seleccion.tipo === 'preset'
    ? { desde: restarDias(hoyNegocio(), seleccion.dias - 1) }
    : { desde: seleccion.desde, hasta: seleccion.hasta }
}
