import { z } from 'zod'
import { PATRONES } from './patrones'

const PRECIO_MAXIMO = 999_999_999_999.99

/** Equivale a @Digits(fraction = 2) evitando errores de coma flotante (0.1 + 0.2…). */
function tieneHastaDosDecimales(valor: number): boolean {
  const centavos = valor * 100
  return Math.abs(centavos - Math.round(centavos)) < 1e-6
}

/** Validación del formulario de producto: replica ProductoRequestDTO. */
export const productoSchema = z.object({
  sku: z
    .string()
    .trim()
    .toUpperCase()
    .min(1, 'El SKU es obligatorio.')
    .regex(PATRONES.sku, 'El SKU admite de 3 a 40 letras, números, puntos, guiones o guiones bajos.'),
  nombre: z
    .string()
    .trim()
    .min(1, 'El nombre es obligatorio.')
    .max(150, 'El nombre no puede superar 150 caracteres.'),
  descripcion: z
    .string()
    .trim()
    .max(1000, 'La descripción no puede superar 1000 caracteres.')
    .transform((valor) => valor || null),
  // Los <input type="number"> se registran con valueAsNumber: un campo vacío llega como NaN.
  precio: z
    .number({ error: 'El precio es obligatorio.' })
    .positive('El precio debe ser mayor que 0.')
    .max(PRECIO_MAXIMO, 'El precio admite hasta 12 enteros y 2 decimales.')
    .refine(tieneHastaDosDecimales, 'El precio admite hasta 12 enteros y 2 decimales.'),
  proveedorId: z
    .number({ error: 'El proveedor es obligatorio.' })
    .int()
    .positive('El proveedor es obligatorio.'),
})

export type ProductoFormValues = z.input<typeof productoSchema>
export type ProductoPayload = z.output<typeof productoSchema>
