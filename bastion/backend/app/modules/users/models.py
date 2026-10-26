from datetime import datetime

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    ForeignKey,
    Index,
    Integer,
    String,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base


class User(Base):
    __tablename__ = "users"
    __table_args__ = (
        CheckConstraint("role IN ('SUPER_ADMIN','ADMINISTRADOR_COMERCIAL','TRABAJADOR')", name="rol_valido"),
        # SUPER_ADMIN no pertenece a ninguna organización; los demás roles siempre a una.
        CheckConstraint(
            "(role = 'SUPER_ADMIN' AND organization_id IS NULL)"
            " OR (role <> 'SUPER_ADMIN' AND organization_id IS NOT NULL)",
            name="rol_organizacion",
        ),
        UniqueConstraint("organization_id", "id"),
        Index("uq_users_email_lower", text("lower(email)"), unique=True),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    organization_id: Mapped[int | None] = mapped_column(ForeignKey("organizations.id", ondelete="RESTRICT"), index=True)
    email: Mapped[str] = mapped_column(String(254))
    full_name: Mapped[str] = mapped_column(String(160))
    password_hash: Mapped[str] = mapped_column(String(255))
    role: Mapped[str] = mapped_column(String(32))
    active: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")
    # Se incrementa al cerrar sesión, cambiar rol o desactivar: invalida los JWT emitidos.
    token_version: Mapped[int] = mapped_column(Integer, default=0, server_default="0")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
