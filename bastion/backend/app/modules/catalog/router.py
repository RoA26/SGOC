from fastapi import APIRouter, Query

from app.core.deps import DB, OrgAdmin, OrgMember
from app.modules.catalog import service
from app.modules.catalog.schemas import CategoryIn, CategoryOut, ProductIn, ProductOut, ProductUpdate

router = APIRouter(tags=["catalog"])


@router.get("/categories", response_model=list[CategoryOut])
def list_categories(actor: OrgMember, db: DB) -> list[CategoryOut]:
    return [CategoryOut.model_validate(c) for c in service.list_categories(db, actor.org_id)]


@router.post("/categories", response_model=CategoryOut, status_code=201)
def create_category(body: CategoryIn, actor: OrgAdmin, db: DB) -> CategoryOut:
    return CategoryOut.model_validate(service.create_category(db, actor.org_id, body.name))


@router.get("/products", response_model=list[ProductOut])
def list_products(
    actor: OrgMember, db: DB, q: str | None = Query(default=None, max_length=100), include_inactive: bool = False
) -> list[ProductOut]:
    return [ProductOut.model_validate(p) for p in service.list_products(db, actor.org_id, q, include_inactive)]


@router.get("/products/{product_id}", response_model=ProductOut)
def get_product(product_id: int, actor: OrgMember, db: DB) -> ProductOut:
    return ProductOut.model_validate(service.get_product(db, actor.org_id, product_id))


@router.post("/products", response_model=ProductOut, status_code=201)
def create_product(body: ProductIn, actor: OrgAdmin, db: DB) -> ProductOut:
    return ProductOut.model_validate(service.create_product(db, actor.org_id, body))


@router.patch("/products/{product_id}", response_model=ProductOut)
def update_product(product_id: int, body: ProductUpdate, actor: OrgAdmin, db: DB) -> ProductOut:
    return ProductOut.model_validate(service.update_product(db, actor.org_id, product_id, body))
