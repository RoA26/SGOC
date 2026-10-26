import threading
from decimal import Decimal

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import func, select, text
from sqlalchemy.exc import DBAPIError

from app.core.db import get_sessionmaker
from app.core.deps import Actor
from app.core.errors import DomainError
from app.modules.inventory.models import Stock
from app.modules.movements import service
from app.modules.movements.models import Movement
from app.modules.movements.schemas import MovementIn
from app.modules.users.models import User
from tests.conftest import Org


def mov(client: TestClient, h: dict[str, str], org: Org, kind: str, qty: str, **kw: object):
    body = {"kind": kind, "product_id": org.product, "warehouse_id": org.warehouse, "quantity": qty, **kw}
    headers = dict(h)
    if "key" in kw:
        headers["Idempotency-Key"] = str(body.pop("key"))
    return client.post("/api/movements", headers=headers, json=body)


def stock(org: Org) -> tuple[Decimal, Decimal]:
    with get_sessionmaker()() as db:
        s = db.scalar(select(Stock).where(Stock.product_id == org.product, Stock.warehouse_id == org.warehouse))
        return (s.quantity, s.avg_cost) if s else (Decimal(0), Decimal(0))


def count_movements() -> int:
    with get_sessionmaker()() as db:
        return db.scalar(select(func.count()).select_from(Movement)) or 0


def test_entrada_salida_y_saldo(client: TestClient, org_a: Org) -> None:
    r = mov(client, org_a.worker, org_a, "ENTRADA", "10", unit_cost="100", reference="FAC-1")
    assert r.status_code == 201, r.text
    assert Decimal(r.json()["balance_after"]) == 10
    r = mov(client, org_a.worker, org_a, "SALIDA", "4")
    assert r.status_code == 201
    assert Decimal(r.json()["quantity"]) == -4 and Decimal(r.json()["balance_after"]) == 6
    inv = client.get("/api/inventory", headers=org_a.worker).json()
    assert Decimal(inv[0]["quantity"]) == 6 and Decimal(inv[0]["total_value"]) == Decimal("600.00")


def test_costo_promedio_ponderado(client: TestClient, org_a: Org) -> None:
    mov(client, org_a.admin, org_a, "ENTRADA", "10", unit_cost="100")
    mov(client, org_a.admin, org_a, "ENTRADA", "30", unit_cost="200")
    assert stock(org_a) == (Decimal(40), Decimal("175.0000"))  # (10·100 + 30·200) / 40
    mov(client, org_a.admin, org_a, "SALIDA", "15")
    assert stock(org_a)[1] == Decimal("175.0000")  # las salidas no cambian el costo
    mov(client, org_a.admin, org_a, "ENTRADA", "5")  # entrada sin costo: conserva el promedio
    assert stock(org_a) == (Decimal(30), Decimal("175.0000"))


def test_salida_sin_existencias_no_deja_rastro(client: TestClient, org_a: Org) -> None:
    mov(client, org_a.worker, org_a, "ENTRADA", "60")
    r = mov(client, org_a.worker, org_a, "SALIDA", "61")
    assert r.status_code == 409 and r.json()["code"] == "stock_insuficiente"
    assert r.json()["detail"] == "Existencias insuficientes: disponible 60"
    assert stock(org_a)[0] == 60 and count_movements() == 1


def test_rollback_si_falla_tras_actualizar_el_saldo(org_a: Org, monkeypatch: pytest.MonkeyPatch) -> None:
    """Un error al insertar el movimiento revierte también el cambio de saldo."""
    real = service._apply

    def boom(*a: object, **kw: object) -> Movement:
        real(*a, **kw)  # type: ignore[arg-type]
        raise DomainError(500, "falla_simulada", "falla")

    monkeypatch.setattr(service, "_apply", boom)
    with get_sessionmaker()() as db:
        actor = Actor(db.get(User, org_a.worker_id))  # type: ignore[arg-type]
        with pytest.raises(DomainError):
            service.register(
                db,
                actor,
                MovementIn(kind="ENTRADA", product_id=org_a.product, warehouse_id=org_a.warehouse, quantity=Decimal(7)),
                None,
            )
    assert stock(org_a)[0] == 0 and count_movements() == 0


def test_idempotencia(client: TestClient, org_a: Org) -> None:
    a = mov(client, org_a.worker, org_a, "ENTRADA", "5", key="clave-0001")
    b = mov(client, org_a.worker, org_a, "ENTRADA", "5", key="clave-0001")
    assert a.status_code == 201 and b.status_code == 200 and a.json()["id"] == b.json()["id"]
    assert stock(org_a)[0] == 5
    c = mov(client, org_a.worker, org_a, "ENTRADA", "6", key="clave-0001")
    assert c.status_code == 409 and c.json()["code"] == "clave_reutilizada"


def test_misma_clave_en_otra_organizacion_no_choca(client: TestClient, org_a: Org, org_b: Org) -> None:
    assert mov(client, org_a.worker, org_a, "ENTRADA", "1", key="clave-compartida").status_code == 201
    assert mov(client, org_b.worker, org_b, "ENTRADA", "1", key="clave-compartida").status_code == 201


