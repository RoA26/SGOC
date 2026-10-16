import { z } from 'zod'
import { PATRONES } from './patrones'

/** BCrypt solo procesa 72 bytes: el backend rechaza contraseñas más largas (no caracteres). */
const PASSWORD_MAX_BYTES = 72
const encoder = new TextEncoder()

export const MENSAJE_CODIGO = 'El código debe tener de 3 a 12 letras mayúsculas o números y empezar por una letra (p. ej. NASC01).'

/**
 * Alta de una empresa con su administrador principal: replica EmpresaRequestDTO (Jakarta
 * Validation) con los mismos mensajes y la misma normalización.
 */
export const empresaSchema = z.object({
  nombre: z
    .string()
    .trim()
    .min(1, 'El nombre de la empresa es obligatorio.')
    .max(200, 'El nombre no puede superar 200 caracteres.'),
  // "901.234.567-8" → "901234567-8": se quitan puntos y espacios antes de validar.
  nit: z
    .string()
    .transform((valor) => valor.replace(/[\s.]/g, '').toUpperCase())
    .pipe(
      z
        .string()
        .min(1, 'El NIT es obligatorio.')
        .regex(
          PATRONES.nit,
          'El NIT debe tener entre 5 y 15 dígitos y, opcionalmente, un dígito de verificación (p. ej. 900123456-7).',
        ),
    ),
  // El backend lo admite vacío (lo propone él), pero la interfaz siempre envía uno: el sugerido o el elegido.
  codigo: z
    .string()
    .trim()
    .toUpperCase()
    .min(1, 'El código empresarial es obligatorio.')
    .regex(PATRONES.codigoEmpresarial, MENSAJE_CODIGO),
  gerente: z.object({
    nombre: z
      .string()
      .trim()
      .min(1, 'El nombre del administrador es obligatorio.')
      .max(150, 'El nombre no puede superar 150 caracteres.'),
    email: z
      .string()
      .trim()
      .toLowerCase()
      .min(1, 'El correo es obligatorio.')
      .max(320, 'El correo es demasiado largo.')
      .regex(PATRONES.email, 'El correo no tiene un formato válido.'),
    username: z
      .string()
      .trim()
      .toLowerCase()
      .min(1, 'El usuario es obligatorio.')
      .regex(
        PATRONES.username,
        'El usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo, y empezar y terminar con letra o número.',
      ),
    // La contraseña no se recorta: los espacios forman parte de ella.
    password: z
      .string()
      .min(1, 'La contraseña es obligatoria.')
      .min(8, 'La contraseña debe tener entre 8 y 72 caracteres.')
      .max(72, 'La contraseña debe tener entre 8 y 72 caracteres.')
      .refine((valor) => encoder.encode(valor).length <= PASSWORD_MAX_BYTES, 'La contraseña no puede superar 72 bytes.'),
  }),
})

export type EmpresaFormValues = z.input<typeof empresaSchema>
/** Cuerpo de POST /api/v1/empresas, ya validado y normalizado. */
export type EmpresaPayload = z.output<typeof empresaSchema>
