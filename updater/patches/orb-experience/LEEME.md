# Orbe 1.3.3: recogida de experiencia del suelo

El pulso recoge los orbes de experiencia verde que estén dentro de su radio
actual, antes y después de hacer daño. Se usa la misma comprobación radial
que para objetos y enemigos: las primeras nueve formas mantienen su alcance
y la forma final conserva los 80 bloques de radio de la versión 1.3.2.

La experiencia se entrega al jugador mediante `ExperienceOrb.playerTouch`.
Así se conserva la recogida de Clumps, sus valores agrupados, los eventos de
recogida y Reparación. No se suman valores a mano, no se descartan entidades
por separado y no se transforma XP verde en EP del arma. Un jugador muerto,
espectador o de otra dimensión no recibe experiencia por este helper.

El constructor requiere el SHA256 exacto de Orbe 1.3.2. Añade una llamada
al comienzo de `OrbCombat.consumeGround`, una clase nueva y versión 1.3.3.
Las demás entradas del JAR quedan idénticas: daño, EP, evolución, radio,
audio/protocolo, NBT y protecciones de objetos.

Construcción con Java 21 y dependencias existentes del servidor:

`python build.py --server-root "C:\Servidor Tensura" --original "C:\Servidor Tensura\mods\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.2.jar" --work-dir "C:\Servidor Tensura\maintenance\orb-xp-tree-2026-11-05\build" --output "C:\Servidor Tensura\maintenance\orb-xp-tree-2026-11-05\candidate\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.3.jar" --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"`
