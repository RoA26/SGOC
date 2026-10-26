import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { ME_ADMIN, ME_WORKER, mockApi, renderApp } from "./utils";

describe("rutas protegidas", () => {
  it("redirige a /login sin sesión", async () => {
    mockApi(() => undefined);
    renderApp("/app/inventario");
    expect(await screen.findByRole("heading", { name: "Iniciar sesión" })).toBeInTheDocument();
  });

  it("muestra acceso restringido si el rol no corresponde", async () => {
    sessionStorage.setItem("bastion.token", "t");
    mockApi((url) => (url === "/api/auth/me" ? { status: 200, body: ME_WORKER } : { status: 200, body: [] }));
    renderApp("/app/usuarios");
    expect(await screen.findByText("Acceso restringido")).toBeInTheDocument();
  });

  it("el menú solo muestra secciones permitidas", async () => {
    sessionStorage.setItem("bastion.token", "t");
    mockApi((url) => (url === "/api/auth/me" ? { status: 200, body: ME_WORKER } : { status: 200, body: [] }));
    renderApp("/app");
    const nav = (await screen.findAllByRole("navigation", { name: "Secciones" }))[0]!;
    expect(nav).toHaveTextContent("Movimientos");
    expect(nav).not.toHaveTextContent("Usuarios");
    expect(nav).not.toHaveTextContent("Organizaciones");
  });

  it("un 401 del backend cierra la sesión local", async () => {
    sessionStorage.setItem("bastion.token", "t");
    let revoked = false;
    mockApi((url) => {
      if (url === "/api/auth/me") return revoked ? { status: 401, body: { code: "no_autenticado" } } : { status: 200, body: ME_ADMIN };
      revoked = true;
      return { status: 401, body: { code: "no_autenticado", detail: "Sesión inválida o expirada" } };
    });
    renderApp("/app/inventario");
    expect(await screen.findByText(/su sesión expiró/i)).toBeInTheDocument();
    expect(sessionStorage.getItem("bastion.token")).toBeNull();
  });
});

describe("inicio de sesión", () => {
  it("valida campos antes de enviar", async () => {
    const calls = mockApi(() => undefined);
    renderApp("/login");
    await userEvent.click(screen.getByRole("button", { name: "Ingresar" }));
    expect(screen.getByText("Ingrese su correo")).toBeInTheDocument();
    expect(screen.getByText("Ingrese su contraseña")).toBeInTheDocument();
    expect(calls).toHaveLength(0);
  });

  it("muestra el error del servidor", async () => {
    mockApi((url) => (url === "/api/auth/login" ? { status: 401, body: { code: "credenciales_invalidas", detail: "Correo o contraseña incorrectos" } } : undefined));
    renderApp("/login");
    await userEvent.type(screen.getByLabelText("Correo electrónico"), "a@b.co");
    await userEvent.type(screen.getByLabelText("Contraseña"), "x");
    await userEvent.click(screen.getByRole("button", { name: "Ingresar" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Correo o contraseña incorrectos");
  });

  it("entra al panel tras iniciar sesión", async () => {
    mockApi((url) => {
      if (url === "/api/auth/login") return { status: 200, body: { access_token: "tok", expires_in: 1800 } };
      if (url === "/api/auth/me") return { status: 200, body: ME_ADMIN };
      return { status: 200, body: [] };
    });
    renderApp("/login");
    await userEvent.type(screen.getByLabelText("Correo electrónico"), "admin@alfa.example.com");
    await userEvent.type(screen.getByLabelText("Contraseña"), "secreta-123");
    await userEvent.click(screen.getByRole("button", { name: "Ingresar" }));
    expect(await screen.findByRole("heading", { name: /hola, admin/i })).toBeInTheDocument();
    await waitFor(() => expect(sessionStorage.getItem("bastion.token")).toBe("tok"));
  });
});

describe("cierre de sesión", () => {
  it("lleva a /login sin recordar la página del usuario anterior", async () => {
    sessionStorage.setItem("bastion.token", "t");
    mockApi((url) => {
      if (url === "/api/auth/me") return { status: 200, body: ME_ADMIN };
      if (url === "/api/auth/logout") return { status: 204 };
      return { status: 200, body: [] };
    });
    renderApp("/app/usuarios");
    await userEvent.click((await screen.findAllByRole("button", { name: /cerrar sesión/i }))[0]!);
    expect(await screen.findByRole("heading", { name: "Iniciar sesión" })).toBeInTheDocument();
    expect(sessionStorage.getItem("bastion.token")).toBeNull();
  });
});
