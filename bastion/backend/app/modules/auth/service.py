import logging

from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app.core.errors import DomainError
from app.core.security import create_token, hash_password, verify_password
from app.modules.organizations.models import Organization
from app.modules.users.models import User

log = logging.getLogger("bastion.auth")


def login(db: Session, email: str, password: str) -> tuple[str, int]:
    user = db.scalar(select(User).where(func.lower(User.email) == email.lower()))
    ok = verify_password(user.password_hash if user else None, password)
    if user is None or not ok or not user.active:
        log.info("Inicio de sesión rechazado")
        raise DomainError(401, "credenciales_invalidas", "Correo o contraseña incorrectos")
    if user.organization_id is not None:
        org = db.get(Organization, user.organization_id)
        if org is None or not org.active:
            raise DomainError(401, "credenciales_invalidas", "Correo o contraseña incorrectos")
    return create_token(user.id, user.token_version)


def logout(db: Session, user: User) -> None:
    """Invalida todos los tokens del usuario (cierre de sesión en todos los dispositivos)."""
    db.execute(update(User).where(User.id == user.id).values(token_version=User.token_version + 1))
    db.commit()


def change_password(db: Session, user: User, current: str, new: str) -> tuple[str, int]:
    """Cambia la clave propia, invalida las demás sesiones y devuelve un token nuevo para esta."""
    if not verify_password(user.password_hash, current):
        raise DomainError(422, "clave_incorrecta", "La contraseña actual no es correcta")
    if current == new:
        raise DomainError(422, "clave_repetida", "La contraseña nueva debe ser distinta de la actual")
    user.password_hash = hash_password(new)
    user.token_version += 1
    db.commit()
    return create_token(user.id, user.token_version)
