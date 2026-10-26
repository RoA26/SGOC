from datetime import UTC, datetime, timedelta

import jwt
from fastapi.testclient import TestClient

from app.core.config import get_settings
from tests.conftest import PASSWORD, Org, login


def test_login_incorrecto_no_revela_si_existe(client: TestClient, org_a: Org) -> None:
    a = client.post("/api/auth/login", json={"email": "admin@alfa.example.com", "password": "mala-clave-xx"})
    b = client.post("/api/auth/login", json={"email": "nadie@alfa.example.com", "password": "mala-clave-xx"})
    assert a.status_code == b.status_code == 401
    assert a.json()["detail"] == b.json()["detail"]
    assert a.headers["content-type"].startswith("application/problem+json")


def test_me_lee_rol_de_la_bd(client: TestClient, org_a: Org) -> None:
    r = client.get("/api/auth/me", headers=org_a.worker)
    assert r.status_code == 200
    assert r.json()["role"] == "TRABAJADOR"
    assert r.json()["organization_name"] == "Org alfa"


def test_sin_token_y_token_manipulado(client: TestClient, org_a: Org) -> None:
    assert client.get("/api/auth/me").status_code == 401
    forged = jwt.encode(
        {"sub": "1", "tv": 0, "iss": "bastion", "exp": datetime.now(UTC) + timedelta(minutes=5)},
        "otro-secreto-" * 4,
        algorithm="HS256",
    )
    assert client.get("/api/auth/me", headers={"Authorization": f"Bearer {forged}"}).status_code == 401


def test_token_expirado(client: TestClient, org_a: Org) -> None:
    exp = jwt.encode(
        {"sub": "2", "tv": 0, "iss": "bastion", "exp": datetime.now(UTC) - timedelta(seconds=1)},
        get_settings().jwt_secret,
        algorithm="HS256",
    )
    assert client.get("/api/auth/me", headers={"Authorization": f"Bearer {exp}"}).status_code == 401


def test_alg_none_rechazado(client: TestClient, org_a: Org) -> None:
    tok = jwt.encode(
        {"sub": "2", "tv": 0, "iss": "bastion", "exp": datetime.now(UTC) + timedelta(minutes=5)}, None, algorithm="none"
    )
    assert client.get("/api/auth/me", headers={"Authorization": f"Bearer {tok}"}).status_code == 401


def test_logout_revoca_el_token(client: TestClient, org_a: Org) -> None:
    assert client.post("/api/auth/logout", headers=org_a.worker).status_code == 204
    assert client.get("/api/auth/me", headers=org_a.worker).status_code == 401
    login(client, "op@alfa.example.com")  # puede volver a entrar


def test_desactivar_trabajador_invalida_su_sesion(client: TestClient, org_a: Org) -> None:
    r = client.patch(f"/api/users/{org_a.worker_id}", headers=org_a.admin, json={"active": False})
    assert r.status_code == 200
    assert client.get("/api/auth/me", headers=org_a.worker).status_code == 401
    r = client.post("/api/auth/login", json={"email": "op@alfa.example.com", "password": PASSWORD})
    assert r.status_code == 401


def test_desactivar_organizacion_bloquea_a_sus_usuarios(
    client: TestClient, superadmin: dict[str, str], org_a: Org, org_b: Org
) -> None:
    r = client.patch(f"/api/organizations/{org_a.id}", headers=superadmin, json={"active": False})
    assert r.status_code == 200
    assert client.get("/api/auth/me", headers=org_a.admin).status_code == 401
    assert (
        client.post("/api/auth/login", json={"email": "admin@alfa.example.com", "password": PASSWORD}).status_code
        == 401
    )
    assert client.get("/api/auth/me", headers=org_b.admin).status_code == 200
