import uuid
from datetime import timedelta

import jwt
import pytest

from app.core.config import settings
from app.core.security import (
    InvalidTokenError,
    create_access_token,
    decode_access_token,
    hash_password,
    verify_password,
)


def test_hash_password_uses_random_salt_and_verifies() -> None:
    first = hash_password("correcto")
    second = hash_password("correcto")

    assert first != second
    assert first.startswith("$2b$")
    assert verify_password("correcto", first)
    assert not verify_password("incorrecto", first)


def test_hash_password_rejects_passwords_over_72_bytes() -> None:
    with pytest.raises(ValueError, match="72 bytes"):
        hash_password("ñ" * 37)  # 74 bytes en UTF-8


def test_verify_password_never_raises() -> None:
    assert not verify_password("x" * 100, hash_password("x" * 72))
    assert not verify_password("correcto", "no-es-un-hash-bcrypt")


def test_access_token_roundtrip() -> None:
    user_id = uuid.uuid4()

    payload = decode_access_token(create_access_token(user_id))

    assert payload.sub == str(user_id)
    assert payload.type == "access"
    assert payload.exp > payload.iat


def test_expired_token_is_rejected() -> None:
    token = create_access_token("user", expires_delta=timedelta(seconds=-1))

    with pytest.raises(InvalidTokenError, match="expirado"):
        decode_access_token(token)


def test_tampered_token_is_rejected() -> None:
    header, payload, signature = create_access_token("user").split(".")
    tampered = f"{header}.{payload}.{signature[::-1]}"

    with pytest.raises(InvalidTokenError):
        decode_access_token(tampered)


def test_token_signed_with_other_key_is_rejected() -> None:
    forged = jwt.encode(
        {"sub": "user", "exp": 9999999999, "iat": 0, "jti": "x", "type": "access"},
        "otra-clave-secreta-de-al-menos-32-bytes!!",
        algorithm=settings.JWT_ALGORITHM,
    )

    with pytest.raises(InvalidTokenError):
        decode_access_token(forged)


def test_unsigned_token_is_rejected() -> None:
    unsigned = jwt.encode(
        {"sub": "user", "exp": 9999999999, "iat": 0, "jti": "x", "type": "access"},
        key=None,
        algorithm="none",
    )

    with pytest.raises(InvalidTokenError):
        decode_access_token(unsigned)


def test_token_missing_type_claim_is_rejected() -> None:
    token = jwt.encode(
        {"sub": "user", "exp": 9999999999, "iat": 0, "jti": "x"},
        settings.JWT_SECRET_KEY.get_secret_value(),
        algorithm=settings.JWT_ALGORITHM,
    )

    with pytest.raises(InvalidTokenError):
        decode_access_token(token)
