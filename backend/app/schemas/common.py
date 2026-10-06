from typing import Literal

from pydantic import BaseModel


class HealthResponse(BaseModel):
    status: Literal["ok"]
    database: Literal["ok"]


class ErrorResponse(BaseModel):
    """Formato de error estándar de FastAPI (`HTTPException`)."""

    detail: str
