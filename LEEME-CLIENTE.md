# Cliente de Tensura — revisión 2026.11.03.1

Descargá [Tensura-Launcher-Scripts.zip](https://github.com/Notatum/Tensura-Pack/raw/refs/heads/main/updater/launcher/Tensura-Launcher-Scripts.zip), extraelo en una carpeta propia y abrí **Tensura-Launcher.bat**. Elegí tu nombre de jugador, memoria y procesadores y pulsá **JUGAR**. El acceso descarga la interfaz y el motor verificados de la publicación actual y abre Minecraft directamente con Java.

La plataforma es **Minecraft 1.21.1 / NeoForge 21.1.250 / Java 21**, con **89 mods de cliente**. Usá siempre el mismo nombre para conservar la identidad de tu personaje en el servidor offline. Esta versión usa un perfil local y no incluye inicio de sesión Microsoft.

## Instalación independiente

Los datos se guardan en **%LOCALAPPDATA%\TensuraLauncher**. El juego queda en su subcarpeta **game**; **Abrir carpeta** permite acceder a ella. Podés mover juntos el BAT y su PS1 a otra carpeta sin perder estos datos.

## Procesadores para Minecraft

El valor inicial es **Automático (80 %)** de los procesadores lógicos disponibles, redondeado hacia abajo y con un mínimo de uno. En un equipo de 16 hilos se seleccionan 12. Para elegir otra cantidad, desmarcá Automático y usá el selector; la elección se guarda junto con el nombre y la memoria y se aplica al próximo arranque.

El motor usa `-XX:ActiveProcessorCount` para los grupos de hilos de Java y afinidad de Windows para el proceso del juego. No fija un porcentaje de utilización ni convierte el hilo principal de Minecraft en una tarea paralela. No modifica la afinidad de aplicaciones que ya estén abiertas. En equipos con más de 64 procesadores, la selección corresponde al grupo disponible para el launcher.

En el primer uso se reutilizan, si existen, Java 21, bibliotecas, recursos y archivos personales de la instalación local de TLauncher. Se copian las partidas, capturas, opciones y lista de servidores. El origen se conserva y no se importan cuentas, contraseñas ni tokens. Los archivos necesarios que falten se descargan de sus proveedores y se comprueban con sus hashes. Después de la preparación, el juego utiliza su propio Java y sus propias carpetas; no ejecuta TLauncher. El mod de skins distribuido por el pack sigue disponible.

Requiere Windows 10/11 de 64 bits, PowerShell 5.1 y .NET Framework 4.8. No hace falta instalar Java, Git, Python ni herramientas de desarrollo. La primera preparación necesita conexión y puede tardar varios minutos.

## Actualizaciones y reparación

- **JUGAR:** comprueba la publicación actual, actualiza y abre el juego.
- **Actualizar:** prepara y verifica el cliente sin abrir Minecraft.
- **Reparar:** fuerza la sincronización estricta de los mods y ajustes administrados, y comprueba bibliotecas, recursos y plataforma.
- **Ver registro:** muestra el detalle de la preparación; los registros del juego también quedan en **logs** dentro de la carpeta de datos.

La interfaz de scripts y el motor del launcher reciben actualizaciones al abrir el acceso. Cada preparación usa una publicación concreta de GitHub para evitar mezclar versiones. Las descargas de interfaz, motor, mods, shaders y recursos se verifican antes de usarse. Esta distribución no incluye ni restaura un EXE.

El pack administra las carpetas mods, config, defaultconfigs, resourcepacks, shaderpacks, kubejs y scripts. Conserva las partidas, capturas, servidores y opciones personales ajenas a la política. Los reemplazos del pack tienen respaldo en **game\.tensura-backups**. Los recursos y shaders personales adicionales se conservan.

**Great Sage usa el JAR original de Modrinth, sin parches ni traducciones externas.** La primera actualización a esta revisión respalda sus ajustes anteriores y deja que el mod regenere sus valores originales. En las siguientes actualizaciones se conservan los archivos de configuración que genere Great Sage. El acceso a sus funciones y comentarios vuelve a respetar los requisitos del autor.

**Complementary Unbound es obligatorio.** El cliente mantiene ComplementaryUnbound_r5.3.zip seleccionado y activado. También mantiene Español (Argentina) y el paquete de traducciones de Tensura. El complemento administrado aplica la política al cargar, guardar o cambiar Iris.

## Compatibilidad con los BAT anteriores

Los dos BAT y Actualizar-Tensura-Cliente.ps1 siguen funcionando juntos. En uso normal descargan el launcher independiente y le piden actualizar o reparar. Las versiones anteriores reciben la migración por el protocolo del updater.

Para mantenimiento técnico, **-NoLaunch** conserva el protocolo anterior: actualiza y verifica la carpeta indicada por **-GameDir**, sin abrir el launcher. En ese modo la ruta predeterminada sigue siendo la instalación antigua, por compatibilidad; indicá siempre la carpeta que quieras administrar:

    "Tensura Mods Updater - Incremental.bat" -NoLaunch -GameDir "%LOCALAPPDATA%\TensuraLauncher\game" -CacheDir "%LOCALAPPDATA%\TensuraLauncher\cache\updater"

Los cambios hechos únicamente en una PC siguen siendo locales. Para que lleguen a las otras PC hay que publicarlos en **Notatum/Tensura-Pack**, rama **main**; se reciben al usar **JUGAR** o **Actualizar**. El procedimiento está en [updater/client/LEEME.md](updater/client/LEEME.md).

Leé [GUIA-HABILIDADES.md](GUIA-HABILIDADES.md) para distinguir aprendizaje, maestría, EP y puntos del árbol de Great Sage.
