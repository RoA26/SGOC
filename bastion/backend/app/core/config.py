"""Configuración leída del entorno. Nunca se registran los valores secretos."""

from functools import lru_cache

from pydantic import Field, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_prefix="BASTION_", env_file=None, extra="ignore")

    env: str = "development"
    database_url: str = "postgresql+psycopg://bastion@127.0.0.1:5432/bastion"
    jwt_secret: str = Field(min_length=32)
    jwt_expire_minutes: int = Field(default=30, ge=5, le=720)
    cors_origins: list[str] = []
    db_pool_size: int = Field(default=5, ge=1, le=20)

    @field_validator("jwt_secret")
    @classmethod
    def _secreto_no_trivial(cls, v: str) -> str:
        if len(set(v)) < 10:
            raise ValueError("BASTION_JWT_SECRET es demasiado predecible")
        return v


@lru_cache
def get_settings() -> Settings:
    return Settings()  # type: ignore[call-arg]
