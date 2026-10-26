import { z } from "zod";

// Refleja MovementIn del backend: 3 decimales en cantidad, 4 en costo.
const decimal = (places: number) => new RegExp(`^\\d{1,11}(\\.\\d{1,${places}})?$`);

export const movementSchema = z
  .object({
    kind: z.enum(["ENTRADA", "SALIDA", "AJUSTE"]),
    product_id: z.coerce.number({ invalid_type_error: "Seleccione un producto" }).int().positive("Seleccione un producto"),
    warehouse_id: z.coerce.number({ invalid_type_error: "Seleccione una bodega" }).int().positive("Seleccione una bodega"),
    quantity: z.string().trim().min(1, "Ingrese la cantidad"),
    unit_cost: z.string().trim().optional(),
    reference: z.string().trim().max(120, "Máximo 120 caracteres").optional(),
    notes: z.string().trim().max(500, "Máximo 500 caracteres").optional(),
  })
  .superRefine((v, ctx) => {
    const raw = v.kind === "AJUSTE" ? v.quantity.replace(/^-/, "") : v.quantity;
    if (!decimal(3).test(raw)) ctx.addIssue({ path: ["quantity"], code: "custom", message: "Cantidad no válida (hasta 3 decimales)" });
    else if (Number(v.quantity) === 0) ctx.addIssue({ path: ["quantity"], code: "custom", message: "La cantidad no puede ser cero" });
    if (v.unit_cost && !decimal(4).test(v.unit_cost)) ctx.addIssue({ path: ["unit_cost"], code: "custom", message: "Costo no válido" });
    if (v.kind === "AJUSTE" && !v.notes) ctx.addIssue({ path: ["notes"], code: "custom", message: "Un ajuste requiere justificación" });
  });

export type MovementForm = z.infer<typeof movementSchema>;

export function toPayload(v: MovementForm) {
  return {
    kind: v.kind,
    product_id: v.product_id,
    warehouse_id: v.warehouse_id,
    quantity: v.quantity,
    ...(v.kind === "ENTRADA" && v.unit_cost ? { unit_cost: v.unit_cost } : {}),
    ...(v.reference ? { reference: v.reference } : {}),
    ...(v.notes ? { notes: v.notes } : {}),
  };
}
