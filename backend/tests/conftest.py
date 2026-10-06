"""Fixtures de pruebas.

Por defecto se usa SQLite en memoria (sin dependencias externas). Para ejecutar
contra PostgreSQL real:
    TEST_DATABASE_URL=postgresql+asyncpg://sgoc:sgoc@localhost:5432/sgoc_test pytest
"""

import os

# Debe ejecutarse antes de importar `app`: la configuración se lee al importar.
os.environ["ENVIRONMENT"] = "test"
os.environ["JWT_SECRET_KEY"] = "test-secret-key-that-is-long-enough-for-hs256-signing"
os.environ["BCRYPT_ROUNDS"] = "4"

from collections.abc import AsyncIterator
from typing import Any

import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)
from sqlalchemy.pool import StaticPool

from app.core.database import get_db_session
from app.main import app
from app.models import Base, User
from app.schemas.user import UserCreate
from app.services.user_service import create_user

TEST_DATABASE_URL = os.environ.get("TEST_DATABASE_URL", "sqlite+aiosqlite://")
USER_PASSWORD = "S3gura!Password"


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


@pytest.fixture
async def engine() -> AsyncIterator[AsyncEngine]:
    engine_kwargs: dict[str, Any] = {}
    if TEST_DATABASE_URL.startswith("sqlite"):
        # Una única conexión compartida para que la BD en memoria persista entre sesiones.
        engine_kwargs = {"poolclass": StaticPool, "connect_args": {"check_same_thread": False}}
    test_engine = create_async_engine(TEST_DATABASE_URL, **engine_kwargs)

    async with test_engine.begin() as connection:
        await connection.run_sync(Base.metadata.drop_all)
        await connection.run_sync(Base.metadata.create_all)
    yield test_engine
    async with test_engine.begin() as connection:
        await connection.run_sync(Base.metadata.drop_all)
    await test_engine.dispose()


@pytest.fixture
def session_factory(engine: AsyncEngine) -> async_sessionmaker[AsyncSession]:
    return async_sessionmaker(bind=engine, expire_on_commit=False, autoflush=False)


@pytest.fixture
async def client(
    session_factory: async_sessionmaker[AsyncSession],
) -> AsyncIterator[AsyncClient]:
    async def override_get_db_session() -> AsyncIterator[AsyncSession]:
        async with session_factory() as session:
            yield session

    app.dependency_overrides[get_db_session] = override_get_db_session
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as test_client:
        yield test_client
    app.dependency_overrides.clear()


@pytest.fixture
async def user(session_factory: async_sessionmaker[AsyncSession]) -> User:
    async with session_factory() as session:
        return await create_user(
            session,
            UserCreate(
                email="Ana.Compras@Example.com", full_name="Ana Compras", password=USER_PASSWORD
            ),
        )


@pytest.fixture
async def inactive_user(session_factory: async_sessionmaker[AsyncSession]) -> User:
    async with session_factory() as session:
        created = await create_user(
            session,
            UserCreate(
                email="baja@example.com", full_name="Usuario de Baja", password=USER_PASSWORD
            ),
        )
        created.is_active = False
        await session.commit()
        return created
