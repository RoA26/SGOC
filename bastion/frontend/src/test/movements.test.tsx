import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ME_ADMIN, ME_WORKER, mockApi, renderApp } from "./utils";

const PRODUCTS = [{ id: 7, sku: "VLV-1", barcode: null, name: "Válvula", category_id: null, unit: "UND", min_stock: "0", active: true }];
const WAREHOUSES = [{ id: 3, code: "PRINCIPAL", name: "Principal", active: true }];

function server(me: object, onPost?: (body: Record<string, unknown>, headers: Record<string, string>) => { status: number; body: unknown }) {
  return mockApi((url, init) => {
    if (url === "/api/auth/me") return { status: 200, body: me };
    if (url === "/api/products") return { status: 200, body: PRODUCTS };
    if (url === "/api/warehouses") return { status: 200, body: WAREHOUSES };
    if (url.startsWith("/api/movements") && init.method === "POST" && onPost)
      return onPost(JSON.parse(String(init.body)) as Record<string, unknown>, init.headers as Record<string, string>);
    if (url.startsWith("/api/movements")) return { status: 200, body: [] };
    return undefined;
  });
}

describe("movimientos", () => {
  it("el trabajador no ve la opción de ajuste", async () => {
    sessionStorage.setItem("bastion.token", "t");
    server(ME_WORKER);
    renderApp("/app/movimientos");
    expect(await screen.findByRole("radio", { name: /salida/i })).toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: /ajuste/i })).toBeNull();
    expect(screen.getByText("Aún no hay movimientos")).toBeInTheDocument();
  });

  it("valida la cantidad y exige justificación en ajustes", async () => {
    sessionStorage.setItem("bastion.token", "t");
    const calls = server(ME_ADMIN);
    renderApp("/app/movimientos");
    await userEvent.click(await screen.findByRole("radio", { name: /ajuste/i }));
    await userEvent.selectOptions(screen.getByLabelText("Producto"), "7");
    await userEvent.type(screen.getByLabelText("Cantidad"), "1.23456");
    await userEvent.click(screen.getByRole("button", { name: /registrar ajuste/i }));
    expect(screen.getByText(/hasta 3 decimales/)).toBeInTheDocument();
    expect(screen.getByText("Un ajuste requiere justificación")).toBeInTheDocument();
    expect(calls.filter((c) => c.init.method === "POST")).toHaveLength(0);
  });

  it("envía clave de idempotencia y muestra el saldo resultante", async () => {
    sessionStorage.setItem("bastion.token", "t");
    let seen: { body: Record<string, unknown>; key: string | undefined } | null = null;
    server(ME_WORKER, (body, headers) => {
      seen = { body, key: headers["Idempotency-Key"] };
      return { status: 201, body: { id: 1, kind: "SALIDA", quantity: "-2", balance_after: "8" } };
    });
    renderApp("/app/movimientos?tipo=SALIDA");
    await userEvent.selectOptions(await screen.findByLabelText("Producto"), "7");
    await userEvent.type(screen.getByLabelText("Cantidad"), "2");
    await userEvent.click(screen.getByRole("button", { name: /registrar salida/i }));
    expect(await screen.findByText(/saldo resultante: 8/i)).toBeInTheDocument();
    expect(seen!.body).toMatchObject({ kind: "SALIDA", product_id: 7, warehouse_id: 3, quantity: "2" });
    expect(seen!.key).toMatch(/.{8,}/);
  });

  it("muestra el conflicto de existencias del servidor", async () => {
    sessionStorage.setItem("bastion.token", "t");
    server(ME_WORKER, () => ({ status: 409, body: { code: "stock_insuficiente", detail: "Existencias insuficientes: disponible 1" } }));
    renderApp("/app/movimientos?tipo=SALIDA");
    await userEvent.selectOptions(await screen.findByLabelText("Producto"), "7");
    await userEvent.type(screen.getByLabelText("Cantidad"), "5");
    await userEvent.click(screen.getByRole("button", { name: /registrar salida/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Existencias insuficientes");
  });
});
