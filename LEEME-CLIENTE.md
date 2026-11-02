# Cliente de Tensura

## Entrada administrada del juego — revisión 2026.11.02.2

Ejecuta **`Tensura Mods Updater - Incremental.bat`** con el juego y Legacy Launcher cerrados. Ahora actualiza también su propio motor, prepara Minecraft **1.21.1 / NeoForge 21.1.250** y arranca el perfil **`Tensura-1.21.1-NeoForge-21.1.250`** automáticamente. Los cambios publicados en `main` se aplican en la siguiente ejecución en cada PC.

**Complementary Unbound es obligatorio en este cliente administrado.** Se distribuye `ComplementaryUnbound_r5.3.zip` y se mantiene seleccionado y activado mediante la política del pack y el complemento de cliente. La selección anterior apuntaba a un archivo r5.9 con Euphoria que no estaba instalado. La activación se restaura con cada actualización y se conserva al usar los controles de Iris durante el juego.

Las copias anteriores del updater reciben automáticamente la migración al ejecutar una actualización normal. Para distribuir a otra PC conserva juntos los dos BAT y `Actualizar-Tensura-Cliente.ps1`. Usa `-NoLaunch` para actualizar sin abrir el juego. El procedimiento y las instrucciones para publicar futuros cambios están en [updater/client/LEEME.md](updater/client/LEEME.md).

Para recibir novedades de GitHub, utiliza `Tensura Mods Updater - Incremental.bat` con el juego y el launcher cerrados. El actualizador obtiene el último commit y verifica los archivos del pack con Packwiz. La variante sin «Incremental» fuerza la validación del pack.

Los dos BAT usan `Actualizar-Tensura-Cliente.ps1`, que debe permanecer a su lado. La carpeta predeterminada es `%APPDATA%\.tlauncher\legacy\Minecraft\game`. Para otra instalación, pasa la ruta como argumento:

```bat
"Tensura Mods Updater - Incremental.bat" -GameDir "C:\ruta\a\tu\juego"
```

El modo incremental comprueba Packwiz en cada ejecución y reemplaza las carpetas que cambiaron. La variante sin «Incremental» vuelve a sincronizar todas las carpetas distribuidas. Ambos modos descargan primero a una caché y guardan lo reemplazado en `.tensura-backups` dentro de la instalación. Si falla la aplicación, restauran los archivos anteriores. `repair` también fuerza la sincronización.

Los SHA-256 de todos los JAR se comparan con el manifiesto de cliente del mismo commit publicado. Si un mod de la caché está alterado, se vuelve a descargar antes de aplicarlo. Una caché con mods ajenos al manifiesto se rechaza antes de cambiar la instalación.

La versión se obtiene de la referencia pública de Git de la rama `main`; no requiere tener Git instalado ni depende de la cuota de la API de GitHub. Se utiliza Packwiz Installer 0.5.14, la versión comprobada con este pack, descargada desde su publicación oficial.

La actualización administra `mods`, `config`, `defaultconfigs`, `resourcepacks`, `shaderpacks`, `kubejs` y `scripts`. Los mods y las configuraciones se ajustan al pack publicado. Los paquetes de recursos y shaders personales se conservan; solo se sustituyen las versiones anteriores del paquete de español de Tensura. Las partidas, capturas y lista de servidores quedan fuera de las carpetas administradas. La selección de idioma conserva las demás preferencias de `options.txt`.

Minecraft y TL Legacy deben estar cerrados. Al terminar, Legacy Launcher inicia el perfil administrado. Para comprobar la actualización sin abrirlo, añade `-NoLaunch`. También puedes indicar `-CacheDir "C:\otra\cache"`; la caché debe estar separada de la instalación.

## Entrada local anterior

`Aplicar-Tensura-Local.bat` utiliza ahora el mismo updater administrado de GitHub. Requiere conexión para comprobar la publicación y evita reinstalar una copia antigua del pack. También admite `-GameDir` y `-NoLaunch`.

Las instalaciones administradas usan los mods y las configuraciones publicados. Guardan una copia en `.tensura-backups` y restauran los archivos reemplazados si falla la aplicación. Los ZIP antiguos de instalación local conservan la revisión con la que fueron creados; distribuye la entrada administrada actual para recibir los cambios siguientes.

Para las otras PC, distribuye los BAT y `Actualizar-Tensura-Cliente.ps1` juntos, o el ZIP de entrada administrada. No hace falta volver a copiar el paquete completo de mods para cada publicación.

Lee [GUIA-HABILIDADES.md](GUIA-HABILIDADES.md) para distinguir aprendizaje, maestría, EP y los puntos del árbol de Great Sage.
