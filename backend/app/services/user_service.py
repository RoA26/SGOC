"""Acceso a datos y reglas de negocio de usuarios."""

import uuid

from anyio import to_thread
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession

from app.core.security import hash_password
from app.models.user import User
from app.schemas.user import UserCreate


class UserAlreadyExistsError(Exception):
    def __init__(self, email: str) -> None:
        super().__init__(f"Ya existe un usuario con el correo {email!r}.")
        self.email = email


def normalize_email(email: str) -> str:
    return email.strip().lower()


async def get_user_by_id(session: AsyncSession, user_id: uuid.UUID) -> User | None:
    return await session.get(User, user_id)


async def get_user_by_email(session: AsyncSession, email: str) -> User | None:
    statement = select(User).where(User.email == normalize_email(email))
    return await session.scalar(statement)


async def create_user(session: AsyncSession, data: UserCreate) -> User:
    email = normalize_email(data.email)
    # bcrypt es CPU-bound: se ejecuta en un hilo para no bloquear el event loop.
    hashed_password = await to_thread.run_sync(hash_password, data.password)

    user = User(email=email, full_name=data.full_name.strip(), hashed_password=hashed_password)
    session.add(user)
    try:
        await session.commit()
    except IntegrityError as exc:
        await session.rollback()
        raise UserAlreadyExistsError(email) from exc

    await session.refresh(user)
    return user
