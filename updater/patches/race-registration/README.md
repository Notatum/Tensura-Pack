# Registro paralelo de razas

Corrección de compatibilidad para Minecraft 1.21.1, NeoForge 21.1.250,
Architectury 13.0.11 y ManasCore Race 4.0.0.2. No reemplaza sus JAR.

Varios constructores de addons llaman simultáneamente al mismo
`RaceRegistry.RACES.register`. Architectury mantiene un `HashMap` del proveedor
y un `LinkedHashMap` de pendientes sin sincronización. Las escrituras paralelas
pueden perder entradas o romper la cadena que recorre el evento de registro.
El resultado puede variar entre arranques con archivos idénticos y provocar
`Value missing` o desconexiones por claves desconocidas.

Un `WrapMethod` de MixinExtras, incluido en NeoForge, ejecuta el método original
bajo el monitor del mapa compartido solo cuando la clave es
`manascore_race:races`. Cubre juntos la selección del contenedor y la inserción;
el monitor se libera también si el original lanza una excepción. Otros registros
siguen su camino original. No modifica fábricas, IDs, proveedores, requisitos,
razas de jugadores, sincronización de red ni la validación de claves.

Construir en una carpeta privada con JDK 21:

```powershell
python build.py --server-root 'C:\Servidor Tensura' `
  --work-dir 'C:\Servidor Tensura\maintenance\race-registry-2026-11-05\build' `
  --output 'C:\Servidor Tensura\maintenance\race-registry-2026-11-05\candidate\tensura-registry-compat-1.0.0.jar' `
  --java-home 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
```

Instalar por los manifiestos administrados en ambos lados; requiere volver a
iniciar el proceso afectado para que sus constructores se ejecuten con la guarda.
