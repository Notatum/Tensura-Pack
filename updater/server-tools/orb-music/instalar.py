"""Instala la herramienta administrativa fuera del pack y de sus carpetas de juego."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import sys

BASE = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description="Instala Agregar música de YouTube en el servidor Tensura.")
    parser.add_argument("--servidor", type=Path, required=True)
    parser.add_argument("--node", type=Path, help="Node.js 22+ existente; no se modifica su instalación")
    parser.add_argument("--actualizar", action="store_true", help="Actualiza componentes desde PyPI en el entorno privado")
    args = parser.parse_args()
    root = args.servidor.resolve(strict=True)
    if not (root / "server.properties").is_file():
        raise SystemExit("La carpeta no contiene server.properties. No se instaló nada.")
    for part in (root / "herramientas", root / "herramientas/musica"):
        if part.is_symlink() or (hasattr(part, "is_junction") and part.is_junction()):
            raise SystemExit("La carpeta de herramientas es un enlace; se conserva sin instalar.")
        part.mkdir(exist_ok=True)
        if not part.resolve().is_relative_to(root):
            raise SystemExit("Ruta de herramientas fuera del servidor.")
    folder = root / "herramientas/musica"
    node = args.node or shutil.which("node")
    if not node or not Path(node).is_file():
        raise SystemExit("Indicá Node.js 22+ con --node. No se descarga ni modifica otro runtime global.")
    node = Path(node).resolve()
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    version = subprocess.check_output([str(node), "--version"], text=True, creationflags=flags).strip()
    if int(version.lstrip("v").split(".")[0]) < 22:
        raise SystemExit("Node.js debe ser 22 o posterior.")
    runtime = folder / "runtime"
    if runtime.is_symlink() or (hasattr(runtime, "is_junction") and runtime.is_junction()):
        raise SystemExit("El entorno Python es un enlace; no se modifica.")
    python = runtime / ("Scripts/python.exe" if os.name == "nt" else "bin/python")
    if not python.exists():
        subprocess.run([sys.executable, "-m", "venv", str(runtime)], check=True)
    command = [str(python), "-m", "pip", "install", "--only-binary=:all:", "--disable-pip-version-check"]
    if args.actualizar:
        command += ["--upgrade", "yt-dlp[default]", "imageio-ffmpeg"]
    else:
        command += ["-r", str(BASE / "requirements.txt")]
    subprocess.run(command, check=True, creationflags=flags)
    for name in ("descargar_musica.py", "instalar.py", "requirements.txt", "LEEME.md"):
        source, target = BASE / name, folder / name
        if target.is_symlink():
            raise SystemExit("Se conserva un archivo de herramienta que es un enlace: " + name)
        if source.resolve() != target.resolve():
            shutil.copy2(source, target)
    config = folder / "config.json"
    if config.is_symlink():
        raise SystemExit("Se conserva la configuración que es un enlace.")
    config.write_text(json.dumps({"node": str(node)}, indent=2)+"\n", encoding="utf-8")
    batch = root / "Agregar-Musica-YouTube.bat"
    if batch.is_symlink():
        raise SystemExit("El acceso es un enlace; no se reemplaza.")
    batch.write_bytes(('''@echo off
setlocal
chcp 65001 >nul
title Musica del servidor - YouTube
if not exist "%~dp0herramientas\\musica\\runtime\\Scripts\\python.exe" (
  echo Falta el entorno de la herramienta. Volve a ejecutar instalar.py.
  pause
  exit /b 1
)
"%~dp0herramientas\\musica\\runtime\\Scripts\\python.exe" -B "%~dp0herramientas\\musica\\descargar_musica.py" --servidor "%~dp0."
echo.
pause
''').replace("\n", "\r\n").encode("ascii"))
    result = subprocess.run([str(python), "-B", str(folder / "descargar_musica.py"), "--servidor", str(root), "--comprobar"], check=True, text=True, encoding="utf-8", capture_output=True, creationflags=flags)
    print(result.stdout)
    (folder / "componentes.json").write_text(result.stdout, encoding="utf-8")
    print("Herramienta instalada. Acceso: " + str(batch))


if __name__ == "__main__":
    main()
