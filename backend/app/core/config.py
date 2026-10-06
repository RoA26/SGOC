"""Configuración central de la aplicación, cargada desde variables de entorno o `.env`."""

from functools import lru_cache
from typing import Annotated, Literal

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, NoDecode, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    PROJECT_NAME: str = "SGOC - Gestión de Órdenes de Compra"
    VERSION: str = "0.1.0"
    ENVIRONMENT: Literal["development", "test", "production"] = "development"
    API_PREFIX: str = "/api"

    # --- Base de datos ---
    DATABASE_URL: str = "postgresql+asyncpg://sgoc:sgoc@localhost:5432/sgoc"
    DATABASE_ECHO: bool = False

    # --- Seguridad ---
    # Obligatoria: la aplicación no arranca sin una clave de firma suficientemente larga.
    JWT_SECRET_KEY: SecretStr = Field(min_length=32)
    JWT_ALGORITHM: Literal["HS256", "HS384", "HS512"] = "HS256"
    ACCESS_TOKEN_EXPIRE_MINUTES: int = Field(default=60, gt=0)
    BCRYPT_ROUNDS: int = Field(default=12, ge=4, le=31)

    # --- CORS ---
    # Se acepta una lista separada por comas: "http://localhost:5173,https://app.example.com".
    CORS_ORIGINS: Annotated[list[str], NoDecode] = ["http://localhost:5173"]

    @field_validator("CORS_ORIGINS", mode="before")
    @classmethod
    def _split_cors_origins(cls, value: object) -> object:
        if isinstance(value, str):
            return [origin.strip().rstrip("/") for origin in value.split(",") if origin.strip()]
        return value


@lru_cache
def get_settings() -> Settings:
    return Settings()


settings = get_settings()
