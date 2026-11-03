# Verificación del launcher 1.1.0 — 3 de noviembre de 2026

- Interfaz renderizada y revisada con selector de CPU, automático 80 % y 12/16 procesadores lógicos. Sin solapamientos de texto o controles.
- Migración de settings antiguos conserva nombre y memoria; selección manual y automática se guardan y se recuperan. El rango se valida contra la afinidad disponible.
- Java real recibe la cantidad esperada; se comprobó afinidad heredada y restauración de la del worker. La receta completa entrega 12 procesadores en automático y 3 en el caso manual de prueba.
- Bootstrap descarga todos los componentes con SHA-256, repara una fuente de UI alterada y prepara la interfaz sin solicitar ni restaurar un EXE. También se comprobó el host con autorrenovación solicitada: el modo scripts no intenta descargar un ejecutable.
- La distribución de scripts contiene BAT, bootstrap y LEEME, sin EXE. Los BAT administrados conservan Incremental/Strict, datos personales, rollback, reparación de cachés y migración de la versión anterior.
- No se midieron FPS ni se atribuyó un diagnóstico de Nightmare a estas pruebas. No se abrió una partida real ni se cambió la afinidad de procesos existentes. En sistemas con más de 64 procesadores se usa el grupo disponible para el launcher.

Prueba reproducible con JDK 21: `powershell -NoProfile -ExecutionPolicy Bypass -File updater/tests/Test-LauncherCpu.ps1`. El script compila un probe temporal y verifica Java y afinidad; no inicia Minecraft.

## Histórico: launcher 1.0.0

Pruebas realizadas el 2 de noviembre de 2026 en Windows 11 de 64 bits con la revisión del pack 2026.11.02.3.

- Compilación del EXE, guardado y lectura de nombre/memoria y argumentos de Windows con rutas que contienen espacios.
- Inspección de la interfaz en español mediante una captura de la ventana renderizada.
- Preparación desde el EXE: Java propio, 87 mods con SHA-256 correctos, 107 entradas de classpath, ocho bibliotecas nativas y 3.888 objetos de recursos únicos verificados.
- Arranque directo mediante Java y su archivo de argumentos, sin rutas de TLauncher en el comando. Minecraft cargó un mundo aislado y terminó con código 0.
- Cinco comprobaciones dentro del mundo: renderizado real con Unbound, bloqueo de desactivación, bloqueo de otro shader, persistencia del archivo de Iris y conservación del renderizado tras una solicitud de desactivación desde su API. Se utilizó un complemento de prueba que queda fuera de la distribución.
- Segunda actualización incremental sin reemplazo de carpetas administradas.
- Comparación SHA-256 de 1.249 archivos personales y partidas de la instalación original: sin cambios.
- Preparación con el origen de TLauncher ausente y Java fuera del PATH: descarga del ZIP oficial de Eclipse Temurin, comprobación SHA-256, instalación de Minecraft/NeoForge, descarga de un recurso faltante y reparación de una biblioteca alterada. Se reutilizó una caché verificada de archivos del pack para evitar volver a descargarlos todos.
- Sustitución del EXE con respaldo de la versión anterior y rechazo de una sustitución con hash incorrecto.
- Regresión de ambos BAT: actualización incremental y estricta, preservación de datos personales, restauración exacta tras un fallo provocado, reparación de cachés de mods/shaders/motor, instalación de plataforma y migración del updater anterior.

La prueba verifica el cliente y un mundo local. El launcher actual usa nombres locales para el servidor offline y no implementa inicio de sesión Microsoft. No se incluyeron cuentas, tokens, mundos ni binarios del juego en el ZIP publicado.
