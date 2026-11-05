# Orbe 1.3.5: recogida por tandas y catálogo de Nightmare

Extiende el Orbe 1.3.4 exacto (SHA256
`8047802bba0538ae034ad6ced85760a49f2fc626a5a0362bc09ea3b5e5fca69b`).
No cambia el JAR de Nightmare ni el de Gran Sabio.

La cola de servidor ahora incluye la recogida inicial y final de XP y
objetos. Comparte un presupuesto blando de 6 ms entre operaciones, hasta 32
acciones totales y hasta 8 impactos por tick entre todos los jugadores.
El lanzamiento toma referencias, muestra la onda y reproduce el sonido;
las recogidas y bajas se completan después. El daño conserva una copia al
lanzar más la comida previa de ese mismo pulso; cambios de EP posteriores
no lo amplifican. El alcance se captura con la forma inicial.

Cada recogida vuelve a validar entidad, radio y propiedad. Los objetos
consumidos acreditan sus cantidades/EP exactos antes de retirarse. Morir,
desconectarse, cambiar de dimensión o perder el orbe deja las entidades
pendientes intactas y conserva los créditos ya aplicados. Siguen el límite
de dos pulsos y la resolución por UUID dentro del inventario. No se
programa trabajo inútil sobre pertenencias ajenas o armas protegidas.
La XP sigue pasando por `ExperienceOrb.playerTouch`: Clumps, Reparación,
eventos y retirada conservan la implementación original.

El perfil JFR privado detectó una enumeración completa del registro de
habilidades en cada golpe, dentro de la lista de evoluciones de Nightmare.
Un mixin opcional reutiliza únicamente el catálogo de IDs, con copia para
cada consumidor e invalidación al cargar/recargar datos y detener el
servidor. **No guarda resultados ni requisitos del jugador.** La evaluación
de requisitos, avisos, aprendizaje, EP y recompensas continúa en el código
original. No se conservan catálogos vacíos ni se comparten entre servidores.

Pruebas NeoForge: 78 comprobaciones (54 regresiones y 24 de recogida y
catálogo). 500 objetos y 500 orbes de XP se recogieron en 16 pasos cada
uno; activación de 0,760/0,463 ms y máximo de paso de 6,944/4,428 ms,
respectivamente. Los pasos incluyen los otros listeners de tick del pack.
100 consultas del catálogo: 69,410 ms reconstruyendo y 0,0368 ms
reutilizando, con 114 entradas idénticas.

El presupuesto se comprueba entre operaciones. Un callback de daño, una
recogida individual de Clumps o un mod externo todavía puede excederlo:
el máximo observado con 50 blancos fue 95,995 ms. No es una garantía de cero
tirones ni una medición de FPS. Las consultas espaciales iniciales siguen
en el hilo del servidor; no se manipulan entidades desde hilos auxiliares.

Construcción reproducible:

`python build.py --server-root "C:\Servidor Tensura" --original "C:\Servidor Tensura\mods\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.4.jar" --work-dir "C:\Servidor Tensura\maintenance\orb-pickup-2026-11-05\build" --output "C:\Servidor Tensura\maintenance\orb-pickup-2026-11-05\candidate\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.5.jar" --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"`
