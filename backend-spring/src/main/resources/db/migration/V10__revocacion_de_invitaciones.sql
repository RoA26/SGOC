-- Invitaciones: revocación auditada y aislamiento de quien las canjea.
--
-- Revocar es una baja lógica: la fila se conserva (quién la creó, quién la revocó y cuándo) y el
-- código deja de aceptarse. Nada se borra; las invitaciones existentes quedan sin revocar.

ALTER TABLE codigos_invitacion ADD COLUMN fecha_revocacion TIMESTAMP WITH TIME ZONE;
ALTER TABLE codigos_invitacion ADD COLUMN revocado_por_id BIGINT;

ALTER TABLE codigos_invitacion ADD CONSTRAINT fk_codigos_invitacion_revocado_por
    FOREIGN KEY (revocado_por_id) REFERENCES usuarios (id);

-- Revocada siempre registra quién y cuándo; una invitación usada no puede estar revocada.
ALTER TABLE codigos_invitacion ADD CONSTRAINT ck_codigos_invitacion_revocacion CHECK (
    (fecha_revocacion IS NULL AND revocado_por_id IS NULL)
    OR (fecha_revocacion IS NOT NULL AND revocado_por_id IS NOT NULL AND usado = FALSE));

-- Quien canjea una invitación pertenece a la empresa de la invitación (FK compuesta, como las de
-- productos y solicitudes). Con usado_por_id nulo (sin canjear) no se comprueba.
ALTER TABLE codigos_invitacion ADD CONSTRAINT fk_codigos_invitacion_usado_por_empresa
    FOREIGN KEY (empresa_id, usado_por_id) REFERENCES usuarios (empresa_id, id);

-- Listado del administrador: las de su empresa, las más recientes primero.
CREATE INDEX ix_codigos_invitacion_empresa_creado ON codigos_invitacion (empresa_id, creado_en);
