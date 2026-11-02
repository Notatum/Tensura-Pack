# Actualizador del servidor Tensura

Requiere Java 21 y el servidor detenido. Coloca el JAR y los BAT en la raíz del servidor. En Linux utiliza los scripts `.sh`.

Ejecuta `Actualizar-Tensura-Servidor.bat` para recibir la versión publicada en `Notatum/Tensura-Pack`, rama `main`. El actualizador consulta el commit y fija esa versión durante toda la descarga. No inicia el servidor por sí solo.

El commit se obtiene de la referencia pública de Git, sin necesitar Git instalado ni consumir la cuota de la API de GitHub. Se utiliza Packwiz Installer 0.5.14 desde su publicación oficial, la versión comprobada con este pack.

Para actualizar y después iniciar:

```bat
Iniciar-Tensura-Con-Update.bat java @user_jvm_args.txt @libraries/net/neoforged/neoforge/21.1.250/win_args.txt nogui
```

La descarga se prepara en `.tensura-updater/staging`. Los SHA-256 de todos los mods se comparan con `updater/manifests/mods-server.json` de la misma publicación. Un JAR alterado en la caché se elimina de esa caché y se vuelve a descargar. La verificación final rechaza archivos ajenos al manifiesto antes de cambiar `mods`.

Al aplicar cambios, los mods anteriores se guardan en `.tensura-updater/backups`. Se mantienen dos respaldos. El cambio de carpetas reintenta los bloqueos transitorios de Windows y restaura la carpeta anterior si no logra instalar la preparada. Los mods ya actualizados no se reemplazan otra vez.

Este actualizador administra `mods`. Conserva el mundo, las configuraciones y las propiedades del servidor. Los cambios de configuración del pack se deben revisar y aplicar por separado; la revisión 2026.11.02.1 ya sincronizó `config/ep-dungeon-server.toml` en este servidor.

`--local` utiliza expresamente `.tensura-updater/local-mods` y exige que todos sus archivos coincidan con `.tensura-updater/local-mods.sha256`. El modo remoto es el predeterminado. `--pack-url URL` permite otro origen del pack Tensura, que también debe ofrecer `updater/manifests/mods-server.json` junto a su `pack.toml`.

La selección para servidor excluye los mods marcados solo para cliente. Además se filtran Sodium y los antiguos vehículos; los vehículos se identifican por el ID declarado en el JAR.

El código fuente está en `TensuraServerUpdater.java`. Se compila con `javac -encoding UTF-8` y se empaqueta con `TensuraServerUpdater` como clase principal. La revisión se comprobó con Packwiz real, un mundo de prueba, una segunda ejecución sin cambios y una caché alterada intencionalmente.

La revisión 2026.11.02.6 distribuye 79 JAR y retira TR: Nightmare, NightmareUtils y TextAnimator. Antes de cambiar los mods, prepara y respalda los datos del mundo que pertenecen a Nightmare; luego aplica la retirada de sus dimensiones, objetos, habilidades y entradas persistentes. Conserva los EP y el contenido de los demás mods. Sus razas slime pasan al slime base de Tensura y las restantes a humano. Si falla la instalación, restaura los originales. Los respaldos de datos quedan en `.tensura-updater/backups/nightmare-*/original` y no se eliminan con la limpieza de respaldos de mods. No cambian `server.properties` ni el modo offline.

El JAR del updater incluye `RetiredContentMigration.java`, ubicado en `updater/shared`. Para compilar ambas clases y regenerar los artefactos y hashes usa `python updater/shared/build_tools.py --server-root RUTA_SERVIDOR`. Usa este JAR actualizado al instalar el updater de servidor en otra PC. La migración del cliente se descarga automáticamente mediante sus BAT o el launcher.

Desde la revisión 2026.11.02.5, al instalar el Great Sage original de Modrinth se respaldan sus dos archivos de configuración anteriores una sola vez. El mod regenera sus valores originales en el siguiente inicio; los ajustes posteriores se conservan. La copia anterior queda en `.tensura-updater/greatsage-vanilla-original-config-backup`. El mundo y sus reglas no se modifican.
