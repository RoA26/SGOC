import { z } from 'zod'

/** Mismos límites que SolicitudRequestDTO y DetalleSolicitudDTO en el backend. */
export const MAX_LINEAS = 50
const CANTIDAD_MAXIMA = 999_999

// Los selects e inputs numéricos entregan NaN cuando están vacíos.
const lineaSchema = z.object({
  productoId: z.number({ error: 'Selecciona un producto.' }).int().positive('Selecciona un producto.'),
  cantidad: z
    .number({ error: 'La cantidad es obligatoria.' })
    .int('La cantidad debe ser un número entero.')
    .positive('La cantidad debe ser mayor que 0.')
    .max(CANTIDAD_MAXIMA, 'La cantidad máxima por línea es 999.999.'),
})

/** Solicitud interna: cabecera (justificación) y detalle (líneas de producto y cantidad). */
export const solicitudSchema = z.object({
  justificacion: z
    .string()
    .trim()
    .min(1, 'La justificación es obligatoria.')
    .min(10, 'La justificación debe tener entre 10 y 1000 caracteres.')
    .max(1000, 'La justificación debe tener entre 10 y 1000 caracteres.'),
  detalles: z
    .array(lineaSchema)
    .min(1, 'Agrega al menos un producto.')
    .max(MAX_LINEAS, `La solicitud admite hasta ${MAX_LINEAS} productos.`)
    // Un producto, una línea: el error se marca en la línea repetida, como hace el backend.
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

export type SolicitudFormValues = z.input<typeof solicitudSchema>
export type SolicitudPayload = z.output<typeof solicitudSchema>
