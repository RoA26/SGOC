-- Hito 2 (identidad y onboarding): estado de cada usuario y código permanente de empresa.
--
-- Nada se borra: el booleano usuarios.activo se conserva como reflejo de estado = 'ACTIVO'
-- (una restricción impide que diverjan) y la columna reservada en V5 para unirse a una
-- empresa pasa a ser su código permanente.

-- ---------------------------------------------------------------------------
-- 1) usuarios.estado: PENDIENTE (se registró con el código de empresa y espera al
--    gerente), ACTIVO, RECHAZADO (el gerente no lo aprobó) o INACTIVO (perdió el acceso).
--    Los usuarios existentes conservan su situación: activo → ACTIVO, inactivo → INACTIVO.
-- ---------------------------------------------------------------------------
ALTER TABLE usuarios ADD COLUMN estado VARCHAR(20);
UPDATE usuarios SET estado = CASE WHEN activo THEN 'ACTIVO' ELSE 'INACTIVO' END;
ALTER TABLE usuarios ALTER COLUMN estado SET NOT NULL;
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_estado
    CHECK (estado IN ('PENDIENTE', 'ACTIVO', 'RECHAZADO', 'INACTIVO'));

-- activo deja de ser una fuente de verdad: solo refleja el estado.
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_estado_activo CHECK (activo = (estado = 'ACTIVO'));

-- Un alta que no indique nada queda sin acceso (la aplicación siempre fija ambos).
ALTER TABLE usuarios ALTER COLUMN estado SET DEFAULT 'PENDIENTE';
ALTER TABLE usuarios ALTER COLUMN activo SET DEFAULT FALSE;

-- ---------------------------------------------------------------------------
-- 2) empresas.codigo_empresa: código permanente con el que los trabajadores se registran
--    en su empresa. Reutiliza la columna reservada en V5 (sin uso hasta ahora) y su UNIQUE.
--    No es un código de invitación: no caduca ni se consume.
-- ---------------------------------------------------------------------------
ALTER TABLE empresas RENAME COLUMN codigo_invitacion_actual TO codigo_empresa;
ALTER TABLE empresas RENAME CONSTRAINT uq_empresas_codigo_invitacion TO uq_empresas_codigo_empresa;

-- Código para las empresas existentes, en el formato de la aplicación (XXXX-XXXX-XXXX-XXXX
-- con el alfabeto Base32 de Crockford, como CodigosInvitacion). SQL válido en PostgreSQL y H2.
UPDATE empresas
SET codigo_empresa =
           SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || '-'
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || '-'
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || '-'
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
        || SUBSTRING('0123456789ABCDEFGHJKMNPQRSTVWXYZ', CAST(FLOOR(RANDOM() * 32) AS INTEGER) + 1, 1)
WHERE codigo_empresa IS NULL;

ALTER TABLE empresas ALTER COLUMN codigo_empresa SET NOT NULL;
