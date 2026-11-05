"""Importador local de YouTube para la biblioteca compartida del orbe."""
from __future__ import annotations

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import unicodedata
from urllib.parse import parse_qs, urlsplit

VERSION = "1.0.0"
MAX_BYTES = 64 * 1024 * 1024
MAX_SOURCE_BYTES = 256 * 1024 * 1024
MAX_TRACKS = 256
MAX_LINKS = 20
VIDEO_ID = re.compile(r"[A-Za-z0-9_-]{11}\Z")
HOSTS = {"youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com"}
EXTENSIONS = {".mp3", ".ogg", ".wav"}


class MusicError(Exception):
    pass


def canonical_url(value: str) -> tuple[str, str]:
    """Solo videos individuales; descarta tracking, playlists y tiempos de inicio."""
    value = value.strip()
    if VIDEO_ID.fullmatch(value):
        return value, "https://www.youtube.com/watch?v=" + value
    if any(ord(c) < 32 for c in value):
        raise MusicError("El enlace contiene caracteres inválidos.")
    if value.startswith(("www.youtube.com/", "youtube.com/", "youtu.be/", "music.youtube.com/")):
        value = "https://" + value
    try:
        url = urlsplit(value)
        if url.scheme not in {"http", "https"} or url.username or url.password or url.port not in {None, 80, 443}:
            raise ValueError()
        host = (url.hostname or "").lower()
        if host in {"youtu.be", "www.youtu.be"}:
            video = url.path.strip("/")
        elif host in HOSTS and url.path == "/watch":
            video = parse_qs(url.query).get("v", [""])[0]
        elif host in HOSTS and re.fullmatch(r"/(shorts|live|embed)/[A-Za-z0-9_-]{11}/?", url.path):
            video = url.path.split("/")[2]
        else:
            video = ""
    except ValueError:
        video = ""
    if not VIDEO_ID.fullmatch(video):
        raise MusicError("Usá un enlace de video de YouTube, YouTube Music o youtu.be. Las playlists completas no se importan.")
    return video, "https://www.youtube.com/watch?v=" + video


def filename(title: str, video: str) -> str:
    title = unicodedata.normalize("NFC", title)
    title = "".join(" " if c in '<>:"/\\|?*' or unicodedata.category(c).startswith("C") else c for c in title)
    title = re.sub(r"\s+", " ", title).strip(" .") or "Canción"
    # Cuenta UTF-16, igual que AudioRules; también mantiene rutas Windows cortas.
    title = title.encode("utf-16-le")[:280].decode("utf-16-le", errors="ignore").rstrip(" .")
    title = title.lstrip(".") or "Canción"
    if title.split(".", 1)[0].upper() in {"CON", "PRN", "AUX", "NUL", *("COM"+str(n) for n in range(1, 10)), *("LPT"+str(n) for n in range(1, 10))}:
        title = "_" + title
    return f"{title} [{video}].mp3"


