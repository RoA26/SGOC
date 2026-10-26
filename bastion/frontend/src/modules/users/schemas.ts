import { z } from "zod";

// Refleja la política del backend: mínimo 10 caracteres.
export const password = z.string().min(10, "Mínimo 10 caracteres").max(128);
export const personSchema = z.object({
  full_name: z.string().trim().min(2, "Ingrese el nombre completo").max(160),
  email: z.string().trim().email("Correo no válido"),
  password,
});
