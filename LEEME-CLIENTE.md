# Cliente de Tensura

Para recibir novedades de GitHub, utiliza `Tensura Mods Updater - Incremental.bat` con el juego y el launcher cerrados. El actualizador obtiene el último commit y verifica los archivos del pack con Packwiz. La variante sin «Incremental» fuerza la validación del pack.

Los dos BAT usan `Actualizar-Tensura-Cliente.ps1`, que debe permanecer a su lado. La carpeta predeterminada es `%APPDATA%\.tlauncher\legacy\Minecraft\game`. Para otra instalación, pasa la ruta como argumento:

```bat
"Tensura Mods Updater - Incremental.bat" -GameDir "C:\ruta\a\tu\juego"
```

El modo incremental comprueba Packwiz en cada ejecución y reemplaza las carpetas que cambiaron. La variante sin «Incremental» vuelve a sincronizar todas las carpetas distribuidas. Ambos modos descargan primero a una caché y guardan lo reemplazado en `.tensura-backups` dentro de la instalación. Si falla la aplicación, restauran los archivos anteriores. `repair` también fuerza la sincronización.

Los SHA-256 de todos los JAR se comparan con el manifiesto de cliente del mismo commit publicado. Si un mod de la caché está alterado, se vuelve a descargar antes de aplicarlo. Una caché con mods ajenos al manifiesto se rechaza antes de cambiar la instalación.

La actualización administra `mods`, `config`, `defaultconfigs`, `resourcepacks`, `shaderpacks`, `kubejs` y `scripts`. Los mods y las configuraciones se ajustan al pack publicado. Los paquetes de recursos y shaders personales se conservan; solo se sustituyen las versiones anteriores del paquete de español de Tensura. Las partidas, capturas y lista de servidores quedan fuera de las carpetas administradas. La selección de idioma conserva las demás preferencias de `options.txt`.

Minecraft y TL Legacy deben estar cerrados. Al terminar, se abre el launcher habitual. Para comprobar la actualización sin abrirlo, añade `-NoLaunch`. También puedes indicar `-CacheDir "C:\otra\cache"`; la caché debe estar separada de la instalación.

## Aplicar el paquete local sin conexión

1. Cierra Minecraft.
2. Extrae el ZIP de actualización completo, incluida la carpeta `mods` con `mods.sha256`.
3. Ejecuta `Aplicar-Tensura-Local.bat`. Para otra carpeta, usa `Aplicar-Tensura-Local.bat -GameDir "C:\ruta\a\tu\juego"`.

El instalador comprueba los hashes antes de cambiar archivos. Reemplaza las versiones anteriores de los mods incluidos, retira los vehículos y conserva los mods adicionales del cliente, como Sodium, las partidas y las demás opciones. Guarda una copia en `.tensura-backups` y selecciona `es_ar`. Si falla la instalación, restaura los archivos que había reemplazado.

Este procedimiento instala la copia contenida en el ZIP. Para futuras revisiones usa el actualizador remoto; no vuelvas a aplicar un ZIP antiguo.

Lee [GUIA-HABILIDADES.md](GUIA-HABILIDADES.md) para distinguir aprendizaje, maestría, EP y los puntos del árbol de Great Sage.