def sha256(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def plain_error(error: object) -> str:
    # No registra URLs firmadas de los streams, cabeceras ni argumentos privados.
    text = re.sub(r"\x1b\[[0-9;]*m", "", str(error))
    text = re.sub(r"https?://[^\s<>]+", "[enlace remoto]", text)
    return text[-900:]


def linked(path: Path) -> bool:
    return path.is_symlink() or (hasattr(path, "is_junction") and path.is_junction())


def child_dir(base: Path, name: str) -> Path:
    path = base / name
    if linked(path):
        raise MusicError(f"La carpeta {name} es un enlace; se conserva sin escribir.")
    path.mkdir(exist_ok=True)
    if not path.is_dir() or path.resolve().parent != base.resolve():
        raise MusicError(f"Carpeta inválida: {name}")
    return path


def atomic_json(path: Path, data: object) -> None:
    if linked(path):
        raise MusicError("El registro de descargas es un enlace; se conserva.")
    descriptor, temporary = tempfile.mkstemp(dir=path.parent, suffix=".tmp")
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8", newline="\n") as stream:
            json.dump(data, stream, ensure_ascii=False, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        Path(temporary).unlink(missing_ok=True)


@contextmanager
def exclusive(path: Path):
    if linked(path):
        raise MusicError("El bloqueo de descargas es un enlace.")
    with path.open("a+b") as handle:
        handle.seek(0, 2)
        if not handle.tell():
            handle.write(b"0")
            handle.flush()
        handle.seek(0)
        try:
            if os.name == "nt":
                import msvcrt
                msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl
                fcntl.flock(handle, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError as error:
            raise MusicError("Ya hay otra importación de canciones en curso. Esperá a que termine.") from error
        try:
            yield
        finally:
            handle.seek(0)
            if os.name == "nt":
                msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(handle, fcntl.LOCK_UN)


class Library:
    def __init__(self, root: Path):
        self.root = root.resolve(strict=True)
        if not (self.root / "server.properties").is_file():
            raise MusicError("La carpeta elegida no contiene server.properties; no se modifica.")
        self.audio = child_dir(self.root, "orbe_audio_servidor")
        self.music = child_dir(self.audio, "musica")
        self.admin = child_dir(self.audio, "descargas")
        self.work = child_dir(self.admin, "temporales")
        self.index_path = self.admin / "catalogo-youtube.json"
        if linked(self.index_path):
            raise MusicError("El registro de descargas es un enlace.")
        self.index = json.loads(self.index_path.read_text(encoding="utf-8")) if self.index_path.exists() else {"schema": 1, "videos": {}}
        if self.index.get("schema") != 1 or not isinstance(self.index.get("videos"), dict):
            raise MusicError("El registro de descargas no tiene el formato esperado; se conserva.")

    def existing(self, video: str) -> Path | None:
        record = self.index["videos"].get(video)
        if not record:
            return None
        name = record.get("file", "")
        if not name or Path(name).name != name or any(c in name for c in "/\\:"):
            raise MusicError("El registro contiene un nombre de archivo inválido.")
        path = self.music / name
        if not path.exists():
            return None
        if linked(path) or not path.is_file() or sha256(path) != record.get("sha256"):
            raise MusicError("La copia existente fue modificada. Se conserva y no se sobrescribe: " + name)
        return path

    def capacity(self) -> int:
        return sum(1 for p in self.music.iterdir() if p.is_file() and not linked(p) and p.suffix.lower() in EXTENSIONS and not p.name.startswith("."))

    def publish(self, video: str, url: str, title: str, mp3: Path, duration: float, bitrate: int) -> dict:
        if self.capacity() >= MAX_TRACKS:
            raise MusicError("La biblioteca llegó al límite de 256 canciones. No se eliminó ninguna.")
        if linked(mp3) or not mp3.is_file() or not 0 < mp3.stat().st_size <= MAX_BYTES:
            raise MusicError("El audio terminado supera 64 MiB o es inválido; no se agrega al orbe.")
        digest = sha256(mp3)
        name = filename(title, video)
        target = self.music / name
        for number in range(1, 1000):
            try:
                # Publicación completa y sin reemplazar: el catálogo nunca ve un MP3 parcial.
                os.link(mp3, target, follow_symlinks=False)
                break
            except FileExistsError:
                target = self.music / (Path(name).stem + f" ({number}).mp3")
        else:
            raise MusicError("Hay demasiados nombres iguales; no se sobrescribió ningún archivo.")
        record = {"url": url, "title": title, "file": target.name, "sha256": digest,
                  "bytes": target.stat().st_size, "seconds": duration, "bitrateKbps": bitrate,
                  "addedUtc": datetime.now(timezone.utc).isoformat()}
        self.index["videos"][video] = record
        try:
            atomic_json(self.index_path, self.index)
        except Exception:
            # Deshacer solamente nuestra creación si no pudimos registrar el resultado.
            if target.resolve().parent == self.music.resolve() and not linked(target) and sha256(target) == digest:
                target.unlink()
            raise
        return {"status": "agregada", "id": video, **record}


def dependencies() -> tuple[str, str, dict]:
    try:
        import imageio_ffmpeg
        import yt_dlp.version
    except ImportError as error:
        raise MusicError("Faltan componentes. Ejecutá instalar.py con el Python del servidor.") from error
    config_path = Path(__file__).with_name("config.json")
    config = json.loads(config_path.read_text(encoding="utf-8")) if config_path.exists() else {}
    configured_node = config.get("node")
    node = configured_node if configured_node and Path(configured_node).is_file() else shutil.which("node")
    if not node or not Path(node).is_file():
        raise MusicError("No se encontró Node.js para YouTube. Volvé a ejecutar el instalador.")
    flags = (subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
    version = subprocess.check_output([node, "--version"], text=True, creationflags=flags).strip()
    if int(version.lstrip("v").split(".")[0]) < 22:
        raise MusicError("YouTube necesita Node.js 22 o posterior.")
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    details = {"programa": VERSION, "yt-dlp": yt_dlp.version.__version__, "node": version,
               "ffmpeg": imageio_ffmpeg.get_ffmpeg_version()}
    return str(Path(node).resolve()), ffmpeg, details


def bitrate_for(duration: object) -> tuple[float, int]:
    if not isinstance(duration, (float, int)) or not math.isfinite(duration) or duration <= 0:
        raise MusicError("No se pudo determinar la duración del audio; no se inicia una descarga ilimitada.")
    for kbps in (192, 160, 128, 96):
        if duration * kbps * 1000 / 8 < MAX_BYTES - 2 * 1024 * 1024:
            return float(duration), kbps
    raise MusicError("El video es demasiado largo para el límite de 64 MiB del orbe.")


class DownloadLog:
    def debug(self, message):
        pass
    def warning(self, message):
        print("Aviso: " + plain_error(message), flush=True)
    def error(self, message):
        pass  # La excepción se informa una sola vez en el resumen.


def download(library: Library, value: str, node: str, ffmpeg: str, factory=None) -> dict:
    from yt_dlp import YoutubeDL
    video, url = canonical_url(value)
    old = library.existing(video)
    if old:
        print("Ya estaba: " + old.name, flush=True)
        return {"status": "existente", "id": video, "file": old.name}
    if library.capacity() >= MAX_TRACKS:
        raise MusicError("La biblioteca ya tiene 256 canciones.")
    if shutil.disk_usage(library.root).free < 512 * 1024 * 1024:
        raise MusicError("Quedan menos de 512 MiB libres; se conserva el espacio para el servidor.")
    job = Path(tempfile.mkdtemp(prefix="youtube-", dir=library.work))
    last_progress = [0.0]
    def progress(info):
        received = info.get("downloaded_bytes") or 0
        if received > MAX_SOURCE_BYTES:
            raise MusicError("El archivo fuente supera el límite de descarga de 256 MiB.")
        now = time.monotonic()
        if now - last_progress[0] >= 2 and info.get("status") == "downloading":
            total = info.get("total_bytes") or info.get("total_bytes_estimate")
            label = f"{min(100, int(100*received/total))}%" if total else f"{received/1048576:.1f} MiB"
            print("Descargando audio: " + label, flush=True)
            last_progress[0] = now
    options = {"format": "bestaudio[ext=m4a]/bestaudio/best", "noplaylist": True,
               "outtmpl": str(job / "fuente.%(ext)s"), "windowsfilenames": True,
               "restrictfilenames": True, "quiet": True, "noprogress": True,
               "logger": DownloadLog(), "progress_hooks": [progress], "cachedir": False,
               "js_runtimes": {"node": {"path": node}}, "ffmpeg_location": ffmpeg,
               "socket_timeout": 20, "retries": 2, "fragment_retries": 2,
               "concurrent_fragment_downloads": 1, "ratelimit": 2*1024*1024,
               "max_filesize": MAX_SOURCE_BYTES, "overwrites": False,
               "writeinfojson": False, "writethumbnail": False}
    try:
        with (factory or YoutubeDL)(options) as ydl:
            print("Consultando YouTube: " + video, flush=True)
            info = ydl.extract_info(url, download=False)
            if not info or info.get("_type") in {"playlist", "multi_video"} or info.get("id") != video:
                raise MusicError("YouTube no devolvió el video individual solicitado.")
            if info.get("is_live") or info.get("live_status") in {"is_live", "is_upcoming"}:
                raise MusicError("El enlace corresponde a un directo o estreno aún abierto.")
            duration, bitrate = bitrate_for(info.get("duration"))
            title = str(info.get("title") or video)
            print("Tema: " + title, flush=True)
            result = ydl.process_ie_result(info, download=True)
            if result is None:
                raise MusicError("La descarga no entregó un archivo de audio.")
        sources = [p for p in job.iterdir() if p.is_file() and not linked(p) and p.name.startswith("fuente.") and p.suffix not in {".part", ".ytdl", ".temp"}]
        if len(sources) != 1 or not 0 < sources[0].stat().st_size <= MAX_SOURCE_BYTES:
            raise MusicError("No se completó un único archivo fuente válido.")
        mp3 = job / "lista.mp3"
        flags = (subprocess.CREATE_NO_WINDOW | subprocess.BELOW_NORMAL_PRIORITY_CLASS) if os.name == "nt" else 0
        print(f"Convirtiendo a MP3 ({bitrate} kbps)...", flush=True)
        subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-nostdin", "-n",
                        "-threads", "1", "-i", str(sources[0]), "-map", "0:a:0", "-vn",
                        "-map_metadata", "-1", "-c:a", "libmp3lame", "-b:a", f"{bitrate}k",
                        "-ar", "44100", "-ac", "2", "-threads", "1", "-id3v2_version", "3",
                        "-fs", str(MAX_BYTES+8192), str(mp3)], check=True, capture_output=True,
                       timeout=300, creationflags=flags)
        if mp3.stat().st_size > MAX_BYTES:
            raise MusicError("El MP3 supera 64 MiB; no se agrega al orbe.")
        # Comprueba que el MP3 completo decodifique, antes de hacerlo visible al servidor.
        subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-xerror", "-nostdin",
                        "-threads", "1", "-i", str(mp3), "-map", "0:a:0", "-threads", "1",
                        "-f", "null", "-"], check=True, capture_output=True,
                       timeout=120, creationflags=flags)
        record = library.publish(video, url, title, mp3, duration, bitrate)
        print("Agregada al servidor: " + record["file"], flush=True)
        return record
    except subprocess.CalledProcessError as error:
        raise MusicError("No se pudo convertir o verificar el audio: " + plain_error(error.stderr.decode("utf-8", errors="replace"))) from error
    finally:
        if job.resolve().parent != library.work.resolve() or linked(job):
            raise MusicError("Se conserva un temporal cuya ruta cambió durante la descarga.")
        shutil.rmtree(job)


