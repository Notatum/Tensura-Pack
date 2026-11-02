# Cliente de Tensura

Para recibir novedades de GitHub, utiliza `Tensura Mods Updater - Incremental.bat` con el juego y el launcher cerrados. El actualizador obtiene el último commit y verifica los archivos del pack con Packwiz. La variante sin «Incremental» fuerza la validación del pack.

Los scripts usan `%APPDATA%\.tlauncher\legacy\Minecraft\game`. Si tu instalación está en otra ubicación, cambia `BASE`, `GAME` y `LAUNCHER` en el BAT antes de usarlo.

## Aplicar el paquete local sin conexión

1. Cierra Minecraft.
2. Extrae el ZIP de actualización completo, incluida la carpeta `mods` con `mods.sha256`.
3. Ejecuta `Aplicar-Tensura-Local.bat`. Para otra carpeta, usa `Aplicar-Tensura-Local.bat -GameDir "C:\ruta\a\tu\juego"`.

El instalador comprueba los hashes antes de cambiar archivos. Reemplaza las versiones anteriores de los mods incluidos, retira los vehículos y conserva los mods adicionales del cliente, como Sodium, las partidas y las demás opciones. Guarda una copia en `.tensura-backups` y selecciona `es_ar`. Si falla la instalación, restaura los archivos que había reemplazado.

Este procedimiento instala la copia contenida en el ZIP. Para futuras revisiones usa el actualizador remoto; no vuelvas a aplicar un ZIP antiguo.

Lee [GUIA-HABILIDADES.md](GUIA-HABILIDADES.md) para distinguir aprendizaje, maestría, EP y los puntos del árbol de Great Sage.
