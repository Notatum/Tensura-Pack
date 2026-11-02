# Orbe 1.3.1: archivos de audio con nombres largos

La lista de audio descartaba silenciosamente los nombres de más de 96 caracteres. Un MP3 real de 104 caracteres permitía reproducir el fallo: pulsar Actualizar releía la carpeta, pero el filtro lo excluía.

El límite ahora es 255 caracteres en `AudioRules`, en la búsqueda del menú y en ambas direcciones del códec de ajustes. El protocolo pasa de 3 a 4 para exigir el mismo soporte en cliente y servidor. Se mantienen las comprobaciones de rutas, archivos ocultos, separadores y caracteres de control, y los formatos MP3, OGG Vorbis y WAV.

El constructor solo cambia `AudioRules`, `OrbNetwork`, la longitud del buscador y la versión del mod. Conserva exactamente las clases de daño lineal, progreso, propiedad y evolución de 1.3.0.

`updater/tests/OrbAudioRefreshVerification.java` comprueba la detección de archivos añadidos, los límites de longitud, el rechazo de rutas inseguras y la decodificación de MP3 reales. El códec de red también se verifica dentro del servidor con un nombre de más de 96 caracteres.

Los archivos se colocan en `orbe_audio/musica` de la instalación activa. En el launcher independiente es `%LOCALAPPDATA%/TensuraLauncher/game/orbe_audio/musica`. Actualizar vuelve a leer esa carpeta sin reiniciar; después se selecciona la canción en Parlante y se pulsa Reproducir. Los archivos personales de música se conservan al actualizar.
