import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { mockApi, renderApp } from "./utils";

describe("página de bienvenida", () => {
  it("es pública y lleva a /login", () => {
    const calls = mockApi(() => undefined);
    renderApp("/");
    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent("Cada existencia");
    const ctas = screen.getAllByRole("link", { name: /iniciar sesión|ingresar a bastión/i });
    expect(ctas.length).toBeGreaterThan(0);
    for (const a of ctas) expect(a).toHaveAttribute("href", "/login");
    expect(calls).toHaveLength(0); // no requiere sesión ni llamadas a la API
  });

  it("incluye las secciones obligatorias y marca lo que aún no existe", () => {
    mockApi(() => undefined);
    renderApp("/");
    for (const id of ["producto", "flujo", "seguridad"]) expect(document.getElementById(id)).not.toBeNull();
    const conteos = screen.getByRole("heading", { name: /conteos físicos/i });
    expect(within(conteos).getByText("Próxima fase")).toBeInTheDocument();
    expect(screen.getByRole("contentinfo")).toBeInTheDocument();
  });
});
