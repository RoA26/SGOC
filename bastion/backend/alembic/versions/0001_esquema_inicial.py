"""esquema inicial

Revision ID: 0001
Revises:
"""

import sqlalchemy as sa

from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "organizations",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=160), nullable=False),
        sa.Column("slug", sa.String(length=60), nullable=False),
        sa.Column("active", sa.Boolean(), server_default="true", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_organizations")),
        sa.UniqueConstraint("slug", name=op.f("uq_organizations_slug")),
    )
    op.create_table(
        "categories",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("organization_id", sa.Integer(), nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.ForeignKeyConstraint(
            ["organization_id"],
            ["organizations.id"],
            name=op.f("fk_categories_organization_id_organizations"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_categories")),
        sa.UniqueConstraint("organization_id", "id", name=op.f("uq_categories_organization_id_id")),
    )
    op.create_index(
        "uq_categories_org_name", "categories", ["organization_id", sa.literal_column("lower(name)")], unique=True
    )
    op.create_table(
        "users",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("organization_id", sa.Integer(), nullable=True),
        sa.Column("email", sa.String(length=254), nullable=False),
        sa.Column("full_name", sa.String(length=160), nullable=False),
        sa.Column("password_hash", sa.String(length=255), nullable=False),
        sa.Column("role", sa.String(length=32), nullable=False),
        sa.Column("active", sa.Boolean(), server_default="true", nullable=False),
        sa.Column("token_version", sa.Integer(), server_default="0", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint(
            "(role = 'SUPER_ADMIN' AND organization_id IS NULL) OR (role <> 'SUPER_ADMIN' AND organization_id IS NOT NULL)",
            name=op.f("ck_users_rol_organizacion"),
        ),
        sa.CheckConstraint(
            "role IN ('SUPER_ADMIN','ADMINISTRADOR_COMERCIAL','TRABAJADOR')", name=op.f("ck_users_rol_valido")
        ),
        sa.ForeignKeyConstraint(
            ["organization_id"],
            ["organizations.id"],
            name=op.f("fk_users_organization_id_organizations"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_users")),
        sa.UniqueConstraint("organization_id", "id", name=op.f("uq_users_organization_id_id")),
    )
    op.create_index(op.f("ix_users_organization_id"), "users", ["organization_id"], unique=False)
    op.create_index("uq_users_email_lower", "users", [sa.literal_column("lower(email)")], unique=True)
    op.create_table(
        "warehouses",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("organization_id", sa.Integer(), nullable=False),
        sa.Column("code", sa.String(length=30), nullable=False),
        sa.Column("name", sa.String(length=120), nullable=False),
        sa.Column("active", sa.Boolean(), server_default="true", nullable=False),
        sa.ForeignKeyConstraint(
            ["organization_id"],
            ["organizations.id"],
            name=op.f("fk_warehouses_organization_id_organizations"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_warehouses")),
        sa.UniqueConstraint("organization_id", "id", name=op.f("uq_warehouses_organization_id_id")),
    )
    op.create_index(op.f("ix_warehouses_organization_id"), "warehouses", ["organization_id"], unique=False)
    op.create_index(
        "uq_warehouses_org_code", "warehouses", ["organization_id", sa.literal_column("lower(code)")], unique=True
    )
    op.create_table(
        "products",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("organization_id", sa.Integer(), nullable=False),
        sa.Column("category_id", sa.Integer(), nullable=True),
        sa.Column("sku", sa.String(length=64), nullable=False),
        sa.Column("barcode", sa.String(length=64), nullable=True),
        sa.Column("name", sa.String(length=200), nullable=False),
        sa.Column("unit", sa.String(length=8), server_default="UND", nullable=False),
        sa.Column("min_stock", sa.Numeric(precision=14, scale=3), server_default="0", nullable=False),
        sa.Column("active", sa.Boolean(), server_default="true", nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint("unit IN ('UND', 'M', 'M2', 'KG', 'ROLLO', 'L')", name=op.f("ck_products_unidad_valida")),
        sa.CheckConstraint("min_stock >= 0", name=op.f("ck_products_min_stock_no_negativo")),
        sa.ForeignKeyConstraint(
            ["organization_id", "category_id"],
            ["categories.organization_id", "categories.id"],
            name=op.f("fk_products_organization_id_category_id_categories"),
        ),
        sa.ForeignKeyConstraint(
            ["organization_id"],
            ["organizations.id"],
            name=op.f("fk_products_organization_id_organizations"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_products")),
        sa.UniqueConstraint("organization_id", "id", name=op.f("uq_products_organization_id_id")),
        sa.UniqueConstraint("organization_id", "sku", name=op.f("uq_products_organization_id_sku")),
    )
    op.create_index(op.f("ix_products_organization_id"), "products", ["organization_id"], unique=False)
    op.create_index(
        "uq_products_org_barcode",
        "products",
        ["organization_id", "barcode"],
        unique=True,
        postgresql_where=sa.text("barcode IS NOT NULL"),
    )
    op.create_table(
        "movements",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("organization_id", sa.Integer(), nullable=False),
        sa.Column("product_id", sa.Integer(), nullable=False),
        sa.Column("warehouse_id", sa.Integer(), nullable=False),
        sa.Column("user_id", sa.Integer(), nullable=False),
        sa.Column("kind", sa.String(length=12), nullable=False),
        sa.Column("quantity", sa.Numeric(precision=14, scale=3), nullable=False),
        sa.Column("unit_cost", sa.Numeric(precision=18, scale=4), nullable=True),
        sa.Column("balance_after", sa.Numeric(precision=14, scale=3), nullable=False),
        sa.Column("avg_cost_after", sa.Numeric(precision=18, scale=4), nullable=False),
        sa.Column("reference", sa.String(length=120), nullable=True),
        sa.Column("notes", sa.String(length=500), nullable=True),
        sa.Column("reverses_id", sa.Integer(), nullable=True),
        sa.Column("idempotency_key", sa.String(length=80), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint(
            "(kind = 'REVERSION') = (reverses_id IS NOT NULL)", name=op.f("ck_movements_reversion_referencia")
        ),
        sa.CheckConstraint(
            "kind IN ('ENTRADA', 'SALIDA', 'AJUSTE', 'REVERSION')", name=op.f("ck_movements_tipo_valido")
        ),
        sa.CheckConstraint("kind NOT IN ('ENTRADA') OR quantity > 0", name=op.f("ck_movements_entrada_positiva")),
        sa.CheckConstraint("kind NOT IN ('SALIDA') OR quantity < 0", name=op.f("ck_movements_salida_negativa")),
        sa.CheckConstraint("balance_after >= 0", name=op.f("ck_movements_saldo_no_negativo")),
        sa.CheckConstraint("quantity <> 0", name=op.f("ck_movements_cantidad_no_cero")),
        sa.ForeignKeyConstraint(
            ["organization_id", "product_id"],
            ["products.organization_id", "products.id"],
            name=op.f("fk_movements_organization_id_product_id_products"),
        ),
        sa.ForeignKeyConstraint(
            ["organization_id", "reverses_id"],
            ["movements.organization_id", "movements.id"],
            name=op.f("fk_movements_organization_id_reverses_id_movements"),
        ),
        sa.ForeignKeyConstraint(
            ["organization_id", "user_id"],
            ["users.organization_id", "users.id"],
            name=op.f("fk_movements_organization_id_user_id_users"),
        ),
        sa.ForeignKeyConstraint(
            ["organization_id", "warehouse_id"],
            ["warehouses.organization_id", "warehouses.id"],
            name=op.f("fk_movements_organization_id_warehouse_id_warehouses"),
        ),
        sa.ForeignKeyConstraint(
            ["organization_id"],
            ["organizations.id"],
            name=op.f("fk_movements_organization_id_organizations"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_movements")),
        sa.UniqueConstraint("organization_id", "id", name=op.f("uq_movements_organization_id_id")),
    )
    op.create_index("ix_movements_org_created", "movements", ["organization_id", "created_at"], unique=False)
    op.create_index(
        "uq_movements_idempotency",
        "movements",
        ["organization_id", "idempotency_key"],
        unique=True,
        postgresql_where=sa.text("idempotency_key IS NOT NULL"),
    )
    op.create_index(
        "uq_movements_reverses",
        "movements",
        ["reverses_id"],
        unique=True,
        postgresql_where=sa.text("reverses_id IS NOT NULL"),
    )
    op.create_table(
        "stock",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("organization_id", sa.Integer(), nullable=False),
        sa.Column("product_id", sa.Integer(), nullable=False),
        sa.Column("warehouse_id", sa.Integer(), nullable=False),
        sa.Column("quantity", sa.Numeric(precision=14, scale=3), server_default="0", nullable=False),
        sa.Column("avg_cost", sa.Numeric(precision=18, scale=4), server_default="0", nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.text("now()"), nullable=False),
        sa.CheckConstraint("avg_cost >= 0", name=op.f("ck_stock_costo_no_negativo")),
        sa.CheckConstraint("quantity >= 0", name=op.f("ck_stock_saldo_no_negativo")),
        sa.ForeignKeyConstraint(
            ["organization_id", "product_id"],
            ["products.organization_id", "products.id"],
            name=op.f("fk_stock_organization_id_product_id_products"),
        ),
        sa.ForeignKeyConstraint(
            ["organization_id", "warehouse_id"],
            ["warehouses.organization_id", "warehouses.id"],
            name=op.f("fk_stock_organization_id_warehouse_id_warehouses"),
        ),
        sa.ForeignKeyConstraint(
            ["organization_id"],
            ["organizations.id"],
            name=op.f("fk_stock_organization_id_organizations"),
            ondelete="RESTRICT",
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("pk_stock")),
        sa.UniqueConstraint(
            "organization_id",
            "product_id",
            "warehouse_id",
            name=op.f("uq_stock_organization_id_product_id_warehouse_id"),
        ),
    )
    # El historial de movimientos es inmutable: las correcciones se hacen con REVERSION.
    op.execute("""
        CREATE FUNCTION movements_inmutables() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
            RAISE EXCEPTION 'Los movimientos confirmados no se modifican ni se borran';
        END $$;
    """)
    op.execute("""
        CREATE TRIGGER trg_movements_inmutables BEFORE UPDATE OR DELETE ON movements
        FOR EACH ROW EXECUTE FUNCTION movements_inmutables();
    """)


def downgrade() -> None:
    op.execute("DROP TRIGGER IF EXISTS trg_movements_inmutables ON movements")
    op.execute("DROP FUNCTION IF EXISTS movements_inmutables()")
    op.drop_table("stock")
    op.drop_index("uq_movements_reverses", table_name="movements", postgresql_where=sa.text("reverses_id IS NOT NULL"))
    op.drop_index(
        "uq_movements_idempotency", table_name="movements", postgresql_where=sa.text("idempotency_key IS NOT NULL")
    )
    op.drop_index("ix_movements_org_created", table_name="movements")
    op.drop_table("movements")
    op.drop_index("uq_products_org_barcode", table_name="products", postgresql_where=sa.text("barcode IS NOT NULL"))
    op.drop_index(op.f("ix_products_organization_id"), table_name="products")
    op.drop_table("products")
    op.drop_index("uq_warehouses_org_code", table_name="warehouses")
    op.drop_index(op.f("ix_warehouses_organization_id"), table_name="warehouses")
    op.drop_table("warehouses")
    op.drop_index("uq_users_email_lower", table_name="users")
    op.drop_index(op.f("ix_users_organization_id"), table_name="users")
    op.drop_table("users")
    op.drop_index("uq_categories_org_name", table_name="categories")
    op.drop_table("categories")
    op.drop_table("organizations")
