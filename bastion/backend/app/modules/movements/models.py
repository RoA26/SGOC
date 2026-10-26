from datetime import datetime
from decimal import Decimal

from sqlalchemy import (
    CheckConstraint,
    DateTime,
    ForeignKey,
    ForeignKeyConstraint,
    Index,
    Numeric,
    String,
    UniqueConstraint,
    func,
    text,
)
from sqlalchemy.orm import Mapped, mapped_column

from app.core.db import Base

TIPOS = ("ENTRADA", "SALIDA", "AJUSTE", "REVERSION")


class Movement(Base):
    """Historial inmutable. Las correcciones se registran como REVERSION, nunca se borra."""

    __tablename__ = "movements"
    __table_args__ = (
        UniqueConstraint("organization_id", "id"),
        ForeignKeyConstraint(["organization_id", "product_id"], ["products.organization_id", "products.id"]),
        ForeignKeyConstraint(["organization_id", "warehouse_id"], ["warehouses.organization_id", "warehouses.id"]),
        ForeignKeyConstraint(["organization_id", "user_id"], ["users.organization_id", "users.id"]),
        ForeignKeyConstraint(["organization_id", "reverses_id"], ["movements.organization_id", "movements.id"]),
        CheckConstraint(f"kind IN {TIPOS}", name="tipo_valido"),
        CheckConstraint("quantity <> 0", name="cantidad_no_cero"),
        CheckConstraint("kind NOT IN ('ENTRADA') OR quantity > 0", name="entrada_positiva"),
        CheckConstraint("kind NOT IN ('SALIDA') OR quantity < 0", name="salida_negativa"),
        CheckConstraint("balance_after >= 0", name="saldo_no_negativo"),
        CheckConstraint("(kind = 'REVERSION') = (reverses_id IS NOT NULL)", name="reversion_referencia"),
        # Un movimiento solo puede revertirse una vez.
        Index("uq_movements_reverses", "reverses_id", unique=True, postgresql_where=text("reverses_id IS NOT NULL")),
        # Reintentos con la misma clave no duplican el movimiento.
        Index(
            "uq_movements_idempotency",
            "organization_id",
            "idempotency_key",
            unique=True,
            postgresql_where=text("idempotency_key IS NOT NULL"),
        ),
        Index("ix_movements_org_created", "organization_id", "created_at"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    organization_id: Mapped[int] = mapped_column(ForeignKey("organizations.id", ondelete="RESTRICT"))
    product_id: Mapped[int] = mapped_column()
    warehouse_id: Mapped[int] = mapped_column()
    user_id: Mapped[int] = mapped_column()
    kind: Mapped[str] = mapped_column(String(12))
    # Con signo: positiva incrementa el saldo, negativa lo reduce.
    quantity: Mapped[Decimal] = mapped_column(Numeric(14, 3))
    unit_cost: Mapped[Decimal | None] = mapped_column(Numeric(18, 4))
    balance_after: Mapped[Decimal] = mapped_column(Numeric(14, 3))
    avg_cost_after: Mapped[Decimal] = mapped_column(Numeric(18, 4))
    reference: Mapped[str | None] = mapped_column(String(120))
    notes: Mapped[str | None] = mapped_column(String(500))
    reverses_id: Mapped[int | None] = mapped_column()
    idempotency_key: Mapped[str | None] = mapped_column(String(80))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
