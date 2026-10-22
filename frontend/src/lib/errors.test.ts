import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'
import { getApiErrorMessage } from './errors'

function respuesta(status: number, data: unknown, headers: Record<string, string> = {}) {
  return new AxiosError('error', 'ERR_BAD_REQUEST', undefined, undefined, {
    status,
    data,
    statusText: '',
    headers: new AxiosHeaders(headers),
    config: {},
  } as AxiosResponse)
}

describe('getApiErrorMessage: 429 (límite de intentos)', () => {
  it('usa el detalle del Problem Details del backend', () => {
    const detalle = 'Demasiados intentos fallidos con este usuario. Espera 15 minutos e inténtalo de nuevo.'
    const error = respuesta(429, { title: 'Demasiados intentos', status: 429, detail: detalle, reintentarEnSegundos: 900 }, { 'retry-after': '900' })
    expect(getApiErrorMessage(error)).toBe(detalle)
  })

  it('sin cuerpo legible, arma el mensaje con Retry-After', () => {
    expect(getApiErrorMessage(respuesta(429, '<html>429</html>', { 'retry-after': '120' }))).toBe(
      'Demasiados intentos. Espera 2 minutos e inténtalo de nuevo.',
    )
    expect(getApiErrorMessage(respuesta(429, '', { 'retry-after': '1' }))).toBe('Demasiados intentos. Espera 1 segundo e inténtalo de nuevo.')
    expect(getApiErrorMessage(respuesta(429, ''))).toBe('Demasiados intentos. Espera unos minutos e inténtalo de nuevo.')
  })

  it('no cambia el resto de errores', () => {
    expect(getApiErrorMessage(respuesta(401, { detail: 'Usuario o contraseña incorrectos.' }))).toBe('Usuario o contraseña incorrectos.')
    expect(getApiErrorMessage(respuesta(400, { detail: 'x', errors: { username: 'El usuario es obligatorio.' } }))).toBe(
      'El usuario es obligatorio.',
    )
    expect(getApiErrorMessage(respuesta(500, '<html>'))).toBe('Ha ocurrido un error inesperado. Inténtalo de nuevo.')
  })
})
