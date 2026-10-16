-- Datos personales normalizados: nombres y apellidos por separado.
--
-- Compatibilidad con las cuentas existentes: `nombre` (texto libre) NO se parte en nombres y
-- apellidos, porque no puede hacerse de forma fiable ("María del Pilar De la Torre"). Esas cuentas
-- quedan con nombres y apellidos nulos y conservan su nombre completo en `nombre`, que sigue siendo
-- el nombre visible; cada persona completa o corrige sus datos desde su perfil.
-- Para las cuentas nuevas la aplicación exige nombres y apellidos, y deriva `nombre` de ellos.
-- No se borra ni se modifica ningún dato.

ALTER TABLE usuarios ADD COLUMN nombres VARCHAR(100);
ALTER TABLE usuarios ADD COLUMN apellidos VARCHAR(100);

-- Cabe el nombre visible derivado: nombres (100) + espacio + apellidos (100).
ALTER TABLE usuarios ALTER COLUMN nombre SET DATA TYPE VARCHAR(201);

-- Unos apellidos sin nombres no tienen sentido.
ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_nombres CHECK (apellidos IS NULL OR nombres IS NOT NULL);
