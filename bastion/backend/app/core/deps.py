"""Autenticación y autorización. El rol, el estado y la organización se leen de la BD en cada petición."""

from collections.abc import Callable
from dataclasses import dataclass
from typing import Annotated

import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.db import get_db
from app.core.roles import Rol
from app.core.security import decode_token
from app.modules.organizations.models import Organization
from app.modules.users.models import User

_bearer = HTTPBearer(auto_error=False)
DB = Annotated[Session, Depends(get_db)]


def _unauthorized(detail: str = "Sesión inválida o expirada") -> HTTPException:
    return HTTPException(401, detail, headers={"WWW-Authenticate": "Bearer"})


@dataclass(frozen=True)
class Actor:
    user: User

    @property
    def id(self) -> int:
        return self.user.id

    @property
    def role(self) -> Rol:
        return Rol(self.user.role)

    @property
    def org_id(self) -> int:
        """Organización del actor. Solo válida para roles de empresa (garantizado por require_org)."""
        assert self.user.organization_id is not None
        return self.user.organization_id


def current_actor(db: DB, cred: Annotated[HTTPAuthorizationCredentials | None, Depends(_bearer)]) -> Actor:
    if cred is None:
        raise _unauthorized("Se requiere autenticación")
    try:
        claims = decode_token(cred.credentials)
        user_id = int(str(claims["sub"]))
        tv = int(str(claims["tv"]))
    except (jwt.PyJWTError, ValueError, KeyError):
        raise _unauthorized() from None
    user = db.get(User, user_id)
    if user is None or not user.active or user.token_version != tv:
        raise _unauthorized()
    if user.organization_id is not None:
        org_active = db.scalar(select(Organization.active).where(Organization.id == user.organization_id))
        if not org_active:
            raise _unauthorized("La organización está desactivada")
    return Actor(user)


CurrentActor = Annotated[Actor, Depends(current_actor)]


def require(*roles: Rol) -> Callable[[Actor], Actor]:
    def dep(actor: CurrentActor) -> Actor:
        if actor.role not in roles:
            raise HTTPException(403, "No tiene permiso para esta operación")
        return actor

    return dep


SuperAdmin = Annotated[Actor, Depends(require(Rol.SUPER_ADMIN))]
OrgAdmin = Annotated[Actor, Depends(require(Rol.ADMINISTRADOR_COMERCIAL))]
# Cualquier miembro de una organización. SUPER_ADMIN no opera datos de negocio en esta fase.
OrgMember = Annotated[Actor, Depends(require(Rol.ADMINISTRADOR_COMERCIAL, Rol.TRABAJADOR))]
