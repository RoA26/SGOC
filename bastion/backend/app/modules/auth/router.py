from fastapi import APIRouter

from app.core.deps import DB, CurrentActor
from app.modules.auth import service
from app.modules.auth.schemas import LoginIn, MeOut, TokenOut
from app.modules.organizations.models import Organization

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login", response_model=TokenOut)
def login(body: LoginIn, db: DB) -> TokenOut:
    token, ttl = service.login(db, body.email, body.password)
    return TokenOut(access_token=token, expires_in=ttl)


@router.get("/me", response_model=MeOut)
def me(actor: CurrentActor, db: DB) -> MeOut:
    u = actor.user
    org = db.get(Organization, u.organization_id) if u.organization_id else None
    return MeOut(
        id=u.id,
        email=u.email,
        full_name=u.full_name,
        role=u.role,
        organization_id=u.organization_id,
        organization_name=org.name if org else None,
    )


@router.post("/logout", status_code=204)
def logout(actor: CurrentActor, db: DB) -> None:
    service.logout(db, actor.user)
