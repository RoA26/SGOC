const qty = new Intl.NumberFormat("es-CO", { maximumFractionDigits: 3 });
const money = new Intl.NumberFormat("es-CO", { style: "currency", currency: "COP", maximumFractionDigits: 0 });
const date = new Intl.DateTimeFormat("es-CO", { dateStyle: "medium", timeStyle: "short" });

export const fmtQty = (v: string | number) => qty.format(Number(v));
export const fmtMoney = (v: string | number) => money.format(Number(v));
export const fmtDate = (iso: string) => date.format(new Date(iso));

/** Clave de idempotencia por envío de formulario: un doble clic o un reintento no duplica el movimiento. */
export const newIdempotencyKey = () =>
  typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
