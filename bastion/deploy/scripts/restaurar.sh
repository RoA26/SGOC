#!/usr/bin/env sh
# Restaura un respaldo SOBRE la BD de Bastión (destructivo para los datos actuales de Bastión).
# Uso: deploy/scripts/restaurar.sh archivo.dump   — pide confirmación explícita.
set -eu
FILE="${1:?indique el archivo .dump}"
printf "Esto reemplaza los datos actuales de Bastión con %s. Escriba RESTAURAR para continuar: " "$FILE"
read -r ok
[ "$ok" = "RESTAURAR" ] || { echo "Cancelado."; exit 1; }
docker compose -p bastion stop backend
docker compose -p bastion exec -T postgres pg_restore -U bastion -d bastion --clean --if-exists --no-owner < "$FILE"
docker compose -p bastion start backend
echo "Restauración completada."
