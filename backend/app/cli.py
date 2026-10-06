"""Comandos de administración.

Crear un usuario (la contraseña se pide de forma interactiva):
    python -m app.cli create-user --email admin@example.com --full-name "Administrador"

En entornos no interactivos (CI, contenedores) la contraseña puede leerse de stdin:
    echo "$ADMIN_PASSWORD" | python -m app.cli create-user \\
        --email admin@example.com --full-name "Administrador" --password-stdin
"""

import argparse
import asyncio
import getpass
import sys

from pydantic import ValidationError

from app.core.database import SessionLocal, engine
from app.schemas.user import UserCreate
from app.services.user_service import UserAlreadyExistsError, create_user


def _read_password(from_stdin: bool) -> str:
    if from_stdin:
        return sys.stdin.readline().rstrip("\r\n")

    password = getpass.getpass("Contraseña: ")
    if password != getpass.getpass("Repite la contraseña: "):
        raise SystemExit("Error: las contraseñas no coinciden.")
    return password


async def _create_user(data: UserCreate) -> None:
    try:
        async with SessionLocal() as session:
            user = await create_user(session, data)
    finally:
        await engine.dispose()
    print(f"Usuario creado: {user.email} (id={user.id})")


def _handle_create_user(args: argparse.Namespace) -> None:
    password = _read_password(args.password_stdin)
    try:
        data = UserCreate(email=args.email, full_name=args.full_name, password=password)
    except ValidationError as exc:
        messages = "\n".join(
            f"  - {'.'.join(map(str, error['loc']))}: {error['msg']}" for error in exc.errors()
        )
        raise SystemExit(f"Error: datos inválidos:\n{messages}") from exc

    try:
        asyncio.run(_create_user(data))
    except UserAlreadyExistsError as exc:
        raise SystemExit(f"Error: {exc}") from exc


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(prog="python -m app.cli", description=__doc__.splitlines()[0])
    subparsers = parser.add_subparsers(dest="command", required=True)

    create = subparsers.add_parser("create-user", help="Crea un usuario con acceso al sistema.")
    create.add_argument("--email", required=True)
    create.add_argument("--full-name", required=True)
    create.add_argument(
        "--password-stdin",
        action="store_true",
        help="Lee la contraseña de la primera línea de stdin en lugar de pedirla.",
    )
    create.set_defaults(handler=_handle_create_user)

    args = parser.parse_args(argv)
    args.handler(args)


if __name__ == "__main__":
    main()
