import { z } from 'zod'

/** Mismos límites que OrdenCompraRequestDTO y LineaOrdenCompraDTO en el backend. */
export const MAX_LINEAS_ORDEN = 100
const CANTIDAD_MAXIMA = 999_999
const COSTO_MAXIMO = 999_999_999_999

// Los selects e inputs numéricos entregan NaN cuando están vacíos.
const lineaSchema = z.object({
  productoId: z.number({ error: 'Selecciona un producto.' }).int().positive('Selecciona un producto.'),
  cantidad: z
    .number({ error: 'La cantidad es obligatoria.' })
    .int('La cantidad debe ser un número entero.')
    .positive('La cantidad debe ser mayor que 0.')
    .max(CANTIDAD_MAXIMA, 'La cantidad máxima por línea es 999.999.'),
  // Costo unitario pactado con el proveedor, en COP enteros (como los precios del catálogo).
  costoUnitario: z
    .number({ error: 'Indica el costo unitario.' })
    .int('El costo debe ser un número entero.')
    .positive('El costo debe ser mayor que 0.')
    .max(COSTO_MAXIMO, 'El costo admite hasta 12 dígitos.'),
})

/** Orden de compra en borrador: proveedor, observaciones y líneas (un producto por línea). */
export const ordenCompraSchema = z.object({
  proveedorId: z.number({ error: 'Selecciona el proveedor.' }).int().positive('Selecciona el proveedor.'),
  observaciones: z
    .string()
    .trim()
    .max(1000, 'Las observaciones no pueden superar 1000 caracteres.')
    .transform((valor) => valor || null),
  detalles: z
    .array(lineaSchema)
    .min(1, 'Añade al menos un producto.')
    .max(MAX_LINEAS_ORDEN, `La orden admite hasta ${MAX_LINEAS_ORDEN} líneas.`)
    .superRefine((lineas, ctx) => {
      const vistos = new Set<number>()
      lineas.forEach(({ productoId }, indice) => {
        if (!Number.isFinite(productoId)) return
        if (vistos.has(productoId)) {
          ctx.addIssue({
            code: 'custom',
            path: [indice, 'productoId'],
            message: 'Este producto ya está en otra línea: ajusta allí la cantidad.',
          })
        }
        vistos.add(productoId)
      })
    }),
})

export type OrdenCompraFormValues = z.input<typeof ordenCompraSchema>
export type OrdenCompraFormPayload = z.output<typeof ordenCompraSchema>
