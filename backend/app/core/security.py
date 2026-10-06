"""Primitivas de seguridad: hashing de contraseñas con bcrypt y emisión/validación de JWT.

Este módulo no conoce HTTP ni la base de datos; solo expone funciones puras
que las capas superiores (servicios y dependencias de la API) componen.
"""

import uuid
from datetime import UTC, datetime, timedelta
from typing import Literal

import bcrypt
import jwt
from pydantic import BaseModel, ValidationError

from app.core.config import settings

# bcrypt solo procesa los primeros 72 bytes; bcrypt>=5 lanza ValueError si se excede.
BCRYPT_MAX_PASSWORD_BYTES = 72
ACCESS_TOKEN_TYPE = "access"  # noqa: S105 - no es una contraseña


class InvalidTokenError(Exception):
    """El token es inválido, está mal formado, fue manipulado o ha expirado."""


class TokenPayload(BaseModel):
    """Claims validados de un token de acceso."""

    sub: str
    exp: datetime
    iat: datetime
    jti: str
    type: Literal["access"]


# --------------------------------------------------------------------------- #
# Contraseñas
# --------------------------------------------------------------------------- #
def password_exceeds_bcrypt_limit(password: str) -> bool:
    return len(password.encode("utf-8")) > BCRYPT_MAX_PASSWORD_BYTES


def hash_password(password: str) -> str:
    """Genera el hash bcrypt (con sal aleatoria) de una contraseña en texto plano.

    Es una operación costosa y bloqueante: desde código async debe ejecutarse
    en un hilo (ver `anyio.to_thread.run_sync`).
    """
    if password_exceeds_bcrypt_limit(password):
        raise ValueError(f"La contraseña no puede superar {BCRYPT_MAX_PASSWORD_BYTES} bytes.")
    salt = bcrypt.gensalt(rounds=settings.BCRYPT_ROUNDS)
    return bcrypt.hashpw(password.encode("utf-8"), salt).decode("ascii")


def verify_password(plain_password: str, hashed_password: str) -> bool:
    """Compara una contraseña con su hash en tiempo constante. Nunca lanza excepciones."""
    if password_exceeds_bcrypt_limit(plain_password):
        return False
    try:
        return bcrypt.checkpw(plain_password.encode("utf-8"), hashed_password.encode("ascii"))
    except ValueError:
        # Hash almacenado corrupto o con formato no reconocido.
        return False


# --------------------------------------------------------------------------- #
# JWT
# --------------------------------------------------------------------------- #
def create_access_token(subject: str | uuid.UUID, expires_delta: timedelta | None = None) -> str:
    """Firma un JWT de acceso para `subject` (normalmente el id del usuario)."""
    issued_at = datetime.now(UTC)
    if expires_delta is None:
        expires_delta = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)

    claims = {
        "sub": str(subject),
        "iat": issued_at,
        "exp": issued_at + expires_delta,
        "jti": uuid.uuid4().hex,
        "type": ACCESS_TOKEN_TYPE,
    }
    return jwt.encode(
        claims,
        settings.JWT_SECRET_KEY.get_secret_value(),
        algorithm=settings.JWT_ALGORITHM,
    )


def decode_access_token(token: str) -> TokenPayload:
    """Verifica firma, expiración y claims obligatorios de un token de acceso.

    Raises:
        InvalidTokenError: si el token no es válido por cualquier motivo.
    """
    try:
        claims = jwt.decode(
            token,
            settings.JWT_SECRET_KEY.get_secret_value(),
            # Lista explícita: impide ataques de confusión de algoritmo ("alg": "none").
            algorithms=[settings.JWT_ALGORITHM],
            options={"require": ["sub", "exp", "iat", "jti"]},
        )
        return TokenPayload.model_validate(claims)
    except jwt.ExpiredSignatureError as exc:
        raise InvalidTokenError("El token ha expirado.") from exc
    except (jwt.PyJWTError, ValidationError) as exc:
        raise InvalidTokenError("El token no es válido.") from exc
