/**
 * Clave de idempotencia para una operación que mueve inventario (recepción, ajuste). Se genera
 * una vez por intento de registro y se reutiliza en los reintentos: si la primera petición llegó
 * al servidor aunque la respuesta se perdiera, el backend devuelve lo ya registrado en lugar de
 * duplicar la mercancía.
 */
export function nuevaClaveIdempotencia(): string {
  return crypto.randomUUID()
}
