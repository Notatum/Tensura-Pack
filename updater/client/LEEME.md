# Cliente administrado de Tensura

El BAT de cliente es la entrada habitual del juego. `Actualizar-Tensura-Cliente.ps1` obtiene el commit de `main`, verifica `release.json`, descarga el motor de esa publicación y comprueba sus SHA-256. El motor sincroniza el pack con Packwiz, instala el runtime requerido cuando falta y prepara el perfil de Legacy Launcher. Al terminar inicia ese perfil automáticamente, usando los datos de cuenta locales del jugador.

## Configuración central

`config/tensura-managed-client.json` define Minecraft, NeoForge, el perfil, el idioma, el paquete de traducciones, el ZIP de Unbound y si los shaders son obligatorios. El nombre del ZIP debe coincidir con el distribuido por `shaderpacks/complementary-unbound.pw.toml`; `shaderSha256` debe corresponder a ese archivo.

La política actual usa Minecraft 1.21.1, NeoForge 21.1.250 y `ComplementaryUnbound_r5.3.zip`. La configuración anterior apuntaba a una carpeta de Unbound r5.9 con Euphoria que no se distribuía, por lo que Iris volvía al renderizado sin shaders.

`Tensura-Managed-Client-1.0.0.jar` se instala solo en el cliente. Verifica el archivo de shader y aplica la política al cargar, guardar y modificar la configuración de Iris. Mientras `mandatoryShaders` sea `true`, los controles de Iris y las solicitudes de su API conservan el shader requerido y su activación. El código fuente está en `mod-src`. La política se lee al iniciar el juego.

## Publicar cambios para las otras PC

1. Edita las configuraciones, los mods o los paquetes del repositorio. Las carpetas administradas son `mods`, `config`, `defaultconfigs`, `resourcepacks`, `shaderpacks`, `kubejs` y `scripts`.
2. Si editas el motor, el bootstrap o el perfil, ejecuta `python updater/client/build-release.py` para renovar sus hashes. Los cambios de versión de Minecraft o NeoForge también requieren actualizar `release.json`, el instalador y la plantilla de perfil.
3. Si cambias mods, actualiza los manifiestos de cliente y servidor y comprueba la instalación con Packwiz. Los manifiestos SHA-256 deben describir todos los JAR que recibe cada lado.
4. Ejecuta `packwiz refresh`, revisa y verifica el resultado, y publica el commit en `main`.

La próxima ejecución del BAT en cada PC recibe esa publicación completa. También obtiene el motor actualizado y renueva su propio bootstrap. Un archivo editado únicamente en una PC queda local hasta que se publica en el repositorio.

## Migración y uso

Las copias anteriores de `Actualizar-Tensura-Cliente.ps1` se actualizan mediante `Configurar-Tensura-Idioma.ps1`, un paso que ya ejecutaba el protocolo anterior. La primera actualización instala el perfil y reemplaza el updater antiguo; las siguientes usan directamente el motor administrado. Los BAT y el PS1 deben permanecer juntos.

El modo incremental también restaura la política cuando el pack publicado no cambió. `-NoLaunch` aplica y verifica todo sin abrir el juego. `-GameDir` permite otra carpeta y `-CacheDir` otra caché. Minecraft y el launcher deben estar cerrados antes de actualizar. Se utiliza Java 21 del sistema o del runtime incluido con Legacy Launcher.

Los respaldos quedan en `.tensura-backups` dentro del juego. Se conservan las partidas, capturas, lista de servidores y opciones ajenas a la política. La configuración del pack se sincroniza con el repositorio. Los recursos y shaders personales adicionales se conservan, aunque Iris mantiene seleccionado el shader obligatorio.

## Comprobaciones realizadas

El updater pasó instalación, repetición sin reemplazos, modo estricto, restauración tras un fallo provocado, reparación de cachés de mods y shaders, instalación de perfil y migración desde la versión publicada anterior. Una prueba real dentro de un mundo aislado verificó que Unbound estaba renderizando y permanecía activo tras intentar desactivarlo o cambiarlo mediante Iris y su API.