def main(argv=None) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description="Agrega canciones de YouTube a la biblioteca del orbe, sin reiniciar Minecraft.")
    parser.add_argument("enlaces", nargs="*", help="Enlaces de videos individuales o IDs de YouTube")
    parser.add_argument("--archivo", type=Path, help="Archivo UTF-8 con un enlace por línea")
    parser.add_argument("--servidor", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--comprobar", action="store_true", help="Revisa componentes sin descargar canciones")
    args = parser.parse_args(argv)
    try:
        node, ffmpeg, versions = dependencies()
        library = Library(args.servidor)
        if args.comprobar:
            print(json.dumps({**versions, "carpeta": str(library.music), "canciones": library.capacity(), "lista": True}, ensure_ascii=False, indent=2))
            return 0
        links = list(args.enlaces)
        if args.archivo:
            if args.archivo.stat().st_size > 65536:
                raise MusicError("El archivo de enlaces es demasiado grande.")
            links.extend(v.strip() for v in args.archivo.read_text(encoding="utf-8-sig").splitlines() if v.strip() and not v.lstrip().startswith("#"))
        if not links:
            if not sys.stdin.isatty():
                raise MusicError("Indicá al menos un enlace o usá --archivo.")
            print("MÚSICA DE YOUTUBE PARA EL ORBE\nPegá un enlace por línea. Una línea vacía empieza la descarga.\n", flush=True)
            while value := input("Enlace: ").strip():
                links.append(value)
                if len(links) >= MAX_LINKS:
                    break
        if not links:
            print("No se ingresaron enlaces. No se descargó nada.")
            return 0
        if len(links) > MAX_LINKS:
            raise MusicError("Se admiten hasta 20 enlaces por tanda.")
        results = []
        with exclusive(library.admin / "descargas.lock"):
            # Leer el registro después del lock para no perder altas de otro proceso.
            library = Library(args.servidor)
            seen = set()
            for value in links:
                try:
                    video, url = canonical_url(value)
                    if video in seen:
                        continue
                    seen.add(video)
                    results.append(download(library, url, node, ffmpeg))
                except Exception as error:
                    message = plain_error(error)
                    results.append({"status": "error", "error": message})
                    print("No se agregó: " + message, file=sys.stderr, flush=True)
            report = {"atUtc": datetime.now(timezone.utc).isoformat(), "results": results}
            atomic_json(library.admin / "ultimo-resultado.json", report)
        added = sum(r["status"] == "agregada" for r in results)
        skipped = sum(r["status"] == "existente" for r in results)
        errors = sum(r["status"] == "error" for r in results)
        print(f"\nTerminó: {added} agregadas, {skipped} ya existentes, {errors} con error.")
        print("En el juego: orbe en mano > O > Parlante > Actualizar.")
        return 1 if errors else 0
    except (MusicError, OSError, ValueError) as error:
        print("No se pudo continuar: " + plain_error(error), file=sys.stderr)
        return 1
    except (KeyboardInterrupt, EOFError):
        print("\nImportación cancelada. Las canciones ya agregadas se conservan.")
        return 130


if __name__ == "__main__":
    raise SystemExit(main())
