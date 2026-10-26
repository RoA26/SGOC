"""Registro de movimientos de inventario.

Toda modificación de `stock` pasa por aquí, dentro de una transacción que bloquea la fila
de saldo (SELECT ... FOR UPDATE). Así dos peticiones simultáneas sobre el mismo producto y
bodega se serializan y no pueden dejar el saldo negativo ni perder actualizaciones.
Ver docs/inventario.md para la fórmula del costo promedio ponderado.
"""

from decimal import ROUND_HALF_UP, Decimal

from sqlalchemy import func, select
from sqlalchemy.dialects.postgresql import insert
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.deps import Actor
from app.core.errors import DomainError, conflict, forbidden, not_found
from app.core.roles import Rol
from app.modules.catalog.models import Product
from app.modules.inventory.models import Stock, Warehouse
from app.modules.movements.models import Movement
from app.modules.movements.schemas import MovementIn

COST_Q = Decimal("0.0001")


def _lock_stock(db: Session, org_id: int, product_id: int, warehouse_id: int) -> Stock:
    db.execute(
        insert(Stock)
        .values(organization_id=org_id, product_id=product_id, warehouse_id=warehouse_id)
        .on_conflict_do_nothing(index_elements=["organization_id", "product_id", "warehouse_id"])
    )
    stock = db.scalar(
        select(Stock)
        .where(Stock.organization_id == org_id, Stock.product_id == product_id, Stock.warehouse_id == warehouse_id)
        .with_for_update()
        .execution_options(populate_existing=True)
    )
    assert stock is not None
    return stock


def weighted_avg(qty: Decimal, avg: Decimal, in_qty: Decimal, in_cost: Decimal) -> Decimal:
    """Costo promedio ponderado tras una entrada: (Q·C + q·c) / (Q + q)."""
    total = qty + in_qty
    if total <= 0:
        return avg
    return ((qty * avg + in_qty * in_cost) / total).quantize(COST_Q, ROUND_HALF_UP)


def _by_key(db: Session, org_id: int, key: str) -> Movement | None:
    return db.scalar(select(Movement).where(Movement.organization_id == org_id, Movement.idempotency_key == key))


def _same_request(m: Movement, data: MovementIn, signed: Decimal) -> bool:
    return (m.kind, m.product_id, m.warehouse_id, m.quantity) == (data.kind, data.product_id, data.warehouse_id, signed)


def _apply(
    db: Session,
    actor: Actor,
    *,
    kind: str,
    product_id: int,
    warehouse_id: int,
    signed: Decimal,
    unit_cost: Decimal | None,
    reference: str | None,
    notes: str | None,
    reverses: Movement | None,
    key: str | None,
) -> Movement:
    org = actor.org_id
    stock = _lock_stock(db, org, product_id, warehouse_id)
    new_qty = stock.quantity + signed
    if new_qty < 0:
        raise conflict("stock_insuficiente", f"Existencias insuficientes: disponible {stock.quantity.normalize():f}")

    new_avg = stock.avg_cost
    if kind == "ENTRADA" and unit_cost is not None:
        new_avg = weighted_avg(stock.quantity, stock.avg_cost, signed, unit_cost)
    elif reverses is not None and reverses.kind == "ENTRADA" and reverses.unit_cost is not None and new_qty > 0:
        # Deshace el aporte de la entrada revertida si el resultado es coherente.
        undone = (stock.quantity * stock.avg_cost - reverses.quantity * reverses.unit_cost) / new_qty
        if undone >= 0:
            new_avg = undone.quantize(COST_Q, ROUND_HALF_UP)

    stock.quantity = new_qty
    stock.avg_cost = new_avg
    stock.updated_at = func.now()
    mov = Movement(
        organization_id=org,
        product_id=product_id,
        warehouse_id=warehouse_id,
        user_id=actor.id,
        kind=kind,
        quantity=signed,
        unit_cost=unit_cost,
        balance_after=new_qty,
        avg_cost_after=new_avg,
        reference=reference,
        notes=notes,
        reverses_id=reverses.id if reverses else None,
        idempotency_key=key,
    )
    db.add(mov)
    db.flush()
    return mov


