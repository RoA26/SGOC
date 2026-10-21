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
    const r = rutas('ADMINISTRADOR_COMERCIAL')
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

  it('el trabajador vende y ve sus ventas, sin gestión comercial ni reportes', () => {
    const r = rutas('TRABAJADOR')
    expect(r).toEqual(expect.arrayContaining(['/pos', '/ventas']))
    expect(r).not.toContain('/comercial')
    expect(r).not.toContain('/reportes')
  })

  it('el administrador comercial vende, gestiona precios y ve reportes', () => {
    expect(rutas('ADMINISTRADOR_COMERCIAL')).toEqual(expect.arrayContaining(['/pos', '/ventas', '/reportes', '/comercial']))
    const permisos = calcularPermisos({ rol: 'ADMINISTRADOR_COMERCIAL', empresaId: 1, esSuperAdmin: false, modoGlobal: false, empresaSupervisada: null })
    expect(permisos.gestionarPrecios).toBe(true)
    expect(permisos.gestionarVentas).toBe(true)
  })

  it('el SUPER_ADMIN no vende ni cambia precios; consulta ventas y reportes solo supervisando', () => {
    const supervisando = calcularPermisos({ rol: 'SUPER_ADMIN', empresaId: 1, esSuperAdmin: true, modoGlobal: false, empresaSupervisada: null })
    expect(supervisando.vender).toBe(false)
    expect(supervisando.gestionarPrecios).toBe(false)
    expect(supervisando.verComercial).toBe(true)
    expect(rutas('SUPER_ADMIN', true)).not.toEqual(expect.arrayContaining(['/ventas']))
    expect(rutas('SUPER_ADMIN', true)).not.toContain('/pos')
  })

  it('el administrador comercial gestiona su personal; ni el trabajador ni el SUPER_ADMIN lo ven', () => {
    expect(rutas('ADMINISTRADOR_COMERCIAL')).toEqual(expect.arrayContaining(['/admin/personal', '/admin/invitaciones']))
    expect(rutas('TRABAJADOR')).not.toContain('/admin/personal')
    expect(rutas('SUPER_ADMIN', true)).not.toContain('/admin/personal')
  })
})
