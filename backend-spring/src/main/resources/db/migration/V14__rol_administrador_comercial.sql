-- Módulo comercial: el administrador de cada empresa pasa de ADMIN_EMPRESA a
-- ADMINISTRADOR_COMERCIAL. Es el mismo rol con su nombre definitivo (no se crea un cuarto rol ni
-- conviven dos equivalentes). SUPER_ADMIN y TRABAJADOR no cambian.
--
-- Solo se traduce el valor: ninguna cuenta pierde su contraseña, su empresa, su estado ni sus
-- relaciones (las FK apuntan al id del usuario, no al rol). Los JWT ya emitidos siguen siendo
-- válidos y reciben los permisos nuevos: el filtro JWT lee el rol de la BD en cada petición, el
-- claim "rol" del token es solo informativo para la interfaz.
-- Requiere desplegar a la vez el backend que conoce el valor nuevo (Flyway migra al arrancar).
-- Vuelta atrás: el UPDATE inverso y la restricción anterior.

ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_rol;

UPDATE usuarios SET rol = 'ADMINISTRADOR_COMERCIAL' WHERE rol = 'ADMIN_EMPRESA';

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_rol
    CHECK (rol IN ('SUPER_ADMIN', 'ADMINISTRADOR_COMERCIAL', 'TRABAJADOR'));
