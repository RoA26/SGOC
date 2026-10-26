"""costo promedio por producto (alineado con Poliway)

El costo promedio ponderado pasa de (producto, bodega) a producto, calculado sobre la
existencia total de la organización, como hace registrar_movimiento() en Poliway
(script 06). Ver docs/inventario.md.

Revision ID: 0002
Revises: 0001
"""

import sqlalchemy as sa

from alembic import op

revision = "0002"
down_revision = "0001"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column(
        "products", sa.Column("avg_cost", sa.Numeric(precision=18, scale=4), server_default="0", nullable=False)
    )
    op.create_check_constraint("costo_no_negativo", "products", "avg_cost >= 0")
    # Datos existentes: promedio de las bodegas ponderado por existencia (o el último costo si no hay).
    op.execute("""
        UPDATE products p SET avg_cost = round(coalesce(
            (SELECT sum(s.quantity * s.avg_cost) / nullif(sum(s.quantity), 0) FROM stock s
              WHERE s.organization_id = p.organization_id AND s.product_id = p.id),
            (SELECT max(s.avg_cost) FROM stock s
              WHERE s.organization_id = p.organization_id AND s.product_id = p.id),
            0), 4)
    """)
    op.drop_constraint(op.f("ck_stock_costo_no_negativo"), "stock", type_="check")
    op.drop_column("stock", "avg_cost")


def downgrade() -> None:
    op.add_column("stock", sa.Column("avg_cost", sa.Numeric(precision=18, scale=4), server_default="0", nullable=False))
    op.create_check_constraint("costo_no_negativo", "stock", "avg_cost >= 0")
    op.execute("""
        UPDATE stock s SET avg_cost = p.avg_cost FROM products p
         WHERE p.organization_id = s.organization_id AND p.id = s.product_id
    """)
    op.drop_constraint(op.f("ck_products_costo_no_negativo"), "products", type_="check")
    op.drop_column("products", "avg_cost")
