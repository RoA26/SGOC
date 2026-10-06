"""Dependencias compartidas por los routers."""

import uuid
from typing import Annotated

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.database import get_db_session
from app.core.security import InvalidTokenError, decode_access_token
from app.models.user import User
from app.services.user_service import get_user_by_id

SessionDep = Annotated[AsyncSession, Depends(get_db_session)]

# auto_error=False: gestionamos nosotros la ausencia de token para devolver un 401 coherente.
bearer_scheme = HTTPBearer(
    auto_error=False,
    description="Token JWT obtenido en `POST /api/auth/login`.",
)


def _unauthorized(detail: str) -> HTTPException:
    return HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail=detail,
        headers={"WWW-Authenticate": "Bearer"},
    )


async def get_current_user(
    session: SessionDep,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer_scheme)],
) -> User:
    """Resuelve el usuario a partir del header `Authorization: Bearer <jwt>`."""
    if credentials is None:
        raise _unauthorized("No autenticado.")

    try:
        payload = decode_access_token(credentials.credentials)
        user_id = uuid.UUID(payload.sub)
    except InvalidTokenError as exc:
        raise _unauthorized(str(exc)) from exc
    except ValueError as exc:
        raise _unauthorized("El token no es válido.") from exc

    user = await get_user_by_id(session, user_id)
    if user is None:
        raise _unauthorized("El usuario del token ya no existe.")
    if not user.is_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="La cuenta de usuario está deshabilitada.",
        )
    return user


CurrentUser = Annotated[User, Depends(get_current_user)]
