import uuid
from datetime import datetime
from typing import Annotated

from pydantic import AfterValidator, BaseModel, ConfigDict, EmailStr, Field

from app.core.security import BCRYPT_MAX_PASSWORD_BYTES, password_exceeds_bcrypt_limit
from app.models.user import FULL_NAME_MAX_LENGTH

PASSWORD_MIN_LENGTH = 8


def _check_bcrypt_limit(password: str) -> str:
    if password_exceeds_bcrypt_limit(password):
        raise ValueError(f"La contraseña no puede superar {BCRYPT_MAX_PASSWORD_BYTES} bytes.")
    return password


NewPassword = Annotated[
    str,
    Field(min_length=PASSWORD_MIN_LENGTH),
    AfterValidator(_check_bcrypt_limit),
]


class UserCreate(BaseModel):
    """Datos para dar de alta un usuario (usado por la CLI de administración)."""

    email: EmailStr
    full_name: str = Field(min_length=1, max_length=FULL_NAME_MAX_LENGTH)
    password: NewPassword


class UserRead(BaseModel):
    """Representación pública de un usuario. Nunca expone el hash de la contraseña."""

    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    email: str
    full_name: str
    is_active: bool
    created_at: datetime
