# Verificación del launcher 1.3.0 — 4 de noviembre de 2026

## Revisión del pack 2026.11.04.2

El EXE 1.3.0 conserva exactamente los bytes de la publicación anterior. Se actualizan el motor y los componentes del pack mediante sus hashes; no hace falta descargar otro launcher.

- Veintiuna comprobaciones privadas del updater/motor: mapas de hashes ya verificados, comprobación final conservada, corrupción del mismo tamaño/fecha detectada, DLL coincidentes bloqueadas sin reescritura, sustitución atómica y limpieza ante fallos.
- Siete comprobaciones de `build-launcher.py --metadata-only`: discrepancias de versión, fuentes o EXE rechazadas sin mutación, componentes y ZIP coherentes, ejecutable conservado. La versión se deriva de la fuente revisada.
- Tres pares alternados de preparación en una instalación privada con caché: medianas 15,737 segundos antes y 13,811 después. No es un benchmark del arranque completo, caché fría ni FPS.
- Managed Client 1.0.1: dos compilaciones idénticas, quince comprobaciones de construcción/metadata/guardia de tecla, diez de caché de shaders y tres de geometría original. La versión interna completa de Nightmare se valida al construir.
- Cliente NeoForge real con Unbound: comparación de nueve vistas dentro del mundo y nueve en vuelo por fase. El baseline reproduce slime invisible, cuernos ausentes y varillas de alas sin membranas. El candidato muestra las tres superficies; mantiene los mods de culling y el shader. La última ejecución carga los 91 JAR del cliente más un helper privado, termina sin fallos en siete comprobaciones y verifica diez mil consultas de tecla desasignada sin error GLFW. El helper y sus mundos no se distribuyen.
- No Death EP Loss 1.1.0: veintiséis comprobaciones pareadas en cada fase, sin fallos. Incluyen penalidad100, muerte/respawn reales de un jugador de prueba, descuentos de Ascension pendientes, puntos antiguos de Witches/Time Traveler, Dreamer y ambas variantes de Phoenix, restauraciones hacia arriba y costes voluntarios. El contexto de Dreamer se limpia incluso ante excepción y se aísla por jugador/hilo. El multiplicador temporal del cuerpo dragón se retira normalmente.
- Worldgen Safety 1.0.0: 165.636 casos puros de límites y quince comprobaciones en NeoForge; comparación de 42.250 posiciones contra los límites reales de generación y seis ejecuciones del feature. No hubo escrituras rechazadas ni errores de far-chunk en la prueba final. Se preserva el comportamiento fuera de WorldGenRegion y se comprueban alturas de upgrading.
- Regresión completa del updater: veinte comprobaciones aprobadas con los 91 JAR actuales, ambos BAT, Incremental/Strict, reparación selectiva, cachés, migración, datos personales y rollback exacto. Auditoría de los 91 JAR: 475 dependencias requeridas, 473 rangos y 292 archivos indexados, sin errores.

Estas pruebas verifican la compatibilidad y las incidencias concretas descritas. No certifican cada combinación cosmética ni establecen FPS comparativos. Las pruebas se ejecutan en datos privados de mantenimiento, con los JAR originales de Gran Sabio y los mods instalados preservados.

## Histórico: publicación inicial 2026.11.04.1

La distribución vigente es `Tensura-Launcher.exe` y `Tensura-Launcher-1.3.0.zip`.
El EXE incluye la interfaz, admite actualización de su mismo archivo y sigue
usando CPU automático80 o selección manual. No usa el ejecutable histórico.

