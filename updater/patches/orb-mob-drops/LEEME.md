# Orbe 1.3.6: botín de mobs y cristales

Corrige la distinción entre el mob que suelta un objeto y su propietario
jugador. El filtro anterior rechazaba cualquier `thrower` distinto del
jugador, incluidos mobs. El defecto se reprodujo con las tres calidades
de cristal y con botín de una baja real de Tensura.

El parche registra el UUID de una fuente no jugadora cuando Minecraft
ejecuta `ItemEntity.setThrower(Entity)`. Esa marca persiste al guardar y
cargar el objeto; no requiere que el mob siga vivo o cargado. Un jugador
que vuelve a soltar el objeto elimina la marca anterior. La absorción
mantiene primero las reservas, pertenencias de muerte y protección de
ambas armas personalizadas. Las fusiones conservan la clasificación.

Los cristales conservan sus valores: baja 12 EP, media 32 EP y alta 90 EP
por unidad. La corrección también permite el botín normal de mobs. Todas
las entradas de cola, daño, radio, XP, catálogo de Nightmare, audio y datos
del arma quedan byteidénticas a 1.3.5.

Los objetos antiguos cuyo origen todavía puede comprobarse en memoria se
reconocen. Si un objeto guardado antes del parche tiene un UUID de origen
pero ya no existe evidencia de si era un mob o un jugador, se conserva su
protección. Para esos cristales viejos, recogerlos normalmente y volver a
soltarlos con Q permite absorberlos. No se liberan UUID desconocidos que
puedan pertenecer a jugadores desconectados.

63 comprobaciones NeoForge: tres calidades, botín propio y ajeno, guardado
y carga, muerte y retirada del mob, reservas, revalidación en cola,
fusiones reales, cambio de lanzador y armas protegidas. Una baja real de
orc emitió los tres tipos de cristal durante LivingDropsEvent y el mismo
pulso los consumió. No se midieron FPS nuevos.

El constructor requiere Orbe 1.3.5 SHA256
`643ce41532dbfc48eb60000ec480be35d424e2309b9fe59fa5ee6975280f06f7`.
Modifica OrbFoodGuard, metadata de versión y configuración de mixins;
añade ItemSourceAccessor e ItemDropSourceMixin.

`python build.py --server-root "C:\Servidor Tensura" --original "C:\Servidor Tensura\mods\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.5.jar" --work-dir "C:\Servidor Tensura\maintenance\orb-crystals-2026-11-05\build" --output "C:\Servidor Tensura\maintenance\orb-crystals-2026-11-05\candidate\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.6.jar" --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"`
