#!/usr/bin/env bash
set -euo pipefail
here="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
if [ "$#" -eq 0 ]; then
  echo 'Uso: bash Iniciar-Tensura-Con-Update.sh <comando original de inicio del servidor>' >&2
  exit 2
fi
exec "${JAVA_BIN:-java}" -jar "$here/Actualizador-Tensura-Servidor.jar" --server-dir "$here" -- "$@"
