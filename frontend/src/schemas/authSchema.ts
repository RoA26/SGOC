import { z } from 'zod'
import type { RegistroRequest } from '@/features/auth/types'
import { PATRONES } from './patrones'
import { apellidosSchema, nombresSchema } from './personaSchema'

/** BCrypt solo procesa 72 bytes: el backend rechaza contraseñas más largas (no caracteres). */
const PASSWORD_MAX_BYTES = 72
const encoder = new TextEncoder()

/**
 * Inicio de sesión: replica LoginRequest. El username solo se exige y se normaliza (sin
 * espacios a los lados y en minúsculas, igual que el backend); no se valida contra el patrón
 * porque los usuarios migrados desde su correo pueden tener caracteres que hoy no se admiten.
 */
export const loginSchema = z.object({
  username: z
    .string()
    .trim()
    .toLowerCase()
    .min(1, 'Ingresa tu usuario.')
    .max(50, 'El usuario es demasiado largo.')
    .regex(/^\S+$/, 'El usuario no puede contener espacios.'),
  password: z.string().min(1, 'Ingresa tu contraseña.').max(128, 'La contraseña es demasiado larga.'),
})

/** Cómo se da de alta el trabajador: invitación de un solo uso o código permanente de su empresa. */
export const VIAS_REGISTRO = ['invitacion', 'empresa'] as const
export type ViaRegistro = (typeof VIAS_REGISTRO)[number]

/** Mismo límite que @Size(max = 64) de los dos códigos en RegistroRequestDTO. */
const CODIGO_MAX = 64

/**
 * Alta de un trabajador: replica RegistroRequestDTO (Jakarta Validation) con los mismos mensajes.
 * Solo se valida y se envía el código de la vía elegida: el backend exige exactamente uno
 * (@UnCodigoDeAlta) y asocia sus errores a `codigoInvitacion` o `codigoEmpresa`.
 */
export const registroSchema = z
  .object({
    via: z.enum(VIAS_REGISTRO),
    nombres: nombresSchema,
    apellidos: apellidosSchema,
    username: z
      .string()
      .trim()
      .toLowerCase()
      .min(1, 'El usuario es obligatorio.')
      .regex(/^\S+$/, 'El usuario no puede contener espacios.')
      .regex(
        PATRONES.username,
        'El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo, y empezar y terminar con letra o número.',
      ),
    email: z
      .string()
      .trim()
      .toLowerCase()
      .min(1, 'El correo es obligatorio.')
      .max(320, 'El correo es demasiado largo.')
      .regex(PATRONES.email, 'El correo no tiene un formato válido.'),
    // La contraseña no se recorta: los espacios forman parte de ella.
    password: z
      .string()
      .min(1, 'La contraseña es obligatoria.')
      .min(8, 'La contraseña debe tener entre 8 y 72 caracteres.')
      .max(72, 'La contraseña debe tener entre 8 y 72 caracteres.')
      .refine(
        (valor) => encoder.encode(valor).length <= PASSWORD_MAX_BYTES,
        'La contraseña no puede superar 72 bytes.',
      ),
    // El backend ignora mayúsculas, espacios y guiones de los códigos; aquí solo se recortan.
    codigoInvitacion: z.string().trim().toUpperCase(),
    codigoEmpresa: z.string().trim().toUpperCase(),
  })
  .superRefine(({ via, codigoInvitacion, codigoEmpresa }, ctx) => {
    const [campo, valor, obligatorio, invalido] =
      via === 'invitacion'
        ? (['codigoInvitacion', codigoInvitacion, 'El código de invitación es obligatorio.', 'El código de invitación no es válido.'] as const)
        : (['codigoEmpresa', codigoEmpresa, 'El código de tu empresa es obligatorio.', 'El código de empresa no es válido.'] as const)
    if (valor.length === 0) ctx.addIssue({ code: 'custom', path: [campo], message: obligatorio })
    else if (valor.length > CODIGO_MAX) ctx.addIssue({ code: 'custom', path: [campo], message: invalido })
  })
  .transform(({ via, codigoInvitacion, codigoEmpresa, ...datos }): RegistroRequest =>
    via === 'invitacion' ? { ...datos, codigoInvitacion } : { ...datos, codigoEmpresa },
  )

/** Valores tal como los edita el formulario. */
export type LoginFormValues = z.input<typeof loginSchema>
/** Credenciales ya validadas y normalizadas. */
export type LoginPayload = z.output<typeof loginSchema>

export type RegistroFormValues = z.input<typeof registroSchema>
export type RegistroPayload = z.output<typeof registroSchema>
