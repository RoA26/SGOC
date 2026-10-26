import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";
import { App } from "@/App";
import { AuthProvider } from "@/lib/auth";

type Handler = (url: string, init: RequestInit) => { status: number; body?: unknown } | undefined;

/** Sustituye fetch por un servidor simulado. Las rutas no previstas responden 404. */
export function mockApi(handler: Handler) {
  const calls: { url: string; init: RequestInit }[] = [];
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit = {}) => {
    calls.push({ url, init });
    const r = handler(url, init) ?? { status: 404, body: { code: "no_encontrado", detail: "no" } };
    return new Response(r.status === 204 ? null : JSON.stringify(r.body ?? {}), {
      status: r.status,
      headers: { "Content-Type": "application/json" },
    });
  }));
  return calls;
}

export function renderApp(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]} future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

export const ME_WORKER = { id: 2, email: "op@alfa.example.com", full_name: "Ana Operaria", role: "TRABAJADOR", organization_id: 1, organization_name: "Alfa" };
export const ME_ADMIN = { ...ME_WORKER, id: 1, full_name: "Admin Alfa", role: "ADMINISTRADOR_COMERCIAL" };
