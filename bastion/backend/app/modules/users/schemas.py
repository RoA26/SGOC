from typing import Literal

from pydantic import BaseModel, ConfigDict, EmailStr, Field

# Política de contraseñas mínima de la primera entrega.
Password = Field(min_length=10, max_length=128)


class UserCreate(BaseModel):
    email: EmailStr
    full_name: str = Field(min_length=2, max_length=160)
    password: str = Password
    # Un administrador comercial solo crea trabajadores: nunca roles superiores desde el cliente.
    role: Literal["TRABAJADOR"] = "TRABAJADOR"


class UserUpdate(BaseModel):
    full_name: str | None = Field(default=None, min_length=2, max_length=160)
    active: bool | None = None


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: str
    full_name: str
    role: str
    active: bool
    organization_id: int | None
