-- Entrega D: código de barras de los productos.
--
-- Texto, no número: conserva los ceros a la izquierda (EAN-13, UPC-A) y admite simbologías
-- alfanuméricas (Code 128, Code 39). Opcional y único por empresa, también entre productos dados
-- de baja (como el SKU). Un solo código por producto; varios códigos (presentaciones, códigos del
-- proveedor) quedan como evolución futura en una tabla propia.

ALTER TABLE productos ADD COLUMN codigo_barras VARCHAR(64);
ALTER TABLE productos ADD CONSTRAINT uq_productos_codigo_barras UNIQUE (empresa_id, codigo_barras);
-- Mismo formato que Patrones.CODIGO_BARRAS: de 3 a 64 caracteres sin espacios.
ALTER TABLE productos ADD CONSTRAINT ck_productos_codigo_barras
    CHECK (codigo_barras IS NULL OR REGEXP_LIKE(codigo_barras, '^[0-9A-Za-z._/+-]{3,64}$'));
