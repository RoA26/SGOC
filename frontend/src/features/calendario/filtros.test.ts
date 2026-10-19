import { describe, expect, it } from 'vitest'
import type { EventoCalendario } from '@/services/calendarioService'
import { agruparPorDia, filtrarEventos, horaDeEvento, TIPOS_EVENTO } from './filtros'
import { rangoDeVista } from './rango'

function evento(parcial: Partial<EventoCalendario>): EventoCalendario {
  return {
    id: 'e', tipo: 'ENTREGA', estado: 'PROGRAMADA', fecha: '2026-11-03', todoElDia: true, titulo: 'Entrega OC-00001',
    confirmada: false, parcial: false, incidencias: 0, ...parcial,
  }
}

describe('filtros del calendario', () => {
  const eventos = [
    evento({ id: 'a', estado: 'RETRASADA' }),
    evento({ id: 'b', estado: 'CONFIRMADA', parcial: true }),
    evento({ id: 'c', tipo: 'RECEPCION', estado: 'PARCIAL', incidencias: 2, todoElDia: false, horaInicio: '09:15' }),
    evento({ id: 'd', tipo: 'RECEPCION', estado: 'COMPLETA', todoElDia: false, horaInicio: '15:00' }),
    evento({ id: 'e', tipo: 'SOLICITUD_PENDIENTE', estado: 'PENDIENTE', fecha: '2026-11-04' }),
  ]
  const todos = new Set(TIPOS_EVENTO)

  it('filtra por tipo y por estado', () => {
    expect(filtrarEventos(eventos, new Set(['RECEPCION'] as const), 'TODOS').map((e) => e.id)).toEqual(['c', 'd'])
    expect(filtrarEventos(eventos, todos, 'RETRASADAS').map((e) => e.id)).toEqual(['a'])
    expect(filtrarEventos(eventos, todos, 'POR_LLEGAR').map((e) => e.id)).toEqual(['b'])
    expect(filtrarEventos(eventos, todos, 'PARCIALES').map((e) => e.id)).toEqual(['b', 'c'])
    expect(filtrarEventos(eventos, todos, 'COMPLETAS').map((e) => e.id)).toEqual(['d'])
    expect(filtrarEventos(eventos, todos, 'CON_INCIDENCIAS').map((e) => e.id)).toEqual(['c'])
  })

  it('agrupa por día y no inventa horas', () => {
    const porDia = agruparPorDia(eventos)
    expect(porDia.get('2026-11-03')).toHaveLength(4)
    expect(porDia.get('2026-11-04')).toHaveLength(1)
    expect(horaDeEvento(evento({ todoElDia: true }))).toBeNull()
    expect(horaDeEvento(evento({ todoElDia: false, horaInicio: '08:00', horaFin: '10:30' }))).toBe('08:00–10:30')
    expect(horaDeEvento(evento({ todoElDia: false, horaInicio: '14:05' }))).toBe('14:05')
  })

  it('pide al backend exactamente el período visible', () => {
    expect(rangoDeVista('mes', '2026-11-15')).toEqual({ desde: '2026-10-26', hasta: '2026-12-06' })
    expect(rangoDeVista('semana', '2026-11-04')).toEqual({ desde: '2026-11-02', hasta: '2026-11-08' })
    expect(rangoDeVista('dia', '2026-11-04')).toEqual({ desde: '2026-11-04', hasta: '2026-11-04' })
  })
})
