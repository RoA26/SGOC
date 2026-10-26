from sqlalchemy import func, or_, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import DomainError, conflict, not_found
from app.modules.catalog.models import Category, Product
from app.modules.catalog.schemas import ProductIn, ProductUpdate


def list_categories(db: Session, org_id: int) -> list[Category]:
    return list(db.scalars(select(Category).where(Category.organization_id == org_id).order_by(Category.name)))


def create_category(db: Session, org_id: int, name: str) -> Category:
    cat = Category(organization_id=org_id, name=name.strip())
    db.add(cat)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise conflict("categoria_duplicada", "Ya existe una categoría con ese nombre") from None
    return cat


def _check_category(db: Session, org_id: int, category_id: int | None) -> None:
    if category_id is None:
        return
    found = db.scalar(select(Category.id).where(Category.organization_id == org_id, Category.id == category_id))
    if found is None:
        raise DomainError(422, "categoria_invalida", "La categoría no existe en su organización")


def list_products(db: Session, org_id: int, q: str | None, include_inactive: bool) -> list[Product]:
    stmt = select(Product).where(Product.organization_id == org_id)
    if not include_inactive:
        stmt = stmt.where(Product.active.is_(True))
    if q:
        like = f"%{q.lower()}%"
        stmt = stmt.where(
            or_(func.lower(Product.name).like(like), func.lower(Product.sku).like(like), Product.barcode == q)
        )
    return list(db.scalars(stmt.order_by(Product.name).limit(500)))


def get_product(db: Session, org_id: int, product_id: int) -> Product:
    p = db.scalar(select(Product).where(Product.organization_id == org_id, Product.id == product_id))
    if p is None:
        raise not_found("El producto")
    return p


def _commit_product(db: Session) -> None:
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise conflict("producto_duplicado", "Ya existe un producto con ese SKU o código de barras") from None


def create_product(db: Session, org_id: int, data: ProductIn) -> Product:
    _check_category(db, org_id, data.category_id)
    p = Product(organization_id=org_id, **data.model_dump())
    db.add(p)
    _commit_product(db)
    return p


def update_product(db: Session, org_id: int, product_id: int, data: ProductUpdate) -> Product:
    p = get_product(db, org_id, product_id)
    changes = data.model_dump(exclude_unset=True)
    if "category_id" in changes:
        _check_category(db, org_id, changes["category_id"])
    for k, v in changes.items():
        setattr(p, k, v)
    _commit_product(db)
    return p
