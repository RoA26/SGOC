/**
 * Expresiones regulares de validación. Copia exacta de
 * backend-spring/.../model/dto/Patrones.java: si cambias una, cambia la otra.
 */
export const PATRONES = {
  /** 5 a 15 dígitos y, opcionalmente, dígito de verificación: 900123456-7. */
  nit: /^\d{5,15}(-[\dK])?$/,
  /** 7 a 20 caracteres: dígitos, espacios, +, - y paréntesis. */
  telefono: /^[+(\d][\d ()+-]{6,19}$/,
  /** 3 a 40 caracteres: letras, números, punto, guion y guion bajo (en mayúsculas). */
  sku: /^[A-Z0-9._-]{3,40}$/,
  /** Exige dominio con punto. */
  email: /^[^@\s]+@[^@\s]+\.[^@\s]+$/,
} as const
