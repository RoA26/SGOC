from fastapi import APIRouter, HTTPException, status

from app.api.deps import CurrentUser, SessionDep
from app.schemas.auth import LoginRequest, TokenResponse
from app.schemas.common import ErrorResponse
from app.schemas.user import UserRead
from app.services.auth_service import (
    InactiveUserError,
    InvalidCredentialsError,
    authenticate_user,
    issue_access_token,
)

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post(
    "/login",
    response_model=TokenResponse,
    summary="Iniciar sesión",
    responses={
        status.HTTP_401_UNAUTHORIZED: {
            "model": ErrorResponse,
            "description": "Credenciales inválidas",
        },
        status.HTTP_403_FORBIDDEN: {"model": ErrorResponse, "description": "Cuenta deshabilitada"},
    },
)
async def login(credentials: LoginRequest, session: SessionDep) -> TokenResponse:
    """Intercambia correo y contraseña por un token de acceso JWT."""
    try:
        user = await authenticate_user(
            session, credentials.email, credentials.password.get_secret_value()
        )
    except InvalidCredentialsError as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Correo o contraseña incorrectos.",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
    except InactiveUserError as exc:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="La cuenta de usuario está deshabilitada.",
        ) from exc

    return issue_access_token(user)


@router.get(
    "/me",
    response_model=UserRead,
    summary="Usuario autenticado",
    responses={status.HTTP_401_UNAUTHORIZED: {"model": ErrorResponse}},
)
async def read_current_user(current_user: CurrentUser) -> UserRead:
    """Devuelve el perfil del usuario dueño del token. Útil para validar sesiones."""
    return UserRead.model_validate(current_user)
