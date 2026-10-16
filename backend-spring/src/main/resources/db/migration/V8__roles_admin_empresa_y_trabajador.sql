-- Normalización de roles: el administrador de una empresa pasa de GERENTE a ADMIN_EMPRESA y el
-- trabajador, de USUARIO a TRABAJADOR. SUPER_ADMIN no cambia.
--
-- Solo se traducen los valores: ninguna cuenta pierde su empresa, su estado ni su acceso. Los JWT
-- ya emitidos siguen siendo válidos (el rol se lee de la BD en cada petición, no del token).
-- Requiere desplegar a la vez el backend que conoce los nuevos valores (Flyway migra al arrancar).
-- Vuelta atrás: los dos UPDATE inversos y la restricción anterior.

ALTER TABLE usuarios DROP CONSTRAINT ck_usuarios_rol;

UPDATE usuarios SET rol = 'ADMIN_EMPRESA' WHERE rol = 'GERENTE';
UPDATE usuarios SET rol = 'TRABAJADOR' WHERE rol = 'USUARIO';

ALTER TABLE usuarios ADD CONSTRAINT ck_usuarios_rol CHECK (rol IN ('SUPER_ADMIN', 'ADMIN_EMPRESA', 'TRABAJADOR'));
