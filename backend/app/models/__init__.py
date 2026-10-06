"""Modelos ORM. Importarlos aquí registra sus tablas en `Base.metadata` (necesario para Alembic)."""

from app.models.base import Base
from app.models.user import User

__all__ = ["Base", "User"]
