# Cliente de Tensura — revisión 2026.11.02.5

Descargá [TensuraLauncher-Portable.zip](https://github.com/Notatum/Tensura-Pack/raw/refs/heads/main/updater/launcher/TensuraLauncher-Portable.zip), extraelo en una carpeta propia y abrí **TensuraLauncher.exe**. Elegí tu nombre de jugador, asigná memoria y pulsá **JUGAR**. El launcher comprueba la publicación de GitHub, sincroniza el pack y abre Minecraft directamente con Java.

La plataforma es **Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21**, con **87 mods de cliente**. Usá siempre el mismo nombre para conservar la identidad de tu personaje en el servidor offline. Esta versión usa un perfil local y no incluye inicio de sesión Microsoft.

## Instalación independiente

Los datos se guardan en **%LOCALAPPDATA%\TensuraLauncher**. El juego queda en su subcarpeta **game**; **Abrir carpeta** permite acceder a ella. Podés mover el EXE a otra carpeta sin perder estos datos.

En el primer uso se reutilizan, si existen, Java 21, bibliotecas, recursos y archivos personales de la instalación local de TLauncher. Se copian las partidas, capturas, opciones y lista de servidores. El origen se conserva y no se importan cuentas, contraseñas ni tokens. Los archivos necesarios que falten se descargan de sus proveedores y se comprueban con sus hashes. Después de la preparación, el juego utiliza su propio Java y sus propias carpetas; no ejecuta TLauncher. El mod de skins distribuido por el pack sigue disponible.

Requiere Windows 10/11 de 64 bits, PowerShell 5.1 y .NET Framework 4.8. No hace falta instalar Java, Git, Python ni herramientas de desarrollo. La primera preparación necesita conexión y puede tardar varios minutos.

## Actualizaciones y reparación

- **JUGAR:** comprueba la publicación actual, actualiza y abre el juego.
- **Actualizar:** prepara y verifica el cliente sin abrir Minecraft.
- **Reparar:** fuerza la sincronización estricta de los mods y ajustes administrados, y comprueba bibliotecas, recursos y plataforma.
- **Ver registro:** muestra el detalle de la preparación; los registros del juego también quedan en **logs** dentro de la carpeta de datos.

El EXE y el motor del launcher también reciben actualizaciones. Cada preparación usa una publicación concreta de GitHub para evitar mezclar versiones. Las descargas de motor, mods, shaders y recursos se verifican antes de usarse.

El pack administra las carpetas mods, config, defaultconfigs, resourcepacks, shaderpacks, kubejs y scripts. Conserva las partidas, capturas, servidores y opciones personales ajenas a la política. Los reemplazos del pack tienen respaldo en **game\.tensura-backups**. Los recursos y shaders personales adicionales se conservan.

**Great Sage usa el JAR original de Modrinth, sin parches ni traducciones externas.** La primera actualización a esta revisión respalda sus ajustes anteriores y deja que el mod regenere sus valores originales. En las siguientes actualizaciones se conservan los archivos de configuración que genere Great Sage. El acceso a sus funciones y comentarios vuelve a respetar los requisitos del autor.

**Complementary Unbound es obligatorio.** El cliente mantiene ComplementaryUnbound_r5.3.zip seleccionado y activado. También mantiene Español (Argentina) y el paquete de traducciones de Tensura. El complemento administrado aplica la política al cargar, guardar o cambiar Iris.

## Compatibilidad con los BAT anteriores

Los dos BAT y Actualizar-Tensura-Cliente.ps1 siguen funcionando juntos. En uso normal descargan el launcher independiente y le piden actualizar o reparar. Las versiones anteriores reciben la migración por el protocolo del updater.

Para mantenimiento técnico, **-NoLaunch** conserva el protocolo anterior: actualiza y verifica la carpeta indicada por **-GameDir**, sin abrir el launcher. En ese modo la ruta predeterminada sigue siendo la instalación antigua, por compatibilidad; indicá siempre la carpeta que quieras administrar:

    "Tensura Mods Updater - Incremental.bat" -NoLaunch -GameDir "%LOCALAPPDATA%\TensuraLauncher\game" -CacheDir "%LOCALAPPDATA%\TensuraLauncher\cache\updater"

Los cambios hechos únicamente en una PC siguen siendo locales. Para que lleguen a las otras PC hay que publicarlos en **Notatum/Tensura-Pack**, rama **main**; se reciben al usar **JUGAR** o **Actualizar**. El procedimiento está en [updater/client/LEEME.md](updater/client/LEEME.md).

Leé [GUIA-HABILIDADES.md](GUIA-HABILIDADES.md) para distinguir aprendizaje, maestría, EP y puntos del árbol de Great Sage.
