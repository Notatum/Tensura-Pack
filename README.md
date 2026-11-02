# Tensura Pack

Minecraft **1.21.1**, NeoForge **21.1.250** y Java **21**. Revisión **2026.11.02**.

## Actualizar el cliente

Usa `Tensura Mods Updater - Incremental.bat` con Minecraft y TL Legacy cerrados. Comprueba el último commit de GitHub y descarga el pack de ese commit. La caché conserva las descargas que ya coinciden con sus hashes. La variante `Tensura Mods Updater.bat` vuelve a validar el contenido del pack.

Los dos actualizadores apuntan a `%APPDATA%\.tlauncher\legacy\Minecraft\game`. Comprueba esa ruta si usas otro launcher. La sincronización del pack incluye mods, configuraciones y paquetes de recursos; conserva las partidas, capturas y opciones personales. Estos actualizadores sincronizan las carpetas del pack: utiliza una instalación dedicada a este servidor.

Se quitó la prioridad automática de `.tensura-client-mods`: una copia local antigua ya no impide recibir las novedades publicadas. El instalador `Aplicar-Tensura-Local.ps1` queda disponible para aplicar explícitamente un paquete sin conexión, con manifiesto SHA-256, respaldo y restauración ante un error. No sustituye la comprobación de GitHub.

Los actualizadores de esta revisión seleccionan **Español (Argentina)** y activan `Tensura-Espanol-2026.11.02.zip`, conservando las demás opciones y paquetes seleccionados. El paquete completa también las interfaces de los otros mods. Si usas otro launcher, elige español y activa ese paquete de recursos manualmente. También incluye `es_es` y `es_mx`.

## Entender el progreso

Consulta [la guía de aprendizaje, maestría y árbol de Great Sage](GUIA-HABILIDADES.md). Incluye los requisitos, las bonificaciones de entrenamiento comprobadas en esta versión y las diferencias entre el mod base y los cambios de este servidor.

## Actualizar el servidor

El paquete de [actualizador del servidor](updater/server/) incluye el JAR, su código y los lanzadores. Copia sus archivos de ejecución a la raíz de un servidor NeoForge ya instalado. Con el servidor detenido, ejecuta `Actualizar-Tensura-Servidor.bat` o `bash Actualizar-Tensura-Servidor.sh`.

El modo predeterminado consulta GitHub, fija el commit y prepara los mods antes de reemplazarlos. Usa Packwiz en modo servidor, excluye Sodium y los vehículos retirados y guarda la instalación de mods anterior. Conserva el mundo y las configuraciones existentes del servidor.

Para actualizar y luego iniciar, usa `Iniciar-Tensura-Con-Update.bat java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.250/win_args.txt nogui`. En Linux, pasa a `Iniciar-Tensura-Con-Update.sh` el comando de inicio correspondiente.

`--local` es una opción explícita para usar la copia local con sus hashes; no consulta novedades. `--remote` mantiene la compatibilidad con los lanzadores anteriores y selecciona el modo remoto. `--pack-url URL` permite usar otro origen.

## Cambios de esta revisión

- Tensura 2.0.1.4, Ascension 3.0.1, Great Sage 0.0.7.1, EP Scaling 1.2.0 y Nightmares 1.0.6.1.
- Vehículos y sus entradas de Packwiz retirados.
- Corrección del enlace de evolución hacia Dragón insecto y conservación de los arreglos de análisis y armas evolucionadas.
- Traducciones revisadas y explicaciones de aprendizaje y maestría, sin mezclar los puntos del árbol.
- Autoactualización remota, validación de hashes y paquete local del cliente con respaldo.

Los JAR modificados son derivados locales. La revisión técnica y sus límites se documentan en `REVISION-2026-11-02.md`.
