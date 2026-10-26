from fastapi import APIRouter

from app.core.deps import DB, OrgAdmin
from app.core.roles import Rol
from app.modules.users import service
from app.modules.users.schemas import PasswordReset, UserCreate, UserOut, UserUpdate

router = APIRouter(prefix="/users", tags=["users"])


@router.get("", response_model=list[UserOut])
def list_users(actor: OrgAdmin, db: DB) -> list[UserOut]:
    return [UserOut.model_validate(u) for u in service.list_org_users(db, actor.org_id)]


@router.post("", response_model=UserOut, status_code=201)
def create_worker(body: UserCreate, actor: OrgAdmin, db: DB) -> UserOut:
    user = service.new_user(
        db,
        email=body.email,
        full_name=body.full_name,
        password=body.password,
        role=Rol.TRABAJADOR,
        org_id=actor.org_id,
    )
    db.commit()
    return UserOut.model_validate(user)


@router.patch("/{user_id}", response_model=UserOut)
def update_worker(user_id: int, body: UserUpdate, actor: OrgAdmin, db: DB) -> UserOut:
    return UserOut.model_validate(
        service.update_org_user(db, actor.org_id, actor.id, user_id, body.full_name, body.active)
    )


@router.post("/{user_id}/password", status_code=204)
def reset_password(user_id: int, body: PasswordReset, actor: OrgAdmin, db: DB) -> None:
    service.reset_worker_password(db, actor.org_id, actor.id, user_id, body.password)
