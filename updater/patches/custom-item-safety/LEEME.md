# Protección de objetos de la dungeon y las armas

Revisión 2026.11.02.1 para Minecraft 1.21.1, NeoForge 21.1.250 y Java 21.

| Mod | Versión anterior | Versión corregida |
| --- | --- | --- |
| EP Dungeon | 1.0.32 | 1.0.33 |
| Arco de Beelzebuth | 1.4.5 | 1.4.6 |
| Orbe de Beelzebuth | 1.2.0 | 1.2.1 |

`PatchCustomItemSafety.java` aplica cambios puntuales al bytecode original: desactiva el filtro que eliminaba todos los objetos del piso 6, protege ambas armas al alimentar el arco y corrige dos comentarios de configuración. El bloqueo del botín de mobs permanece en `NoDropHandler` y `LivingEntityNoDropsMixin`.

El orbe conserva su lógica de combate y progreso. Antes de consumir un objeto del suelo, `OrbFoodGuard` comprueba su ID y su propietario. Nunca consume el arco ni otro orbe. Para los demás objetos, respeta el UUID de quien los arrojó, la reserva de recogida y el marcador de muerte del jugador. Los objetos sin dueño y los del propio usuario siguen siendo alimento válido.

`OrbDropOwnership` marca los objetos que deja un jugador al morir sin cambiar las reglas de recogida ordinarias. Los mixins leen los UUID sin serializar cada entidad y evitan que una unión de pilas borre la identidad del dueño. Los objetos del mismo dueño siguen agrupándose. Los objetos antiguos sin información de propiedad no permiten reconstruir retrospectivamente quién los dejó.

No cambian los IDs de los objetos, los formatos de progreso, los umbrales de evolución, el daño ni los protocolos de red de las armas. Los EP y la evolución se conservan en los objetos existentes. El addon de dungeon mantiene su excepción anterior de Charybdis para el reinicio; el comentario ahora la explica.

La prueba `updater/tests/CustomModsVerification.java` se ejecutó en un servidor dedicado aislado: **77 comprobaciones correctas**, incluidos EP fraccionarios, evolución, ambas manos, propiedad, muerte, serialización, unión de pilas, recogida normal y botín de mobs. El mod de pruebas no se distribuye a los jugadores.

Para recompilar se necesitan los tres JAR originales, el JDK 21 y las bibliotecas del servidor. Coloca los originales en `before/mods` junto a `build_fixes.py` o indica `--original-mods`. Ejecuta `python build_fixes.py --server-root "C:\Servidor Tensura"`. Las salidas quedan en `release/mods`, con su manifiesto SHA-256. Las fuentes de `src` acompañan al parche.
