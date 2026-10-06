"""Casos de uso de autenticación. Independiente de HTTP: comunica fallos mediante excepciones."""

from datetime import timedelta
from functools import cache

from anyio import to_thread
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.config import settings
from app.core.security import create_access_token, hash_password, verify_password
from app.models.user import User
from app.schemas.auth import TokenResponse
from app.services.user_service import get_user_by_email


class AuthenticationError(Exception):
    """Base de los errores de autenticación."""


class InvalidCredentialsError(AuthenticationError):
    """Correo inexistente o contraseña incorrecta (indistinguibles a propósito)."""


class InactiveUserError(AuthenticationError):
    """Credenciales correctas, pero la cuenta está deshabilitada."""


@cache
def _dummy_password_hash() -> str:
    # Hash de referencia para igualar el tiempo de respuesta cuando el correo no existe,
    # evitando la enumeración de usuarios por análisis de tiempos.
    return hash_password("dummy-password-for-timing-equalization")


async def authenticate_user(session: AsyncSession, email: str, password: str) -> User:
    """Valida las credenciales y devuelve el usuario autenticado.

    Raises:
        InvalidCredentialsError: si el correo no existe o la contraseña no coincide.
        InactiveUserError: si la cuenta está deshabilitada.
    """
    user = await get_user_by_email(session, email)
    if user is None:
        await to_thread.run_sync(verify_password, password, _dummy_password_hash())
        raise InvalidCredentialsError

    if not await to_thread.run_sync(verify_password, password, user.hashed_password):
        raise InvalidCredentialsError

    if not user.is_active:
        raise InactiveUserError

    return user


def issue_access_token(user: User) -> TokenResponse:
    expires_delta = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    return TokenResponse(
        access_token=create_access_token(user.id, expires_delta=expires_delta),
        expires_in=int(expires_delta.total_seconds()),
    )
