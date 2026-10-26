from fastapi import APIRouter

from app.core.deps import DB, OrgAdmin, OrgMember
from app.modules.inventory import service
from app.modules.inventory.schemas import StockRow, WarehouseIn, WarehouseOut

router = APIRouter(tags=["inventory"])


@router.get("/warehouses", response_model=list[WarehouseOut])
def list_warehouses(actor: OrgMember, db: DB) -> list[WarehouseOut]:
    return [WarehouseOut.model_validate(w) for w in service.list_warehouses(db, actor.org_id)]


@router.post("/warehouses", response_model=WarehouseOut, status_code=201)
def create_warehouse(body: WarehouseIn, actor: OrgAdmin, db: DB) -> WarehouseOut:
    return WarehouseOut.model_validate(service.create_warehouse(db, actor.org_id, body.code, body.name))


@router.get("/inventory", response_model=list[StockRow])
def inventory(actor: OrgMember, db: DB, warehouse_id: int | None = None, below_min: bool = False) -> list[StockRow]:
    return service.stock_rows(db, actor.org_id, warehouse_id, below_min)
