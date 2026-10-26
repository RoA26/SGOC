from datetime import datetime
from decimal import Decimal

from sqlalchemy import (
    Boolean,
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


class Warehouse(Base):
    __tablename__ = "warehouses"
    __table_args__ = (
        UniqueConstraint("organization_id", "id"),
        Index("uq_warehouses_org_code", "organization_id", text("lower(code)"), unique=True),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    organization_id: Mapped[int] = mapped_column(ForeignKey("organizations.id", ondelete="RESTRICT"), index=True)
    code: Mapped[str] = mapped_column(String(30))
    name: Mapped[str] = mapped_column(String(120))
    active: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")


class Stock(Base):
    """Saldo y costo promedio por producto y bodega. Solo lo modifica el servicio de movimientos."""

    __tablename__ = "stock"
    __table_args__ = (
        UniqueConstraint("organization_id", "product_id", "warehouse_id"),
        ForeignKeyConstraint(["organization_id", "product_id"], ["products.organization_id", "products.id"]),
        ForeignKeyConstraint(["organization_id", "warehouse_id"], ["warehouses.organization_id", "warehouses.id"]),
        CheckConstraint("quantity >= 0", name="saldo_no_negativo"),
        CheckConstraint("avg_cost >= 0", name="costo_no_negativo"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    organization_id: Mapped[int] = mapped_column(ForeignKey("organizations.id", ondelete="RESTRICT"))
    product_id: Mapped[int] = mapped_column()
    warehouse_id: Mapped[int] = mapped_column()
    quantity: Mapped[Decimal] = mapped_column(Numeric(14, 3), default=Decimal(0), server_default="0")
    avg_cost: Mapped[Decimal] = mapped_column(Numeric(18, 4), default=Decimal(0), server_default="0")
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
