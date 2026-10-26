"""Las pruebas usan un PostgreSQL real (bloqueos de fila, índices parciales, triggers).

Defina BASTION_TEST_DATABASE_URL apuntando a una base desechable: se borra y recrea el esquema.
"""

import os
import secrets
from collections.abc import Iterator

import pytest

URL = os.environ.get("BASTION_TEST_DATABASE_URL")
if not URL:
    pytest.exit("Defina BASTION_TEST_DATABASE_URL (base de datos PostgreSQL desechable)", returncode=2)
os.environ["BASTION_DATABASE_URL"] = URL
os.environ.setdefault("BASTION_JWT_SECRET", secrets.token_urlsafe(48))
os.environ["BASTION_ENV"] = "test"

from alembic.config import Config  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402
from sqlalchemy import text  # noqa: E402

from alembic import command  # noqa: E402
from app.core.db import get_engine, get_sessionmaker  # noqa: E402
from app.core.roles import Rol  # noqa: E402
from app.main import app  # noqa: E402
from app.modules.users.service import new_user  # noqa: E402

PASSWORD = "Clave-segura-123"
TABLES = "movements, stock, products, categories, warehouses, users, organizations"


@pytest.fixture(scope="session", autouse=True)
def _schema() -> Iterator[None]:
    with get_engine().begin() as c:
        c.execute(text("DROP SCHEMA public CASCADE; CREATE SCHEMA public"))
    command.upgrade(Config("alembic.ini"), "head")
    yield
    get_engine().dispose()


@pytest.fixture(autouse=True)
def _clean() -> Iterator[None]:
    yield
    with get_engine().begin() as c:
        # TRUNCATE no dispara el trigger de filas que protege los movimientos.
        c.execute(text(f"TRUNCATE {TABLES} RESTART IDENTITY CASCADE"))


@pytest.fixture
def client() -> TestClient:
    return TestClient(app)


def login(client: TestClient, email: str, password: str = PASSWORD) -> dict[str, str]:
    r = client.post("/api/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200, r.text
    return {"Authorization": f"Bearer {r.json()['access_token']}"}


@pytest.fixture
def superadmin(client: TestClient) -> dict[str, str]:
    with get_sessionmaker()() as db:
        new_user(
            db, email="root@bastion.example.com", full_name="Raíz", password=PASSWORD, role=Rol.SUPER_ADMIN, org_id=None
        )
        db.commit()
    return login(client, "root@bastion.example.com")


class Org:
    def __init__(self, client: TestClient, sa: dict[str, str], slug: str) -> None:
        r = client.post(
            "/api/organizations",
            headers=sa,
            json={
                "name": f"Org {slug}",
                "slug": slug,
                "admin": {"email": f"admin@{slug}.example.com", "full_name": "Admin", "password": PASSWORD},
            },
        )
        assert r.status_code == 201, r.text
        self.id = r.json()["id"]
        self.admin = login(client, f"admin@{slug}.example.com")
        r = client.post(
            "/api/users",
            headers=self.admin,
            json={"email": f"op@{slug}.example.com", "full_name": "Operario", "password": PASSWORD},
        )
        assert r.status_code == 201, r.text
        self.worker_id = r.json()["id"]
        self.worker = login(client, f"op@{slug}.example.com")
        r = client.post("/api/products", headers=self.admin, json={"sku": "SKU-1", "name": "Tornillo"})
        assert r.status_code == 201, r.text
        self.product = r.json()["id"]
        r = client.post("/api/warehouses", headers=self.admin, json={"code": "PRINCIPAL", "name": "Bodega principal"})
        assert r.status_code == 201, r.text
        self.warehouse = r.json()["id"]


@pytest.fixture
def org_a(client: TestClient, superadmin: dict[str, str]) -> Org:
    return Org(client, superadmin, "alfa")


@pytest.fixture
def org_b(client: TestClient, superadmin: dict[str, str]) -> Org:
    return Org(client, superadmin, "beta")
