from decimal import Decimal

from pydantic import BaseModel, ConfigDict, Field


class WarehouseIn(BaseModel):
    code: str = Field(min_length=1, max_length=30, pattern=r"^[A-Za-z0-9._\-]+$")
    name: str = Field(min_length=1, max_length=120)


class WarehouseUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=120)
    active: bool | None = None


class WarehouseOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    code: str
    name: str
    active: bool


class StockRow(BaseModel):
    product_id: int
    sku: str
    product_name: str
    unit: str
    warehouse_id: int
    warehouse_code: str
    quantity: Decimal
    min_stock: Decimal
    below_min: bool
    # Solo para el ADMINISTRADOR_COMERCIAL; vacíos para el TRABAJADOR.
    avg_cost: Decimal | None
    total_value: Decimal | None
