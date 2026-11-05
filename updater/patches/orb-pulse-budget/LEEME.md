# Orbe 1.3.4: impactos repartidos entre ticks

El pulso anterior procesaba todas las bajas en la misma llamada. En el
servidor privado con el pack completo, 50 zombis produjeron una mediana de
411 ms y un máximo de 878 ms; el pulso vacío tuvo una mediana de 0,146 ms.
Son pruebas sintéticas, con callbacks de combate en calentamiento, no FPS
del usuario ni un perfil histórico de su sesión.

Ahora el sonido, la onda y el cooldown comienzan al lanzar. Los impactos se
reparten en el hilo del servidor con presupuesto global de 6 ms entre
operaciones y hasta 8 acciones por tick, compartido entre jugadores. Un
callback individual de un mod no se puede interrumpir: el presupuesto no
es un límite duro de tiempo. En una prueba, los 50 blancos se completaron
en 51 pasos y lanzar tardó 2,49 ms; el paso más largo fue 112 ms por un
callback individual. No se afirma una mejora equivalente de FPS.

Se conservan el radio de cada forma, el daño capturado al lanzar, la
selección de enemigos, aliados, propiedad del arma, fuente de daño,
créditos de OrbKills y recogidas de objetos/XP con Clumps/Reparación. Los
blancos se vuelven a validar al procesarlos. Muerte, desconexión, cambio
de dimensión o pérdida del arma cancelan los impactos restantes sin
eliminar esos blancos ni sus drops. Mover el arma dentro del inventario
conserva el trabajo mediante su UUID.

Se admiten dos pulsos pendientes por jugador. Un tercero se rechaza con
un mensaje, evitando crecimiento ilimitado. Los fallos de un callback se
registran y liberan el trabajo afectado. La recogida inicial y final sigue
siendo síncrona; esta revisión corrige el bloqueo medido de muchas bajas.

El constructor exige Orbe 1.3.3 SHA256
`93bccee3a4f0751474e6d80daad6f07c92cf60b157f76efc5edda2d7ee73850b`.
Modifica el pulso, acceso de dos helpers, versión y dos claves nuevas por
idioma. Añade OrbPulseWork y Job. Resto de entradas, incluidos XP, daño,
radios, audio/protocolo4, NBT, modelos y seguridad, permanecen idénticos.

`python build.py --server-root "C:\Servidor Tensura" --original "C:\Servidor Tensura\mods\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.3.jar" --work-dir "C:\Servidor Tensura\maintenance\orb-lag-2026-11-05\build" --output "C:\Servidor Tensura\maintenance\orb-lag-2026-11-05\candidate\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.4.jar" --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"`
