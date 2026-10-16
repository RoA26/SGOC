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
  /** Código de barras: de 3 a 64 caracteres sin espacios (conserva ceros a la izquierda). */
  codigoBarras: /^[0-9A-Za-z._/+-]{3,64}$/,
  /** Código empresarial (p. ej. NASC01): de 3 a 12 letras mayúsculas o números, empezando por letra. */
  codigoEmpresarial: /^[A-Z][A-Z0-9]{2,11}$/,
  /** Exige dominio con punto. */
  email: /^[^@\s]+@[^@\s]+\.[^@\s]+$/,
  /** 3 a 50 caracteres en minúsculas: letras, números, punto, guion y guion bajo; empieza y termina con letra o número. */
  username: /^[a-z0-9](?:[a-z0-9._-]{1,48})[a-z0-9]$/,
} as const
