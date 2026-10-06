"""Motor y fábrica de sesiones asíncronas de SQLAlchemy."""

from collections.abc import AsyncIterator

from sqlalchemy.ext.asyncio import (
    AsyncEngine,
    AsyncSession,
    async_sessionmaker,
    create_async_engine,
)

from app.core.config import settings

engine: AsyncEngine = create_async_engine(
    settings.DATABASE_URL,
    echo=settings.DATABASE_ECHO,
    pool_pre_ping=True,
)

SessionLocal: async_sessionmaker[AsyncSession] = async_sessionmaker(
    bind=engine,
    expire_on_commit=False,
    autoflush=False,
)


async def get_db_session() -> AsyncIterator[AsyncSession]:
    """Dependencia de FastAPI: una sesión por petición, cerrada al finalizar.

    Las operaciones de escritura confirman explícitamente en la capa de servicios;
    cualquier transacción pendiente se revierte al cerrar la sesión.
    """
    async with SessionLocal() as session:
        yield session
