from sqlalchemy import create_engine

import app.models  # noqa: F401  registra todos los modelos
from alembic import context
from app.core.config import get_settings
from app.core.db import Base

target_metadata = Base.metadata


def run_migrations_online() -> None:
    url = context.config.attributes.get("url") or get_settings().database_url
    engine = create_engine(url)
    with engine.connect() as conn:
        context.configure(connection=conn, target_metadata=target_metadata, compare_type=True)
        with context.begin_transaction():
            context.run_migrations()
    engine.dispose()


run_migrations_online()
