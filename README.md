# Tensura Pack

Minecraft **1.21.1**, NeoForge **21.1.250** y Java **21**. Revisión **2026.11.02.3**.

## Actualizar el cliente

Descargá [TensuraLauncher-Portable.zip](https://github.com/Notatum/Tensura-Pack/raw/refs/heads/main/updater/launcher/TensuraLauncher-Portable.zip), extraelo y abrí **TensuraLauncher.exe**. Elegí tu nombre y pulsá **JUGAR**. Comprueba la publicación de GitHub, prepara el pack y abre Minecraft directamente. **Actualizar** prepara el cliente; **Reparar** fuerza la sincronización y verifica los archivos.

El juego tiene su propia carpeta en `%LOCALAPPDATA%\TensuraLauncher\game` y su propio Java 21. La primera preparación puede copiar archivos y partidas de TLauncher; no copia cuentas ni tokens y conserva el origen. Los archivos que falten se descargan y verifican. El launcher, su motor y el pack se actualizan desde este repositorio.

Los BAT anteriores siguen siendo compatibles y abren el launcher independiente para actualizar o reparar. `-NoLaunch` conserva la actualización técnica de una carpeta indicada por `-GameDir`. `Aplicar-Tensura-Local.ps1` utiliza el mismo updater remoto administrado. Las instrucciones completas están en [LEEME-CLIENTE.md](LEEME-CLIENTE.md).

El cliente selecciona **Español (Argentina)**, activa `Tensura-Espanol-2026.11.02.zip` y mantiene **Complementary Unbound obligatorio**. Conserva las demás preferencias personales. La distribución contiene **82 mods de servidor y 87 de cliente**. El servidor mantiene perfiles locales en modo offline; el launcher actual no incluye autenticación Microsoft.

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
