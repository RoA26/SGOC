from fastapi.testclient import TestClient

from app import bootstrap
from tests.conftest import login


def test_bootstrap_de_un_solo_uso(client: TestClient) -> None:
    assert bootstrap.run("Jefe@Bastion.example.com", "Jefe", "una-clave-muy-larga") == 0
    assert bootstrap.run("otro@bastion.example.com", "Otro", "una-clave-muy-larga") == 1
    h = login(client, "jefe@bastion.example.com", "una-clave-muy-larga")
    assert client.get("/api/auth/me", headers=h).json()["role"] == "SUPER_ADMIN"


def test_bootstrap_exige_clave_larga() -> None:
    assert bootstrap.run("a@bastion.example.com", "A", "corta") == 2


def test_no_hay_registro_publico(client: TestClient) -> None:
    r = client.post("/api/auth/register", json={"email": "x@x.example.com", "password": "x", "role": "SUPER_ADMIN"})
    assert r.status_code in (404, 405)
    assert client.post("/api/users", json={"email": "x@x.example.com"}).status_code == 401


def test_cli_en_proceso_independiente() -> None:
    """Ejecuta el comando tal como se documenta, sin la aplicación web cargada."""
    import os
    import subprocess
    import sys

    env = {**os.environ, "BASTION_BOOTSTRAP_PASSWORD": "una-clave-muy-larga"}
    args = [sys.executable, "-m", "app.bootstrap", "--email", "cli@bastion.example.com", "--name", "CLI"]
    first = subprocess.run(args, env=env, capture_output=True, text=True, check=False)  # noqa: S603
    assert first.returncode == 0, first.stderr
    second = subprocess.run(args, env=env, capture_output=True, text=True, check=False)  # noqa: S603
    assert second.returncode == 1 and "Ya existe" in second.stderr
