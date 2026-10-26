"""Acceso vertical: cada rol solo hace lo que le corresponde."""

from fastapi.testclient import TestClient

from tests.conftest import PASSWORD, Org


def test_trabajador_no_gestiona_catalogo_ni_usuarios(client: TestClient, org_a: Org) -> None:
    h = org_a.worker
    assert client.post("/api/products", headers=h, json={"sku": "X", "name": "X"}).status_code == 403
    assert client.post("/api/categories", headers=h, json={"name": "X"}).status_code == 403
    assert client.post("/api/warehouses", headers=h, json={"code": "X", "name": "X"}).status_code == 403
    assert client.get("/api/users", headers=h).status_code == 403
    assert (
        client.post(
            "/api/users", headers=h, json={"email": "z@alfa.example.com", "full_name": "Z", "password": PASSWORD}
        ).status_code
        == 403
    )
    # Sí puede consultar
    assert client.get("/api/products", headers=h).status_code == 200
    assert client.get("/api/inventory", headers=h).status_code == 200


def test_trabajador_no_registra_ajustes(client: TestClient, org_a: Org) -> None:
    r = client.post(
        "/api/movements",
        headers=org_a.worker,
        json={
            "kind": "AJUSTE",
            "product_id": org_a.product,
            "warehouse_id": org_a.warehouse,
            "quantity": "5",
            "notes": "conteo",
        },
    )
    assert r.status_code == 403


def test_trabajador_no_revierte(client: TestClient, org_a: Org) -> None:
    r = client.post(
        "/api/movements",
        headers=org_a.worker,
        json={"kind": "ENTRADA", "product_id": org_a.product, "warehouse_id": org_a.warehouse, "quantity": "5"},
    )
    assert r.status_code == 201
    r = client.post(f"/api/movements/{r.json()['id']}/reversal", headers=org_a.worker, json={"notes": "error"})
    assert r.status_code == 403


def test_admin_no_puede_crear_roles_superiores(client: TestClient, org_a: Org) -> None:
    for role in ("SUPER_ADMIN", "ADMINISTRADOR_COMERCIAL"):
        r = client.post(
            "/api/users",
            headers=org_a.admin,
            json={"email": f"{role.lower()}@alfa.example.com", "full_name": "Z", "password": PASSWORD, "role": role},
        )
        assert r.status_code == 422


def test_admin_no_modifica_su_propia_cuenta_ni_otros_admins(client: TestClient, org_a: Org) -> None:
    me = client.get("/api/auth/me", headers=org_a.admin).json()
    r = client.patch(f"/api/users/{me['id']}", headers=org_a.admin, json={"active": False})
    assert r.status_code == 403


def test_admin_no_administra_organizaciones(client: TestClient, org_a: Org) -> None:
    assert client.get("/api/organizations", headers=org_a.admin).status_code == 403
    r = client.patch(f"/api/organizations/{org_a.id}", headers=org_a.admin, json={"active": False})
    assert r.status_code == 403


def test_superadmin_no_opera_datos_de_negocio(client: TestClient, superadmin: dict[str, str], org_a: Org) -> None:
    assert client.get("/api/products", headers=superadmin).status_code == 403
    assert client.get("/api/inventory", headers=superadmin).status_code == 403
    assert client.get("/api/users", headers=superadmin).status_code == 403


def test_respuestas_no_exponen_hash(client: TestClient, superadmin: dict[str, str], org_a: Org) -> None:
    body = client.get("/api/users", headers=org_a.admin).text + client.get("/api/auth/me", headers=org_a.admin).text
    assert "password" not in body and "argon2" not in body
