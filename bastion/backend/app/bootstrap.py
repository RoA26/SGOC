"""Crea el primer SUPER_ADMIN. Uso único: se niega si ya existe alguno.

    BASTION_BOOTSTRAP_PASSWORD=... python -m app.bootstrap --email admin@ejemplo.com --name "Nombre"

Si BASTION_BOOTSTRAP_PASSWORD no está definida, la contraseña se pide por consola sin eco.
"""

import argparse
import getpass
import os
import sys

from sqlalchemy import func, select, text

import app.models  # noqa: F401  registra todos los modelos antes de usar el ORM
from app.core.db import get_sessionmaker
from app.core.roles import Rol
from app.modules.users.models import User
from app.modules.users.service import new_user

LOCK_ID = 0x0B4571  # cerrojo consultivo para evitar dos bootstraps simultáneos


def run(email: str, name: str, password: str) -> int:
    if len(password) < 12:
        print("La contraseña del SUPER_ADMIN debe tener al menos 12 caracteres.", file=sys.stderr)
        return 2
    with get_sessionmaker()() as db:
        db.execute(text("SELECT pg_advisory_xact_lock(:id)"), {"id": LOCK_ID})
        exists = db.scalar(select(func.count()).select_from(User).where(User.role == Rol.SUPER_ADMIN.value))
        if exists:
            print("Ya existe un SUPER_ADMIN: el bootstrap no se vuelve a ejecutar.", file=sys.stderr)
            return 1
        new_user(db, email=email, full_name=name, password=password, role=Rol.SUPER_ADMIN, org_id=None)
        db.commit()
    print(f"SUPER_ADMIN creado: {email.lower()}")
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--email", required=True)
    p.add_argument("--name", required=True)
    a = p.parse_args()
    pw = os.environ.get("BASTION_BOOTSTRAP_PASSWORD") or getpass.getpass("Contraseña: ")
    return run(a.email, a.name, pw)


if __name__ == "__main__":
    raise SystemExit(main())
