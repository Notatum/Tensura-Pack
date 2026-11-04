# Límite de escritura de worldgen de Ars Elemental

Mod independiente `tensura_worldgen_safety` 1.0.0 para Minecraft 1.21.1, NeoForge 21.1.250 y Ars Elemental 0.7.10.3. Debe distribuirse en ambos lados para cubrir servidor dedicado y partidas individuales. No modifica ni redistribuye un JAR derivado de Ars Elemental.

`BlackstoneFormation` puede intentar cimentar su formación fuera de los chunks que `WorldGenRegion` permite escribir. Minecraft rechaza esas operaciones y registra un error por bloque; el bucle continúa descendiendo. El arreglo conserva la guarda original de 32 bloques y añade el límite real de la región activa.

- `WorldGenRegionAccessor` lee `generatingStep` y `center`. El radio se obtiene de `ChunkStep.blockStateWriteRadius()`: no se fija en 1 ni se deduce del origen del feature.
- Dos redirects de `isTooFar` añaden solamente el límite horizontal de la región. Esto permite que `fillPos` esté en la altura máxima si la escritura efectiva a `fillPos.below()` todavía es válida.
- Seis redirects de `LevelAccessor.setBlock` comprueban la posición exacta que se escribirá. Incluyen el límite vertical de generación cuando el chunk central está en upgrading; también cubren lava y limpieza de aire.
- Fuera de `WorldGenRegion`, las guardas devuelven paso libre y se ejecutan los métodos originales. No se ajustan los parámetros del generador ni se sustituye su RNG. La prevención de intentos rechazados puede terminar antes la continuación inútil de un cimiento que ya no puede escribir y cambiar el consumo de aleatoriedad de esos intentos sin efecto. No se promete que los bloques de terreno futuro sean idénticos frente al algoritmo defectuoso.
- No se llama `ensureCanWrite`, porque esa comprobación también registra el error. No se cambian niveles globales de logging ni se ocultan errores de otros features.

Los redirects exigen exactamente 2 y 6 coincidencias; el mixin es requerido. La dependencia de Ars está acotada a 0.7.10.3 y el constructor valida su SHA256 conocido. Ante otra versión, se debe revisar primero el bytecode. No hay contadores, comandos, eventos de tick ni hooks de prueba en el JAR de producción.

Para construir, usar Java 21 y pasar rutas privadas explícitas:

```powershell
python updater/patches/worldgen-write-bounds/build.py `
  --server-root "C:\Servidor Tensura" `
  --work-dir "C:\Servidor Tensura\maintenance\startup-review-2026-11-04\server\build" `
  --output "C:\Servidor Tensura\maintenance\startup-review-2026-11-04\server\candidate\tensura-worldgen-safety-1.0.0.jar" `
  --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
```

El constructor solo permite generar clases/JAR dentro de `maintenance`, usa entradas ordenadas y fechas ZIP fijas, y ejecuta 165.636 comprobaciones de coordenadas positivas/negativas, alineaciones, radios -1/0/1/2/4 y alturas. No instala, publica ni modifica manifiestos. La validación de mixins y del feature real se hace aparte en un servidor privado; sus fuentes y resultados son diagnósticos privados, no forman parte del mod.

La mejora de FPS/MSPT y del tiempo total de arranque requiere una medición comparativa posterior. Este arreglo evita una ruta de escrituras rechazadas durante generación de terreno; no promete reducir todas las fases de carga del juego.
