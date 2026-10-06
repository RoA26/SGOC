import { z } from 'zod'
import { PATRONES } from './patrones'

/**
 * Validación del formulario de proveedor: replica ProveedorRequestDTO (Jakarta Validation)
 * con los mismos mensajes, y normaliza igual que el backend antes de enviar.
 */
export const proveedorSchema = z.object({
  // "900.123.456-7" → "900123456-7": se quitan puntos y espacios antes de validar.
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
  razonSocial: z
    .string()
    .trim()
    .min(1, 'La razón social es obligatoria.')
    .max(200, 'La razón social no puede superar 200 caracteres.'),
  email: z
    .string()
    .trim()
    .toLowerCase()
    .min(1, 'El correo es obligatorio.')
    .max(320, 'El correo es demasiado largo.')
    .regex(PATRONES.email, 'El correo no tiene un formato válido.'),
  telefono: z
    .string()
    .trim()
    .refine(
      (valor) => valor === '' || PATRONES.telefono.test(valor),
      'El teléfono admite de 7 a 20 caracteres: dígitos, espacios, +, - y paréntesis.',
    )
    .transform((valor) => valor || null),
  direccion: z
    .string()
    .trim()
    .max(300, 'La dirección no puede superar 300 caracteres.')
    .transform((valor) => valor || null),
})

/** Valores tal como los edita el formulario (todo texto). */
export type ProveedorFormValues = z.input<typeof proveedorSchema>
/** Cuerpo que se envía a la API, ya validado y normalizado. */
export type ProveedorPayload = z.output<typeof proveedorSchema>