def _parallel(n: int, fn) -> list[object]:
    barrier = threading.Barrier(n)
    results: list[object] = [None] * n

    def run(i: int) -> None:
        barrier.wait()
        try:
            results[i] = fn(i)
        except Exception as e:  # noqa: BLE001
            results[i] = e

    threads = [threading.Thread(target=run, args=(i,)) for i in range(n)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return results


def _register(org: Org, data: MovementIn, key: str | None = None) -> Movement:
    with get_sessionmaker()() as db:
        actor = Actor(db.get(User, org.worker_id))  # type: ignore[arg-type]
        return service.register(db, actor, data, key)[0]


def test_salidas_concurrentes_no_dejan_saldo_negativo(client: TestClient, org_a: Org) -> None:
    mov(client, org_a.admin, org_a, "ENTRADA", "5")
    out = MovementIn(kind="SALIDA", product_id=org_a.product, warehouse_id=org_a.warehouse, quantity=Decimal(1))
    results = _parallel(12, lambda _: _register(org_a, out))
    ok = [r for r in results if isinstance(r, Movement)]
    failed = [r for r in results if isinstance(r, DomainError)]
    assert len(ok) == 5 and len(failed) == 7, results
    assert all(f.code == "stock_insuficiente" for f in failed)
    assert stock(org_a)[0] == 0
    assert sorted(m.balance_after for m in ok) == [0, 1, 2, 3, 4]


def test_primera_entrada_concurrente_crea_un_solo_saldo(org_a: Org) -> None:
    data = MovementIn(kind="ENTRADA", product_id=org_a.product, warehouse_id=org_a.warehouse, quantity=Decimal(1))
    results = _parallel(8, lambda _: _register(org_a, data))
    assert all(isinstance(r, Movement) for r in results), results
    assert stock(org_a)[0] == 8


def test_reintentos_concurrentes_con_la_misma_clave(org_a: Org) -> None:
    data = MovementIn(kind="ENTRADA", product_id=org_a.product, warehouse_id=org_a.warehouse, quantity=Decimal(2))
    results = _parallel(6, lambda _: _register(org_a, data, "reintento-01"))
    assert all(isinstance(r, Movement) for r in results), results
    assert len({r.id for r in results}) == 1  # type: ignore[union-attr]
    assert stock(org_a)[0] == 2 and count_movements() == 1


def test_reversion_unica_y_auditable(client: TestClient, org_a: Org) -> None:
    mov(client, org_a.admin, org_a, "ENTRADA", "10", unit_cost="100")
    m2 = mov(client, org_a.admin, org_a, "ENTRADA", "10", unit_cost="300").json()
    assert stock(org_a) == (Decimal(20), Decimal("200.0000"))
    r = client.post(f"/api/movements/{m2['id']}/reversal", headers=org_a.admin, json={"notes": "factura errada"})
    assert r.status_code == 201, r.text
    assert r.json()["kind"] == "REVERSION" and r.json()["reverses_id"] == m2["id"]
    assert stock(org_a) == (Decimal(10), Decimal("100.0000"))
    again = client.post(f"/api/movements/{m2['id']}/reversal", headers=org_a.admin, json={"notes": "otra vez"})
    assert again.status_code == 409
    rev = client.post(f"/api/movements/{r.json()['id']}/reversal", headers=org_a.admin, json={"notes": "x" * 5})
    assert rev.status_code == 422
    assert count_movements() == 3


def test_reversion_bloqueada_si_ya_no_hay_existencias(client: TestClient, org_a: Org) -> None:
    m = mov(client, org_a.admin, org_a, "ENTRADA", "3").json()
    mov(client, org_a.admin, org_a, "SALIDA", "2")
    r = client.post(f"/api/movements/{m['id']}/reversal", headers=org_a.admin, json={"notes": "error"})
    assert r.status_code == 409 and stock(org_a)[0] == 1


def test_ajuste_admin_con_justificacion(client: TestClient, org_a: Org) -> None:
    mov(client, org_a.admin, org_a, "ENTRADA", "10")
    r = mov(client, org_a.admin, org_a, "AJUSTE", "-3", notes="Faltante en revisión")
    assert r.status_code == 201 and stock(org_a)[0] == 7
    assert mov(client, org_a.admin, org_a, "AJUSTE", "-8", notes="exceso").status_code == 409


def test_movimientos_son_inmutables_en_bd(client: TestClient, org_a: Org) -> None:
    mov(client, org_a.admin, org_a, "ENTRADA", "1")
    with get_sessionmaker()() as db:
        with pytest.raises(DBAPIError):
            db.execute(text("UPDATE movements SET quantity = 99"))
        db.rollback()
        with pytest.raises(DBAPIError):
            db.execute(text("DELETE FROM movements"))
        db.rollback()
    assert count_movements() == 1


def test_trabajador_solo_ve_sus_movimientos(client: TestClient, org_a: Org) -> None:
    mov(client, org_a.admin, org_a, "ENTRADA", "5")
    mov(client, org_a.worker, org_a, "SALIDA", "1")
    assert len(client.get("/api/movements", headers=org_a.worker).json()) == 1
    assert len(client.get("/api/movements", headers=org_a.admin).json()) == 2


@pytest.mark.parametrize(
    "body",
    [
        {"kind": "ENTRADA", "quantity": "0"},
        {"kind": "ENTRADA", "quantity": "-1"},
        {"kind": "SALIDA", "quantity": "-1"},
        {"kind": "SALIDA", "quantity": "1", "unit_cost": "5"},
        {"kind": "ENTRADA", "quantity": "1.0001"},
        {"kind": "ENTRADA", "quantity": "1", "unit_cost": "-1"},
        {"kind": "AJUSTE", "quantity": "1"},
        {"kind": "TRASLADO", "quantity": "1"},
        {"kind": "ENTRADA", "quantity": "abc"},
    ],
)
def test_validacion_de_entradas(client: TestClient, org_a: Org, body: dict[str, str]) -> None:
    r = client.post(
        "/api/movements",
        headers=org_a.admin,
        json={"product_id": org_a.product, "warehouse_id": org_a.warehouse, **body},
    )
    assert r.status_code == 422 and r.json()["code"] == "validacion"
    assert count_movements() == 0
