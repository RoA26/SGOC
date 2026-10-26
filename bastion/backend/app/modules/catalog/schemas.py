from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field

Unidad = Literal["UND", "M", "M2", "KG", "ROLLO", "L"]


class CategoryIn(BaseModel):
    name: str = Field(min_length=1, max_length=120)


class CategoryOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str


class ProductIn(BaseModel):
    sku: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9._\-/]+$")
    barcode: str | None = Field(default=None, min_length=1, max_length=64)
    name: str = Field(min_length=1, max_length=200)
    category_id: int | None = None
    unit: Unidad = "UND"
    min_stock: Decimal = Field(default=Decimal(0), ge=0, max_digits=14, decimal_places=3)


class ProductUpdate(BaseModel):
    barcode: str | None = Field(default=None, min_length=1, max_length=64)
    name: str | None = Field(default=None, min_length=1, max_length=200)
    category_id: int | None = None
    unit: Unidad | None = None
    min_stock: Decimal | None = Field(default=None, ge=0, max_digits=14, decimal_places=3)
    active: bool | None = None


class ProductOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    sku: str
    barcode: str | None
    name: str
    category_id: int | None
    unit: str
    min_stock: Decimal
    active: bool
