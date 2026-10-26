#!/usr/bin/env sh
# Respaldo lógico de la BD de Bastión. Uso: deploy/scripts/respaldo.sh [directorio]
# Solo lee: no detiene ni modifica ningún contenedor.
set -eu
DEST="${1:-./backups}"
mkdir -p "$DEST"
FILE="$DEST/bastion-$(date +%Y%m%d-%H%M%S).dump"
docker compose -p bastion exec -T postgres pg_dump -U bastion -d bastion --format=custom > "$FILE"
echo "Respaldo creado: $FILE ($(du -h "$FILE" | cut -f1))"
