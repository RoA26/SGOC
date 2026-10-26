import logging

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import text

from app.core import errors
from app.core.config import get_settings
from app.core.db import get_engine
from app.modules.auth.router import router as auth_router
from app.modules.catalog.router import router as catalog_router
from app.modules.inventory.router import router as inventory_router
from app.modules.movements.router import router as movements_router
from app.modules.organizations.router import router as organizations_router
from app.modules.users.router import router as users_router

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


def create_app() -> FastAPI:
    settings = get_settings()
    docs = settings.env != "production"
    app = FastAPI(
        title="Bastión API",
        version="0.1.0",
        docs_url="/api/docs" if docs else None,
        openapi_url="/api/openapi.json" if docs else None,
        redoc_url=None,
    )
    errors.install(app)
    if settings.cors_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=settings.cors_origins,
            allow_methods=["*"],
            allow_headers=["Authorization", "Content-Type", "Idempotency-Key"],
        )
    for r in (auth_router, organizations_router, users_router, catalog_router, inventory_router, movements_router):
        app.include_router(r, prefix="/api")

    @app.get("/api/health", tags=["health"])
    def health() -> dict[str, str]:
        with get_engine().connect() as c:
            c.execute(text("SELECT 1"))
        return {"status": "ok"}

    return app


app = create_app()
