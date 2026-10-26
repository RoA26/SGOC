from datetime import UTC, datetime, timedelta

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

from app.core.config import get_settings

_ph = PasswordHasher()  # Argon2id con parámetros por defecto de argon2-cffi
ALG = "HS256"
# Hash fijo para igualar el tiempo de respuesta cuando el usuario no existe.
_DUMMY_HASH = _ph.hash("bastion-usuario-inexistente")


def hash_password(raw: str) -> str:
    return _ph.hash(raw)


def verify_password(hashed: str | None, raw: str) -> bool:
    try:
        return _ph.verify(hashed or _DUMMY_HASH, raw) and hashed is not None
    except (VerificationError, InvalidHashError):
        return False


def create_token(user_id: int, token_version: int) -> tuple[str, int]:
    s = get_settings()
    now = datetime.now(UTC)
    exp = now + timedelta(minutes=s.jwt_expire_minutes)
    payload = {"sub": str(user_id), "tv": token_version, "iat": now, "exp": exp, "iss": "bastion"}
    return jwt.encode(payload, s.jwt_secret, algorithm=ALG), s.jwt_expire_minutes * 60


def decode_token(token: str) -> dict[str, object]:
    """Valida firma, algoritmo, emisor y expiración. Lanza jwt.PyJWTError si falla."""
    return jwt.decode(
        token, get_settings().jwt_secret, algorithms=[ALG], issuer="bastion", options={"require": ["exp", "sub", "tv"]}
    )
