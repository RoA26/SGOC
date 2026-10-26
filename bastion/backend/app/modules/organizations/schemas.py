from datetime import datetime

from pydantic import BaseModel, ConfigDict, EmailStr, Field

from app.modules.users.schemas import Password


class FirstAdmin(BaseModel):
    email: EmailStr
    full_name: str = Field(min_length=2, max_length=160)
    password: str = Password


class OrganizationCreate(BaseModel):
    name: str = Field(min_length=2, max_length=160)
    slug: str = Field(pattern=r"^[a-z0-9](?:[a-z0-9-]{0,58}[a-z0-9])?$")
    admin: FirstAdmin


class OrganizationUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=2, max_length=160)
    active: bool | None = None


class OrganizationOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    slug: str
    active: bool
    created_at: datetime
