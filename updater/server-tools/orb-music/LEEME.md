# Agregar música de YouTube al orbe

Herramienta administrativa local del servidor, versión 1.0.0. Recibe enlaces de videos de YouTube o YouTube Music, descarga su audio y publica MP3 completos en `orbe_audio_servidor/musica`. Funciona con la biblioteca compartida de Orbe 1.4.0 / pack 2026.11.05.5. No requiere cambiar mods, actualizar clientes ni reiniciar el servidor.

En esta instalación, abrir **`C:\Servidor Tensura\Agregar-Musica-YouTube.bat`**, pegar un enlace por línea y terminar con una línea vacía. Después, en el juego: orbe en mano → **O → Parlante → Actualizar**. El catálogo también se revisa automáticamente cada diez segundos.

También se puede pedir al asistente por el chat que use esta herramienta con uno o varios enlaces. No hay un servicio que lea el chat en segundo plano: el asistente ejecuta el importador cuando recibe el pedido y tiene acceso a esta máquina.

## Comandos

Desde PowerShell, usando el Python privado:

```powershell
& 'C:\Servidor Tensura\herramientas\musica\runtime\Scripts\python.exe' -B 'C:\Servidor Tensura\herramientas\musica\descargar_musica.py' 'https://www.youtube.com/watch?v=ID_DEL_VIDEO'
```

Admite varios enlaces como argumentos y `--archivo enlaces.txt` con un enlace por línea. `--comprobar` revisa componentes y carpeta sin descargar canciones. Para otra instalación, indicar `--servidor 'C:\ruta\servidor'`, que debe contener `server.properties`. Los IDs de once caracteres también se admiten. Hasta 20 enlaces por tanda; un enlace de video con parámetros de playlist importa únicamente ese video. Las playlists, canales, directos y estrenos todavía abiertos no se importan.

## Comportamiento

- Descarga secuencial, límite de red 2 MiB/s y conversión FFmpeg con un hilo y prioridad inferior a la normal en Windows.
- MP3 estéreo, 44,1 kHz y 192 kbps por defecto. Para videos largos elige 160, 128 o 96 kbps si hace falta para entrar en 64 MiB. Rechaza duraciones desconocidas, videos demasiado largos, más de 256 canciones y fuentes de más de 256 MiB.
- Descarga, convierte y verifica la decodificación del MP3 en `orbe_audio_servidor/descargas/temporales`. Solo después lo hace visible en `musica`, de manera atómica y sin sobrescribir archivos existentes. El nombre incluye el ID de YouTube.
- Un registro por ID y SHA-256 evita descargar otra vez una canción ya agregada. Si alguien modificó su copia, se conserva y se informa el conflicto. También conserva las canciones importadas manualmente.
- Se procesan los demás enlaces aunque uno falle. El resumen queda en `orbe_audio_servidor/descargas/ultimo-resultado.json`; el registro está en `catalogo-youtube.json`. Ambos son privados del servidor y no se publican con el pack.
- No importa cookies, cuentas, contraseñas ni configuración del navegador. Si YouTube exige iniciar sesión o limita una descarga, devuelve el error y conserva las canciones existentes. No garantiza que todos los videos estén disponibles.

## Instalación y componentes

Con Python 3.11 o posterior y Node.js 22 o posterior disponibles:

```powershell
python instalar.py --servidor 'C:\Servidor Tensura' --node 'C:\ruta\node.exe'
```

Instala un entorno Python aislado en `herramientas/musica/runtime`, copia el programa y genera el acceso BAT. No modifica Java, Node, Minecraft ni el launcher global. Usa ruedas de PyPI para `yt-dlp[default]` y `imageio-ffmpeg`, que incluye un FFmpeg ejecutable. El motor JavaScript de Node se habilita explícitamente con EJS instalado. Versiones iniciales comprobadas: yt-dlp 2026.10.26, EJS 0.8.0, imageio-ffmpeg 0.8.1 / FFmpeg 9.0.2 y Node 24.22.1.

Si YouTube cambia y hace falta actualizar, ejecutar el instalador con `--actualizar` renueva solo esos componentes privados desde PyPI. Se registra la comprobación instalada en `herramientas/musica/componentes.json`. No se ejecutan actualizaciones automáticas al pegar cada enlace.

Fuentes oficiales: [yt-dlp y su API Python](https://github.com/yt-dlp/yt-dlp#embedding-yt-dlp), [EJS y motores JavaScript](https://github.com/yt-dlp/yt-dlp/wiki/EJS), [FFmpeg incluido por imageio-ffmpeg](https://github.com/imageio/imageio-ffmpeg). Solo el código de la herramienta se versiona; música, enlaces personales, entorno y registros quedan fuera del repositorio y del pack.
