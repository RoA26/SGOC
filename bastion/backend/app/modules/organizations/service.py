from sqlalchemy import select, update
from sqlalchemy.orm import Session

from app.core.errors import conflict, not_found
from app.core.roles import Rol
from app.modules.organizations.models import Organization
from app.modules.organizations.schemas import OrganizationCreate
from app.modules.users.models import User
from app.modules.users.service import new_user


def create(db: Session, data: OrganizationCreate) -> Organization:
    """Crea la organización y su primer ADMINISTRADOR_COMERCIAL en una sola transacción."""
    if db.scalar(select(Organization.id).where(Organization.slug == data.slug)) is not None:
        raise conflict("slug_duplicado", "Ya existe una organización con ese identificador")
    org = Organization(name=data.name, slug=data.slug)
    db.add(org)
    db.flush()
    new_user(
        db,
        email=data.admin.email,
        full_name=data.admin.full_name,
        password=data.admin.password,
        role=Rol.ADMINISTRADOR_COMERCIAL,
        org_id=org.id,
    )
    db.commit()
    return org


def list_all(db: Session) -> list[Organization]:
    return list(db.scalars(select(Organization).order_by(Organization.name)))


def update_org(db: Session, org_id: int, name: str | None, active: bool | None) -> Organization:
    org = db.get(Organization, org_id, with_for_update=True)
    if org is None:
        raise not_found("La organización")
    if name is not None:
        org.name = name
    if active is not None and active != org.active:
        org.active = active
        # Desactivar invalida de inmediato las sesiones de sus usuarios.
        db.execute(update(User).where(User.organization_id == org_id).values(token_version=User.token_version + 1))
    db.commit()
    return org
