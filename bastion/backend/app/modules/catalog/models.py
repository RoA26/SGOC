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

UNIDADES = ("UND", "M", "M2", "KG", "ROLLO", "L")


class Category(Base):
    __tablename__ = "categories"
    __table_args__ = (
        UniqueConstraint("organization_id", "id"),
        Index("uq_categories_org_name", "organization_id", text("lower(name)"), unique=True),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    organization_id: Mapped[int] = mapped_column(ForeignKey("organizations.id", ondelete="RESTRICT"))
    name: Mapped[str] = mapped_column(String(120))


class Product(Base):
    __tablename__ = "products"
    __table_args__ = (
        UniqueConstraint("organization_id", "id"),
        UniqueConstraint("organization_id", "sku"),
        Index(
            "uq_products_org_barcode",
            "organization_id",
            "barcode",
            unique=True,
            postgresql_where=text("barcode IS NOT NULL"),
        ),
        # La categoría debe ser de la misma organización (FK compuesta).
        ForeignKeyConstraint(["organization_id", "category_id"], ["categories.organization_id", "categories.id"]),
        CheckConstraint("min_stock >= 0", name="min_stock_no_negativo"),
        CheckConstraint(f"unit IN {UNIDADES}", name="unidad_valida"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    organization_id: Mapped[int] = mapped_column(ForeignKey("organizations.id", ondelete="RESTRICT"), index=True)
    category_id: Mapped[int | None] = mapped_column()
    sku: Mapped[str] = mapped_column(String(64))
    barcode: Mapped[str | None] = mapped_column(String(64))
    name: Mapped[str] = mapped_column(String(200))
    unit: Mapped[str] = mapped_column(String(8), default="UND", server_default="UND")
    min_stock: Mapped[Decimal] = mapped_column(Numeric(14, 3), default=0, server_default="0")
    active: Mapped[bool] = mapped_column(Boolean, default=True, server_default="true")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
