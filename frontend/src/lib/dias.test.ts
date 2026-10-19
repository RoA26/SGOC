import { describe, expect, it } from 'vitest'
import { aDate, cuadriculaMes, formatearDia, hoyEnBogota, inicioSemana, semana, sumarDias, sumarMeses } from './dias'

describe('días del calendario (sin zona horaria)', () => {
  it('no corre la fecha un día hacia atrás en Colombia', () => {
    expect(aDate('2026-11-03').getDate()).toBe(3)
    expect(formatearDia('2026-11-03')).toContain('3')
    expect(formatearDia('2026-11-03')).toContain('nov')
  })

  it('las semanas empiezan en lunes y la cuadrícula del mes tiene 42 días', () => {
    expect(inicioSemana('2026-11-01')).toBe('2026-10-26') // domingo → lunes anterior
    expect(semana('2026-11-04')).toEqual([
      '2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05', '2026-11-06', '2026-11-07', '2026-11-08',
    ])
    const dias = cuadriculaMes('2026-11-15')
    expect(dias).toHaveLength(42)
    expect(dias[0]).toBe('2026-10-26')
    expect(dias).toContain('2026-11-30')
  })

  it('suma días y meses sin desbordar', () => {
    expect(sumarDias('2026-12-31', 1)).toBe('2027-01-01')
    expect(sumarMeses('2026-01-31', 1)).toBe('2026-02-28')
    expect(sumarMeses('2026-03-15', -3)).toBe('2025-12-15')
  })

  it('hoy es el día de Colombia aunque en UTC ya sea mañana', () => {
    // 02:00 UTC del 20/10 = 21:00 del 19/10 en Bogotá.
    expect(hoyEnBogota(new Date('2026-10-20T02:00:00Z'))).toBe('2026-10-19')
  })
})
