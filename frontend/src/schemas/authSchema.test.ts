import { describe, expect, it } from 'vitest'
import { registroSchema } from './authSchema'

const DATOS = { nombres: 'Ana', apellidos: 'Ruiz', username: 'ana.ruiz', email: 'ana@empresa.com', password: 'ClaveSegura2026' }

function errores(valores: Record<string, unknown>): Record<string, string> {
  const r = registroSchema.safeParse(valores)
  return r.success ? {} : Object.fromEntries(r.error.issues.map((i) => [i.path.join('.'), i.message]))
}

describe('registroSchema: exactamente un código, como @UnCodigoDeAlta', () => {
  it('con invitación envía solo codigoInvitacion', () => {
    const r = registroSchema.parse({ ...DATOS, via: 'invitacion', codigoInvitacion: ' 7kq2-m9xa-4hpr-t3vw ', codigoEmpresa: 'IGNORADO' })
    expect(r).toEqual({ ...DATOS, codigoInvitacion: '7KQ2-M9XA-4HPR-T3VW' })
    expect(r).not.toHaveProperty('codigoEmpresa')
  })

  it('con el código de la empresa envía solo codigoEmpresa', () => {
    const r = registroSchema.parse({ ...DATOS, via: 'empresa', codigoInvitacion: 'IGNORADO', codigoEmpresa: ' 4hpr-t3vw-7kq2-m9xa ' })
    expect(r).toEqual({ ...DATOS, codigoEmpresa: '4HPR-T3VW-7KQ2-M9XA' })
    expect(r).not.toHaveProperty('codigoInvitacion')
  })

  it('exige el código de la vía elegida, con los mensajes del backend', () => {
    expect(errores({ ...DATOS, via: 'invitacion', codigoInvitacion: '  ', codigoEmpresa: 'X' })).toEqual({
      codigoInvitacion: 'El código de invitación es obligatorio.',
    })
    expect(errores({ ...DATOS, via: 'empresa', codigoInvitacion: 'X', codigoEmpresa: '' })).toEqual({
      codigoEmpresa: 'El código de tu empresa es obligatorio.',
    })
  })

  it('rechaza códigos de más de 64 caracteres (@Size(max = 64))', () => {
    expect(errores({ ...DATOS, via: 'empresa', codigoInvitacion: '', codigoEmpresa: 'A'.repeat(65) })).toEqual({
      codigoEmpresa: 'El código de empresa no es válido.',
    })
    // El código de la otra vía no se valida ni se envía.
    expect(errores({ ...DATOS, via: 'empresa', codigoInvitacion: 'A'.repeat(65), codigoEmpresa: 'ABCD' })).toEqual({})
  })
})
