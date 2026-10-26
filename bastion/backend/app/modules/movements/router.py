from typing import Annotated

from fastapi import APIRouter, Header, Query, Response

from app.core.deps import DB, OrgAdmin, OrgMember
from app.modules.movements import service
from app.modules.movements.schemas import MovementIn, MovementOut, ReversalIn

router = APIRouter(prefix="/movements", tags=["movements"])
IdemKey = Annotated[str | None, Header(alias="Idempotency-Key", min_length=8, max_length=80)]


@router.post("", response_model=MovementOut, status_code=201)
def create_movement(
    body: MovementIn, actor: OrgMember, db: DB, response: Response, idempotency_key: IdemKey = None
) -> MovementOut:
    mov, created = service.register(db, actor, body, idempotency_key)
    if not created:
        response.status_code = 200
    return MovementOut.model_validate(mov)


@router.post("/{movement_id}/reversal", response_model=MovementOut, status_code=201)
def reverse_movement(movement_id: int, body: ReversalIn, actor: OrgAdmin, db: DB) -> MovementOut:
    return MovementOut.model_validate(service.reverse(db, actor, movement_id, body.notes))


@router.get("", response_model=list[MovementOut])
def list_movements(
    actor: OrgMember,
    db: DB,
    product_id: int | None = None,
    warehouse_id: int | None = None,
    limit: int = Query(default=50, ge=1, le=200),
    offset: int = Query(default=0, ge=0),
) -> list[MovementOut]:
    return [
        MovementOut.model_validate(m)
        for m in service.list_movements(db, actor, product_id, warehouse_id, limit, offset)
    ]
