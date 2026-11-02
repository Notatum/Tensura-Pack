# Cliente administrado de Tensura

La entrada habitual es **TensuraLauncher.exe**, distribuido en [el ZIP portátil](../launcher/TensuraLauncher-Portable.zip). Abre Minecraft directamente con Java, usa una carpeta propia y un nombre local para el servidor offline. Los BAT anteriores descargan el EXE verificado y le solicitan actualizar o reparar. `Actualizar-Tensura-Cliente.ps1 -NoLaunch` sigue siendo el protocolo que utiliza el motor del launcher para sincronizar el pack.

El launcher consulta el commit de `main`, comprueba `updater/launcher/release.json`, renueva su EXE si corresponde y descarga el motor de esa publicación con sus SHA-256. Después sincroniza el pack con Packwiz, prepara NeoForge, comprueba bibliotecas y recursos y genera los argumentos para Java. La instalación inicial copia archivos útiles de TLauncher si existen; las cuentas y tokens quedan fuera. Los datos propios se guardan en `%LOCALAPPDATA%\TensuraLauncher`.

## Configuración central

`config/tensura-managed-client.json` define Minecraft, NeoForge, el perfil, el idioma, el paquete de traducciones, el ZIP de Unbound y si los shaders son obligatorios. El nombre del ZIP debe coincidir con el distribuido por `shaderpacks/complementary-unbound.pw.toml`; `shaderSha256` debe corresponder a ese archivo.

La política actual usa Minecraft 1.21.1, NeoForge 21.1.250 y `ComplementaryUnbound_r5.3.zip`. La configuración anterior apuntaba a una carpeta de Unbound r5.9 con Euphoria que no se distribuía, por lo que Iris volvía al renderizado sin shaders.

`Tensura-Managed-Client-1.0.0.jar` se instala solo en el cliente. Verifica el archivo de shader y aplica la política al cargar, guardar y modificar la configuración de Iris. Mientras `mandatoryShaders` sea `true`, los controles de Iris y las solicitudes de su API conservan el shader requerido y su activación. El código fuente está en `mod-src`. La política se lee al iniciar el juego.

## Publicar cambios para las otras PC

1. Edita las configuraciones, los mods o los paquetes del repositorio. Las carpetas administradas son `mods`, `config`, `defaultconfigs`, `resourcepacks`, `shaderpacks`, `kubejs` y `scripts`.
2. Si editas el motor, el bootstrap o el perfil, ejecuta `python updater/client/build-release.py` para renovar sus hashes. Después ejecuta `python updater/launcher/build-launcher.py`, que compila el EXE, crea el ZIP portátil y actualiza el manifiesto del launcher y su referencia en el manifiesto de cliente. Este segundo paso también es obligatorio si cambias el código del launcher. Los cambios de versión de Minecraft o NeoForge requieren actualizar la política, `release.json`, el instalador y la plantilla de perfil.
3. Si cambias mods, actualiza los manifiestos de cliente y servidor y comprueba la instalación con Packwiz. Los manifiestos SHA-256 deben describir todos los JAR que recibe cada lado.
4. Ejecuta `packwiz refresh`, revisa y verifica el resultado, y publica el commit en `main`.

La próxima pulsación de **JUGAR** o **Actualizar** en cada PC recibe esa publicación completa. Los BAT reciben la misma migración. Un archivo editado únicamente en una PC queda local hasta que se publica en el repositorio. Las URLs de JAR propios deben apuntar a un commit inmutable o una publicación y corresponder al hash distribuido; no uses rutas relativas de descarga de Packwiz.

## Migración y uso

Las copias anteriores de `Actualizar-Tensura-Cliente.ps1` se actualizan mediante `Configurar-Tensura-Idioma.ps1`, un paso que ya ejecutaba el protocolo anterior. La primera actualización instala el perfil y reemplaza el updater antiguo; las siguientes usan directamente el motor administrado. Los BAT y el PS1 deben permanecer juntos.

El modo incremental también restaura la política cuando el pack publicado no cambió. `-NoLaunch` aplica y verifica todo sin abrir el juego. `-GameDir` permite otra carpeta y `-CacheDir` otra caché; en ese protocolo la ruta predeterminada antigua se conserva por compatibilidad. Minecraft debe estar cerrado antes de actualizar. El launcher independiente instala y usa su propio Java 21; no requiere Java del sistema ni TLauncher después de la importación. Si no puede reutilizar uno existente, descarga Eclipse Temurin JRE 21.0.13+9 con SHA-256 fijado.

Los respaldos quedan en `.tensura-backups` dentro del juego. Se conservan las partidas, capturas, lista de servidores y opciones ajenas a la política. La configuración del pack se sincroniza con el repositorio. Los recursos y shaders personales adicionales se conservan, aunque Iris mantiene seleccionado el shader obligatorio.

Great Sage se descarga directamente de Modrinth y se verifica contra su hash oficial. La migración `greatSageVanillaRevision` respalda y retira una sola vez los ajustes del pack para regenerar los valores originales. Después, `greatsage-common.toml` y `greatsage-client.toml` se conservan también en modo estricto. El paquete de español excluye el namespace de Great Sage, que utiliza las traducciones originales incluidas por el autor.

Desde 2026.11.02.6 se retiran TR: Nightmare, NightmareUtils y TextAnimator. El motor descarga la herramienta de migración desde el mismo commit y verifica su SHA-256 antes de ejecutarla. Prepara los cambios de cada mundo con respaldo antes de reemplazar el pack; aplica los datos al finalizar y los restaura si falla la actualización. Los respaldos están en `.tensura-backups/<fecha>/retired-content/original`. Los slimes de Nightmare pasan al slime base de Tensura y sus demás razas a humano; se conservan los EP y el contenido de los demás mods. El cliente distribuido contiene 84 JAR, el orbe 1.3.0 y el paquete de español revisado.

## Comprobaciones realizadas

Desde 2026.11.02.8 el cliente incluye Controlling y Searchables, con 89 JAR. `config/tensura-keybindings.json` distribuye las prioridades de Tensura. `Preparar-Teclas.ps1` modifica únicamente las asignaciones enumeradas, conserva los controles vanilla y marca la revisión en `.tensura-client-release.json` al finalizar correctamente. Repetir la actualización respeta los cambios personales posteriores. Para publicar una distribución nueva de teclas, cambia `revision` en la política, actualiza la guía y publica normalmente el pack. La sustitución de opciones participa del respaldo y restauración del updater.

La publicación 2026.11.02.7 restaura Nightmare y sus dependencias, con 87 mods de cliente. Su política de retirada está desactivada y el motor tampoco la ejecuta si el manifiesto verificado incluye Nightmare, aunque quedase una política antigua. Se conservan los mundos en su estado actual. El orbe 1.3.1 admite nombres de audio de hasta 255 caracteres; la carpeta personal `orbe_audio` queda fuera de las carpetas reemplazadas por el updater.

El updater pasó instalación, repetición sin reemplazos, modo estricto, restauración tras un fallo provocado, reparación de cachés de mods y shaders, instalación de perfil y migración desde la versión publicada anterior. Un arranque directo con Java en un mundo aislado verificó el renderizado real de Unbound y el bloqueo de cambios mediante Iris y su API. El complemento de verificación usado en esa prueba no se distribuye a los jugadores.
