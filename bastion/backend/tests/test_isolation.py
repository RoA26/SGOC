"""Acceso horizontal: una organización no ve ni modifica recursos de otra."""

import pytest
from fastapi.testclient import TestClient
from sqlalchemy.exc import IntegrityError

from app.core.db import get_sessionmaker
from app.modules.movements.models import Movement
from tests.conftest import Org


def test_mismo_sku_en_dos_organizaciones(client: TestClient, org_a: Org, org_b: Org) -> None:
    # Ambas fixtures crearon "SKU-1" sin colisión; repetirlo dentro de la misma sí colisiona.
    r = client.post("/api/products", headers=org_a.admin, json={"sku": "SKU-1", "name": "Otro"})
    assert r.status_code == 409


def test_no_lee_productos_ajenos(client: TestClient, org_a: Org, org_b: Org) -> None:
    assert client.get(f"/api/products/{org_b.product}", headers=org_a.admin).status_code == 404
    ids = {p["id"] for p in client.get("/api/products", headers=org_a.admin).json()}
    assert org_b.product not in ids


def test_no_modifica_productos_ajenos(client: TestClient, org_a: Org, org_b: Org) -> None:
    r = client.patch(f"/api/products/{org_b.product}", headers=org_a.admin, json={"name": "hackeado"})
    assert r.status_code == 404


def test_no_usa_categoria_ajena(client: TestClient, org_a: Org, org_b: Org) -> None:
    cat = client.post("/api/categories", headers=org_b.admin, json={"name": "Beta"}).json()["id"]
    r = client.post("/api/products", headers=org_a.admin, json={"sku": "N", "name": "N", "category_id": cat})
    assert r.status_code == 422


def test_no_mueve_inventario_ajeno(client: TestClient, org_a: Org, org_b: Org) -> None:
    base = {"kind": "ENTRADA", "quantity": "1"}
    r = client.post(
        "/api/movements",
        headers=org_a.worker,
        json={**base, "product_id": org_b.product, "warehouse_id": org_a.warehouse},
    )
    assert r.status_code == 404
    r = client.post(
        "/api/movements",
        headers=org_a.worker,
        json={**base, "product_id": org_a.product, "warehouse_id": org_b.warehouse},
    )
    assert r.status_code == 404


def test_no_revierte_movimiento_ajeno(client: TestClient, org_a: Org, org_b: Org) -> None:
    m = client.post(
        "/api/movements",
        headers=org_b.admin,
        json={"kind": "ENTRADA", "product_id": org_b.product, "warehouse_id": org_b.warehouse, "quantity": "3"},
    ).json()
    r = client.post(f"/api/movements/{m['id']}/reversal", headers=org_a.admin, json={"notes": "intento"})
    assert r.status_code == 404
    assert client.get("/api/movements", headers=org_a.admin).json() == []


def test_no_gestiona_usuarios_ajenos(client: TestClient, org_a: Org, org_b: Org) -> None:
    r = client.patch(f"/api/users/{org_b.worker_id}", headers=org_a.admin, json={"active": False})
    assert r.status_code == 404


def test_la_bd_impide_mezclar_organizaciones(org_a: Org, org_b: Org) -> None:
    """Aunque un servicio fallara, las FK compuestas rechazan un movimiento con producto de otra empresa."""
    with get_sessionmaker()() as db:
        db.add(
            Movement(
                organization_id=org_a.id,
                product_id=org_b.product,
                warehouse_id=org_a.warehouse,
                user_id=org_a.worker_id,
                kind="ENTRADA",
                quantity=1,
                balance_after=1,
                avg_cost_after=0,
            )
        )
        with pytest.raises(IntegrityError):
            db.flush()
