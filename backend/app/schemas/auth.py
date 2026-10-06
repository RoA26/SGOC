from typing import Literal

from pydantic import BaseModel, EmailStr, Field, SecretStr


class LoginRequest(BaseModel):
    email: EmailStr
    # SecretStr evita que la contraseña aparezca en logs o reprs por accidente.
    password: SecretStr = Field(min_length=1, max_length=256)


class TokenResponse(BaseModel):
    access_token: str
    token_type: Literal["bearer"] = "bearer"  # noqa: S105 - no es una contraseña
    expires_in: int = Field(description="Segundos de validez del token.")