- Diez comprobaciones de actualización: código actual con versión de fixture anterior actualiza al candidato, reemplazo atómico/.previous, settings exactos, proceso desacoplado sin bloqueo, SHAincorrecto rechazado, downgrade ignorado, puente desde el binario1.2 ya distribuido, identidad/datos conservados, receta90mods y ninguna petición del EXE retirado.
- El primer ensayo detectó un bloqueo circular de pipes en Windows PowerShell5.1; se corrigió separando el worker mediante ShellExecute. La repetición completa pasó.
- Veinte comprobaciones de distribución: ambos BAT, coincidencia exacta de90JAR, actualizaciones Incremental/Strict sin alterar IDs/fechas de los JAR correctos, reparación de archivos faltantes/dañados y retirada con respaldo, rollback exacto, datos personales, cachés de mods/shaders/motor y migración del bootstrap anterior.
- Once comprobaciones del perfil: límites iniciales de renderizado/entidades/simulación, ajustes menores, personalizaciones posteriores, FPS y teclas, valores inválidos y ausencia de escrituras parciales.
- Servidor NeoForge privado con82mods: cinco comprobaciones funcionales; ServerCore interpreta objetivo40MSPT y un único ajuste de vista6..8, IA por rango desactivada y Nightmare/Gran Sabio conservados. Mundo privado y puerto25587; nunca se cargó la partida real.
- Auditoría del cliente completo90JAR:472dependencias y470rangos válidos; sin archivos dañados, dependencias faltantes ni errores de índice. Entity Culling1.11.3 procede de Modrinth oficial, solo cliente, con tickCulling desactivado y excepciones para entidades de mods.
- Defender completó el escaneo del candidato con protección activa y ninguna detección nueva. El resultado local no establece un falso positivo histórico ni garantiza clasificación en otros equipos. El EXE sigue sin firma digital.

En la publicación04.1 no se midieron FPS comparativos y la prueba gráfica quedó
pendiente porque el cliente real seguía abierto y había6.3GB libres. La revisión
04.2 descrita arriba añadió las pruebas visuales privadas tras su cierre; no se
cerraron el juego, launcher ni servidor del usuario por cuenta del mantenimiento.

## Histórico: launcher 1.2.0

La distribución vigente es `Tensura-Launcher.exe`, compilado desde
`src/TensuraLauncher.cs` con el compilador de .NET Framework de Windows, x64,
WinForms y manifiesto `asInvoker`. Incluye la interfaz y no necesita archivos
a su lado. También está en `Tensura-Launcher-1.2.0.zip` junto con las instrucciones.
El EXE no está firmado digitalmente.

- Abierto desde una carpeta que contiene solamente el EXE, con espacios y ñ en la ruta. SelfTest de CPU, configuración e identidad aprobado.
- Interfaz completa renderizada y revisada; la ventana real de prueba expone el título 1.2.0 y responde. El controlador de interacción de Windows no estuvo disponible, por lo que no se certifica aquí una prueba manual de los botones.
- Errores de conexión y de integridad provocados: salida de error y detalle en el registro. Se agregó captura de errores previos a crear la interfaz y registro de los fallos de inicialización.
- Preparación aislada desde el EXE: Minecraft 1.21.1, NeoForge 21.1.250, 89 mods con los SHA-256 del manifiesto actual y CPU automático80. No se inició Minecraft ni se tocó el servidor activo.
- Eliminada la sustitución automática del ejecutable y la entrada histórica `application`. El motor sigue actualizándose con archivos verificados. El EXE anterior en cuarentena y el ZIP histórico `TensuraLauncher-Portable.zip` no son la distribución vigente y no se reutilizan.
- Escaneo personalizado de Defender completado con protección y análisis en tiempo real activos: ninguna detección nueva sobre el candidato. Este resultado local no establece que la detección histórica fuera un falso positivo ni garantiza la clasificación en otras PC.
- La alternativa de scripts conserva sus pruebas de descarga, reparación, settings y Java real con automático12/16 y manual3. Las pruebas actuales no miden FPS ni abren una partida real.

Reconstrucción en Windows de64bits: `python updater/launcher/build-launcher.py`.
El constructor genera un EXE nuevo y hashes; debe analizarse antes de ejecutar
o distribuir. No recupera el binario retirado ni cambia el antivirus.

## Histórico: launcher de scripts 1.1.0

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
