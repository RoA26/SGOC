from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import conflict
from app.modules.catalog.models import Product
from app.modules.inventory.models import Stock, Warehouse
from app.modules.inventory.schemas import StockRow


def list_warehouses(db: Session, org_id: int) -> list[Warehouse]:
    return list(db.scalars(select(Warehouse).where(Warehouse.organization_id == org_id).order_by(Warehouse.code)))


def create_warehouse(db: Session, org_id: int, code: str, name: str) -> Warehouse:
    w = Warehouse(organization_id=org_id, code=code, name=name)
    db.add(w)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise conflict("bodega_duplicada", "Ya existe una bodega con ese código") from None
    return w


def stock_rows(db: Session, org_id: int, warehouse_id: int | None, below_min: bool) -> list[StockRow]:
    stmt = (
        select(Stock, Product, Warehouse)
        .join(Product, (Product.id == Stock.product_id) & (Product.organization_id == Stock.organization_id))
        .join(Warehouse, (Warehouse.id == Stock.warehouse_id) & (Warehouse.organization_id == Stock.organization_id))
        .where(Stock.organization_id == org_id)
        .order_by(Product.name, Warehouse.code)
    )
    if warehouse_id is not None:
        stmt = stmt.where(Stock.warehouse_id == warehouse_id)
    rows = []
    for s, p, w in db.execute(stmt):
        low = s.quantity <= p.min_stock
        if below_min and not low:
            continue
        rows.append(
            StockRow(
                product_id=p.id,
                sku=p.sku,
                product_name=p.name,
                unit=p.unit,
                warehouse_id=w.id,
                warehouse_code=w.code,
                quantity=s.quantity,
                min_stock=p.min_stock,
                below_min=low,
                avg_cost=s.avg_cost,
                total_value=(s.quantity * s.avg_cost).quantize(Decimal("0.01")),
            )
        )
    return rows
