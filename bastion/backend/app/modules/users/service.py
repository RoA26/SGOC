from sqlalchemy import func, select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.errors import DomainError, conflict, not_found
from app.core.roles import Rol
from app.core.security import hash_password
from app.modules.users.models import User


def email_taken(db: Session, email: str) -> bool:
    return db.scalar(select(func.count()).select_from(User).where(func.lower(User.email) == email.lower())) != 0


def new_user(db: Session, *, email: str, full_name: str, password: str, role: Rol, org_id: int | None) -> User:
    """Crea el usuario dentro de la transacción en curso (sin commit)."""
    if email_taken(db, email):
        raise conflict("correo_duplicado", "Ya existe un usuario con ese correo")
    user = User(
        email=email.lower(),
        full_name=full_name,
        password_hash=hash_password(password),
        role=role.value,
        organization_id=org_id,
    )
    db.add(user)
    try:
        db.flush()
    except IntegrityError:
        db.rollback()
        raise conflict("correo_duplicado", "Ya existe un usuario con ese correo") from None
    return user


def list_org_users(db: Session, org_id: int) -> list[User]:
    return list(db.scalars(select(User).where(User.organization_id == org_id).order_by(User.full_name)))


def get_org_user(db: Session, org_id: int, user_id: int) -> User:
    user = db.scalar(select(User).where(User.organization_id == org_id, User.id == user_id))
    if user is None:
        raise not_found("El usuario")
    return user


def update_org_user(
    db: Session, org_id: int, actor_id: int, user_id: int, full_name: str | None, active: bool | None
) -> User:
    user = get_org_user(db, org_id, user_id)
    if user.role != Rol.TRABAJADOR:
        # Los administradores de la organización los gestiona SUPER_ADMIN.
        raise DomainError(403, "prohibido", "Solo puede gestionar trabajadores")
    if user.id == actor_id:
        raise DomainError(403, "prohibido", "No puede modificar su propia cuenta desde aquí")
    if full_name is not None:
        user.full_name = full_name
    if active is not None and active != user.active:
        user.active = active
        user.token_version += 1
    db.commit()
    return user
