/** Cliente HTTP único. Traduce las respuestas Problem Details (RFC 9457) del backend a ApiError. */

const TOKEN_KEY = "bastion.token";

export type FieldError = { campo: string; mensaje: string };

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fields: FieldError[] = [],
  ) {
    super(message);
  }
}

export const tokenStore = {
  get: (): string | null => {
    try {
      return sessionStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set: (t: string) => {
    try {
      sessionStorage.setItem(TOKEN_KEY, t);
    } catch {
      /* sin almacenamiento: la sesión dura lo que la pestaña en memoria */
    }
  },
  clear: () => {
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      /* ignorado */
    }
  },
};

/** Se dispara cuando el backend responde 401: la sesión dejó de ser válida. */
export const SESSION_EXPIRED = "bastion:session-expired";

type Options = { method?: string; body?: unknown; headers?: Record<string, string>; signal?: AbortSignal };

export async function api<T>(path: string, opts: Options = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json", ...opts.headers };
  const token = tokenStore.get();
  if (token) headers.Authorization = `Bearer ${token}`;
  if (opts.body !== undefined) headers["Content-Type"] = "application/json";

  let res: Response;
  try {
    res = await fetch(`/api${path}`, {
      method: opts.method ?? "GET",
      headers,
      body: opts.body === undefined ? null : JSON.stringify(opts.body),
      signal: opts.signal ?? null,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "sin_conexion", "No se pudo contactar con el servidor. Revise su conexión.");
  }

  if (res.status === 204) return undefined as T;
  const data: unknown = await res.json().catch(() => null);
  if (!res.ok) {
    const p = (data ?? {}) as { code?: string; detail?: string; errores?: FieldError[] };
    if (res.status === 401 && token) {
      tokenStore.clear();
      window.dispatchEvent(new Event(SESSION_EXPIRED));
    }
    throw new ApiError(res.status, p.code ?? "error", p.detail ?? `Error ${res.status}`, p.errores ?? []);
  }
  return data as T;
}

export function errorMessage(e: unknown): string {
  if (e instanceof ApiError) return e.message;
  return "Ocurrió un error inesperado.";
}
