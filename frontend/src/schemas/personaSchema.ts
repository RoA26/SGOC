import { z } from 'zod'

/** Nombres y apellidos: replican DatosPersonalesDTO (obligatorios, máximo 100 caracteres cada uno). */
export const nombresSchema = z
  .string()
  .trim()
  .min(1, 'Los nombres son obligatorios.')
  .max(100, 'Los nombres no pueden superar 100 caracteres.')

export const apellidosSchema = z
  .string()
  .trim()
  .min(1, 'Los apellidos son obligatorios.')
  .max(100, 'Los apellidos no pueden superar 100 caracteres.')

export const datosPersonalesSchema = z.object({ nombres: nombresSchema, apellidos: apellidosSchema })

export type DatosPersonalesFormValues = z.input<typeof datosPersonalesSchema>
export type DatosPersonalesPayload = z.output<typeof datosPersonalesSchema>