def register(db: Session, actor: Actor, data: MovementIn, key: str | None) -> tuple[Movement, bool]:
    """Registra un movimiento. Devuelve (movimiento, creado). `creado` es False en un reintento idempotente."""
    org = actor.org_id
    if data.kind == "AJUSTE" and actor.role != Rol.ADMINISTRADOR_COMERCIAL:
        raise forbidden("Solo un administrador comercial puede registrar ajustes")
    signed = -data.quantity if data.kind == "SALIDA" else data.quantity

    if key:
        prev = _by_key(db, org, key)
        if prev is not None:
            if not _same_request(prev, data, signed):
                raise conflict("clave_reutilizada", "La clave de idempotencia ya se usó con otros datos")
            return prev, False

    product = db.scalar(select(Product).where(Product.organization_id == org, Product.id == data.product_id))
    if product is None:
        raise not_found("El producto")
    if not product.active:
        raise DomainError(422, "producto_inactivo", "El producto está inactivo")
    wh = db.scalar(select(Warehouse).where(Warehouse.organization_id == org, Warehouse.id == data.warehouse_id))
    if wh is None:
        raise not_found("La bodega")
    if not wh.active:
        raise DomainError(422, "bodega_inactiva", "La bodega está inactiva")

    try:
        mov = _apply(
            db,
            actor,
            kind=data.kind,
            product_id=product.id,
            warehouse_id=wh.id,
            signed=signed,
            unit_cost=data.unit_cost,
            reference=data.reference,
            notes=data.notes,
            reverses=None,
            key=key,
        )
        db.commit()
    except IntegrityError:
        db.rollback()
        # Carrera entre dos reintentos con la misma clave: gana el primero.
        prev = _by_key(db, org, key) if key else None
        if prev is not None and _same_request(prev, data, signed):
            return prev, False
        raise conflict("conflicto", "No se pudo registrar el movimiento por un conflicto de datos") from None
    except DomainError:
        db.rollback()
        raise
    return mov, True


def reverse(db: Session, actor: Actor, movement_id: int, notes: str) -> Movement:
    """Corrige un movimiento con otro de signo contrario. El original no se modifica."""
    org = actor.org_id
    orig = db.scalar(
        select(Movement).where(Movement.organization_id == org, Movement.id == movement_id).with_for_update()
    )
    if orig is None:
        raise not_found("El movimiento")
    if orig.kind == "REVERSION":
        raise DomainError(422, "no_reversible", "Una reversión no puede revertirse")
    if db.scalar(select(Movement.id).where(Movement.reverses_id == orig.id)) is not None:
        raise conflict("ya_revertido", "El movimiento ya fue revertido")
    try:
        mov = _apply(
            db,
            actor,
            kind="REVERSION",
            product_id=orig.product_id,
            warehouse_id=orig.warehouse_id,
            signed=-orig.quantity,
            unit_cost=None,
            reference=f"Reversión de #{orig.id}",
            notes=notes,
            reverses=orig,
            key=None,
        )
        db.commit()
    except IntegrityError:
        db.rollback()
        raise conflict("ya_revertido", "El movimiento ya fue revertido") from None
    except DomainError:
        db.rollback()
        raise
    return mov


def list_movements(
    db: Session, actor: Actor, product_id: int | None, warehouse_id: int | None, limit: int, offset: int
) -> list[Movement]:
    stmt = select(Movement).where(Movement.organization_id == actor.org_id)
    if actor.role == Rol.TRABAJADOR:
        stmt = stmt.where(Movement.user_id == actor.id)
    if product_id is not None:
        stmt = stmt.where(Movement.product_id == product_id)
    if warehouse_id is not None:
        stmt = stmt.where(Movement.warehouse_id == warehouse_id)
    return list(db.scalars(stmt.order_by(Movement.id.desc()).limit(limit).offset(offset)))
