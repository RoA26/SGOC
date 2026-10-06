"""Punto de entrada de la API.

Desarrollo:  uvicorn app.main:app --reload
"""

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text
from sqlalchemy.exc import SQLAlchemyError

from app.api import auth
from app.api.deps import SessionDep
from app.core.config import settings
from app.core.database import engine
from app.schemas.common import ErrorResponse, HealthResponse


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    yield
    # Cierra limpiamente el pool de conexiones al apagar el servidor.
    await engine.dispose()


def create_app() -> FastAPI:
    app = FastAPI(
        title=settings.PROJECT_NAME,
        version=settings.VERSION,
        lifespan=lifespan,
    )

    app.add_middleware(
        CORSMiddleware,
        allow_origins=settings.CORS_ORIGINS,
        # La autenticación viaja en el header Authorization, no en cookies.
        allow_credentials=False,
        allow_methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
        allow_headers=["Authorization", "Content-Type"],
    )

    app.include_router(auth.router, prefix=settings.API_PREFIX)

    @app.get(
        f"{settings.API_PREFIX}/health",
        tags=["health"],
        summary="Estado del servicio",
        responses={status.HTTP_503_SERVICE_UNAVAILABLE: {"model": ErrorResponse}},
    )
    async def health_check(session: SessionDep) -> HealthResponse:
        try:
            await session.execute(text("SELECT 1"))
        except SQLAlchemyError as exc:
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="Base de datos no disponible.",
            ) from exc
        return HealthResponse(status="ok", database="ok")

    return app


app = create_app()
