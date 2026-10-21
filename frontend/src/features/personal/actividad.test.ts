import { describe, expect, it } from 'vitest'
import { accionesDisponibles, describirUltimaOperacion, resumirRecientes } from './actividad'
import type { ActividadTrabajador } from './types'

const SIN_ACTIVIDAD: ActividadTrabajador = { dias: 30, solicitudes: 0, recepciones: 0, ventas: 0 }

describe('actividad del personal', () => {
  it('sin operaciones no inventa ninguna fecha', () => {
    expect(describirUltimaOperacion(SIN_ACTIVIDAD)).toBeNull()
    expect(resumirRecientes(SIN_ACTIVIDAD)).toBeNull()
  })

  it('describe la última operación con su tipo y resume los conteos en singular y plural', () => {
    const actividad: ActividadTrabajador = {
      ultimaOperacion: { tipo: 'VENTA', en: '2026-10-21T15:10:00Z' },
      dias: 30,
      solicitudes: 2,
      recepciones: 1,
      ventas: 0,
    }
    expect(describirUltimaOperacion(actividad)).toMatch(/^Venta · .*2026/)
    expect(resumirRecientes(actividad)).toBe('2 solicitudes · 1 recepción')
  })

  it('ofrece solo las transiciones que acepta el backend', () => {
    expect(accionesDisponibles('PENDIENTE').map((a) => a.destino)).toEqual(['ACTIVO', 'RECHAZADO'])
    expect(accionesDisponibles('ACTIVO').map((a) => a.destino)).toEqual(['INACTIVO'])
    expect(accionesDisponibles('INACTIVO').map((a) => a.destino)).toEqual(['ACTIVO'])
    expect(accionesDisponibles('RECHAZADO').map((a) => a.destino)).toEqual(['ACTIVO'])
    // Quitar o negar el acceso se confirma; concederlo no.
    expect(accionesDisponibles('PENDIENTE').find((a) => a.destino === 'RECHAZADO')?.restrictiva).toBe(true)
    expect(accionesDisponibles('ACTIVO')[0]?.restrictiva).toBe(true)
  })
})
