from pydantic import BaseModel, EmailStr, Field


class LoginIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=1, max_length=256)


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"  # noqa: S105  (no es un secreto)
    expires_in: int


class MeOut(BaseModel):
    id: int
    email: str
    full_name: str
    role: str
    organization_id: int | None
    organization_name: str | None
