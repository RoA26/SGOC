import { describe, expect, it } from 'vitest'
import { calcularPermisos } from '@/features/auth/permisos'
import type { Rol } from '@/features/auth/types'
import { construirNavegacion } from './navegacion'

function rutas(rol: Rol, modoGlobal = false): string[] {
  const permisos = calcularPermisos({ rol, empresaId: modoGlobal ? null : 1, esSuperAdmin: rol === 'SUPER_ADMIN', modoGlobal, empresaSupervisada: null })
  return construirNavegacion(rol, permisos).flatMap((g) => g.items.map((i) => i.to ?? ''))
}

describe('menú según el rol', () => {
  it('el trabajador recibe mercancía y ve el calendario, pero no la supervisión', () => {
    const r = rutas('TRABAJADOR')
    expect(r).toContain('/recepcion')
    expect(r).toContain('/calendario')
    expect(r).not.toContain('/entregas')
    expect(r).not.toContain('/ordenes-compra')
  })

  it('el administrador supervisa entregas y recepciones (no duplica la vista del trabajador)', () => {
    const r = rutas('ADMIN_EMPRESA')
    expect(r).toEqual(expect.arrayContaining(['/entregas', '/recepciones', '/calendario', '/ordenes-compra']))
    expect(r).not.toContain('/recepcion')
  })

  it('el SUPER_ADMIN no registra recepciones ni programa, y sin empresa no tiene calendario', () => {
    const supervisando = calcularPermisos({ rol: 'SUPER_ADMIN', empresaId: 1, esSuperAdmin: true, modoGlobal: false, empresaSupervisada: null })
    expect(supervisando.registrarRecepciones).toBe(false)
    expect(supervisando.programarEntregas).toBe(false)
    expect(supervisando.verCalendario).toBe(true)
    expect(rutas('SUPER_ADMIN', true)).not.toContain('/calendario')
  })
})
