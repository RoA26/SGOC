import { z } from 'zod'
import { PATRONES } from './patrones'

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

/**
 * Alta con código de invitación: replica RegistroRequestDTO (Jakarta Validation) con los
 * mismos mensajes.
 */
export const registroSchema = z.object({
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
  // El backend ignora mayúsculas, espacios y guiones del código; aquí solo se recorta.
  codigoInvitacion: z
    .string()
    .trim()
    .toUpperCase()
    .min(1, 'El código de invitación es obligatorio.')
    .max(64, 'El código de invitación no es válido.'),
})

/** Valores tal como los edita el formulario. */
export type LoginFormValues = z.input<typeof loginSchema>
/** Credenciales ya validadas y normalizadas. */
export type LoginPayload = z.output<typeof loginSchema>

export type RegistroFormValues = z.input<typeof registroSchema>
export type RegistroPayload = z.output<typeof registroSchema>
