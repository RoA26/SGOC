-- Gestión centralizada de empresas: código empresarial estable y legible (p. ej. NASC01).
--
-- Identifica a la organización en la plataforma: es público, único y no cambia. No es una
-- credencial ni sustituye a empresas.codigo_empresa, el código aleatorio (secreto) con el que
-- los trabajadores se registran, que se conserva tal cual.
--
-- Nada se borra: solo se añade una columna y se rellena para las empresas existentes.

ALTER TABLE empresas ADD COLUMN codigo VARCHAR(12);

-- Empresas existentes: "EMP" + su id con al menos tres cifras (EMP001, EMP042, EMP1234).
-- Determinista y único porque el id lo es; no colisiona con los códigos que propone la
-- aplicación más adelante, porque esta los comprueba siempre contra la BD.
UPDATE empresas
SET codigo = 'EMP' || LPAD(CAST(id AS VARCHAR(10)), GREATEST(3, LENGTH(CAST(id AS VARCHAR(10)))), '0')
WHERE codigo IS NULL;

ALTER TABLE empresas ALTER COLUMN codigo SET NOT NULL;
ALTER TABLE empresas ADD CONSTRAINT uq_empresas_codigo UNIQUE (codigo);
-- Mismo formato que Patrones.CODIGO_EMPRESARIAL: de 3 a 12 letras mayúsculas o dígitos,
-- empezando por letra. regexp_like existe en PostgreSQL 15+ y en H2.
ALTER TABLE empresas ADD CONSTRAINT ck_empresas_codigo CHECK (REGEXP_LIKE(codigo, '^[A-Z][A-Z0-9]{2,11}$'));
