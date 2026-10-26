"""Errores en formato Problem Details (RFC 9457)."""

import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from starlette.exceptions import HTTPException as StarletteHTTPException

log = logging.getLogger("bastion")


class DomainError(Exception):
    def __init__(self, status: int, code: str, detail: str) -> None:
        super().__init__(detail)
        self.status, self.code, self.detail = status, code, detail


def not_found(what: str) -> DomainError:
    return DomainError(404, "no_encontrado", f"{what} no existe")


def forbidden(detail: str = "No tiene permiso para esta operación") -> DomainError:
    return DomainError(403, "prohibido", detail)


def conflict(code: str, detail: str) -> DomainError:
    return DomainError(409, code, detail)


def _problem(status: int, code: str, detail: str, **extra: object) -> JSONResponse:
    body = {"type": f"https://bastion/errores/{code}", "title": code, "status": status, "detail": detail, "code": code}
    body.update(extra)
    return JSONResponse(body, status_code=status, media_type="application/problem+json")


def install(app: FastAPI) -> None:
    @app.exception_handler(DomainError)
    async def _domain(_: Request, exc: DomainError) -> JSONResponse:
        return _problem(exc.status, exc.code, exc.detail)

    @app.exception_handler(StarletteHTTPException)
    async def _http(_: Request, exc: StarletteHTTPException) -> JSONResponse:
        code = {401: "no_autenticado", 403: "prohibido", 404: "no_encontrado", 405: "metodo_no_permitido"}.get(
            exc.status_code, "error_http"
        )
        resp = _problem(exc.status_code, code, str(exc.detail))
        if exc.headers:
            resp.headers.update(exc.headers)
        return resp

    @app.exception_handler(RequestValidationError)
    async def _validation(_: Request, exc: RequestValidationError) -> JSONResponse:
        errores = [{"campo": ".".join(str(p) for p in e["loc"][1:]), "mensaje": e["msg"]} for e in exc.errors()]
        return _problem(422, "validacion", "Datos de entrada inválidos", errores=errores)

    @app.exception_handler(Exception)
    async def _unexpected(_: Request, exc: Exception) -> JSONResponse:
        log.exception("Error no controlado: %s", type(exc).__name__)
        return _problem(500, "error_interno", "Error interno del servidor")
