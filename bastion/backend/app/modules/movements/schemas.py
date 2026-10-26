from datetime import datetime
from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

Qty = Field(max_digits=14, decimal_places=3)


class MovementIn(BaseModel):
    kind: Literal["ENTRADA", "SALIDA", "AJUSTE"]
    product_id: int
    warehouse_id: int
    # ENTRADA y SALIDA: cantidad positiva. AJUSTE: con signo (+ suma, − resta), distinta de cero.
    quantity: Decimal = Qty
    unit_cost: Decimal | None = Field(default=None, ge=0, max_digits=18, decimal_places=4)
    reference: str | None = Field(default=None, max_length=120)
    notes: str | None = Field(default=None, max_length=500)

    @model_validator(mode="after")
    def _check(self) -> "MovementIn":
        if self.quantity == 0:
            raise ValueError("La cantidad no puede ser cero")
        if self.kind != "AJUSTE" and self.quantity < 0:
            raise ValueError("La cantidad debe ser positiva")
        if self.kind != "ENTRADA" and self.unit_cost is not None:
            raise ValueError("Solo las entradas registran costo unitario")
        if self.kind == "AJUSTE" and not (self.notes and self.notes.strip()):
            raise ValueError("Un ajuste requiere una justificación en notas")
        return self


class ReversalIn(BaseModel):
    notes: str = Field(min_length=3, max_length=500)


class MovementOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    kind: str
    product_id: int
    warehouse_id: int
    user_id: int
    quantity: Decimal
    unit_cost: Decimal | None
    balance_after: Decimal
    avg_cost_after: Decimal
    reference: str | None
    notes: str | None
    reverses_id: int | None
    created_at: datetime
