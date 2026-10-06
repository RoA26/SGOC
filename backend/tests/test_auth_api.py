import uuid
from datetime import timedelta

import pytest
from httpx import AsyncClient

from app.core.config import settings
from app.core.security import create_access_token, decode_access_token
from app.models import User
from tests.conftest import USER_PASSWORD

pytestmark = pytest.mark.anyio

LOGIN_URL = f"{settings.API_PREFIX}/auth/login"
ME_URL = f"{settings.API_PREFIX}/auth/me"


def _auth_header(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


# --------------------------------------------------------------------------- #
# POST /auth/login
# --------------------------------------------------------------------------- #
async def test_login_returns_valid_token(client: AsyncClient, user: User) -> None:
    response = await client.post(LOGIN_URL, json={"email": user.email, "password": USER_PASSWORD})

    assert response.status_code == 200
    body = response.json()
    assert body["token_type"] == "bearer"
    assert body["expires_in"] == settings.ACCESS_TOKEN_EXPIRE_MINUTES * 60
    assert decode_access_token(body["access_token"]).sub == str(user.id)


async def test_login_email_is_case_insensitive(client: AsyncClient, user: User) -> None:
    response = await client.post(
        LOGIN_URL, json={"email": "ANA.COMPRAS@example.COM", "password": USER_PASSWORD}
    )

    assert response.status_code == 200


async def test_login_wrong_password(client: AsyncClient, user: User) -> None:
    response = await client.post(LOGIN_URL, json={"email": user.email, "password": "incorrecta"})

    assert response.status_code == 401
    assert response.json() == {"detail": "Correo o contraseña incorrectos."}
    assert response.headers["www-authenticate"] == "Bearer"


async def test_login_unknown_email_is_indistinguishable(client: AsyncClient, user: User) -> None:
    response = await client.post(
        LOGIN_URL, json={"email": "nadie@example.com", "password": USER_PASSWORD}
    )

    assert response.status_code == 401
    assert response.json() == {"detail": "Correo o contraseña incorrectos."}


async def test_login_inactive_user(client: AsyncClient, inactive_user: User) -> None:
    response = await client.post(
        LOGIN_URL, json={"email": inactive_user.email, "password": USER_PASSWORD}
    )

    assert response.status_code == 403


async def test_login_inactive_user_with_wrong_password_does_not_leak_status(
    client: AsyncClient, inactive_user: User
) -> None:
    response = await client.post(
        LOGIN_URL, json={"email": inactive_user.email, "password": "incorrecta"}
    )

    assert response.status_code == 401


@pytest.mark.parametrize(
    "payload",
    [
        {"email": "no-es-un-correo", "password": "x"},
        {"email": "ana@example.com", "password": ""},
        {"email": "ana@example.com"},
        {},
    ],
)
async def test_login_validates_payload(client: AsyncClient, payload: dict[str, str]) -> None:
    response = await client.post(LOGIN_URL, json=payload)

    assert response.status_code == 422


# --------------------------------------------------------------------------- #
# GET /auth/me (ruta protegida)
# --------------------------------------------------------------------------- #
async def test_me_returns_current_user(client: AsyncClient, user: User) -> None:
    login = await client.post(LOGIN_URL, json={"email": user.email, "password": USER_PASSWORD})
    token = login.json()["access_token"]

    response = await client.get(ME_URL, headers=_auth_header(token))

    assert response.status_code == 200
    body = response.json()
    assert body["id"] == str(user.id)
    assert body["email"] == "ana.compras@example.com"
    assert body["full_name"] == "Ana Compras"
    assert "hashed_password" not in body


async def test_me_without_token(client: AsyncClient) -> None:
    response = await client.get(ME_URL)

    assert response.status_code == 401
    assert response.headers["www-authenticate"] == "Bearer"


async def test_me_with_non_bearer_scheme(client: AsyncClient) -> None:
    response = await client.get(ME_URL, headers={"Authorization": "Basic dXNlcjpwYXNz"})

    assert response.status_code == 401


async def test_me_with_garbage_token(client: AsyncClient) -> None:
    response = await client.get(ME_URL, headers=_auth_header("esto.no.es-un-jwt"))

    assert response.status_code == 401


async def test_me_with_expired_token(client: AsyncClient, user: User) -> None:
    token = create_access_token(user.id, expires_delta=timedelta(seconds=-1))

    response = await client.get(ME_URL, headers=_auth_header(token))

    assert response.status_code == 401
    assert response.json() == {"detail": "El token ha expirado."}


async def test_me_with_token_for_deleted_user(client: AsyncClient) -> None:
    response = await client.get(ME_URL, headers=_auth_header(create_access_token(uuid.uuid4())))

    assert response.status_code == 401


async def test_me_with_non_uuid_subject(client: AsyncClient) -> None:
    response = await client.get(ME_URL, headers=_auth_header(create_access_token("admin")))

    assert response.status_code == 401


async def test_me_for_user_deactivated_after_login(
    client: AsyncClient, inactive_user: User
) -> None:
    response = await client.get(ME_URL, headers=_auth_header(create_access_token(inactive_user.id)))

    assert response.status_code == 403


# --------------------------------------------------------------------------- #
# Infraestructura
# --------------------------------------------------------------------------- #
async def test_health_check(client: AsyncClient) -> None:
    response = await client.get(f"{settings.API_PREFIX}/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "database": "ok"}


async def test_cors_allows_frontend_origin(client: AsyncClient) -> None:
    response = await client.options(
        LOGIN_URL,
        headers={
            "Origin": "http://localhost:5173",
            "Access-Control-Request-Method": "POST",
            "Access-Control-Request-Headers": "content-type",
        },
    )

    assert response.status_code == 200
    assert response.headers["access-control-allow-origin"] == "http://localhost:5173"


async def test_cors_rejects_unknown_origin(client: AsyncClient) -> None:
    response = await client.options(
        LOGIN_URL,
        headers={"Origin": "http://evil.example.com", "Access-Control-Request-Method": "POST"},
    )

    assert "access-control-allow-origin" not in response.headers
