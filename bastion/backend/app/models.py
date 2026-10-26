"""Importa todos los modelos para que Alembic y SQLAlchemy conozcan el metadata completo."""

from app.modules.catalog.models import Category, Product
from app.modules.inventory.models import Stock, Warehouse
from app.modules.movements.models import Movement
from app.modules.organizations.models import Organization
from app.modules.users.models import User

__all__ = ["Category", "Movement", "Organization", "Product", "Stock", "User", "Warehouse"]
