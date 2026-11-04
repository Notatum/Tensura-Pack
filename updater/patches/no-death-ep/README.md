# No Death EP Loss 1.1.0

Mod propio `nodeatheploss` para Minecraft 1.21.1 / NeoForge 21.1.250. Reemplaza el JAR anterior NoDeathEPLoss 1.0.0, conservando su modid y función de carga. Se distribuye en ambos lados para cubrir servidor dedicado y servidor integrado. No modifica los JAR oficiales de Tensura, Ascension ni Nightmare.

La versión 1.0.0 era un datapack que fijaba `epDeathPenalty=0` al cargar datos. La versión 1.1.0 conserva esa función por compatibilidad y añade protección autoritativa para jugadores del servidor en las rutas auditadas de pérdida de EP máximo y de descuentos de energía actual asociados a muertes y resurrecciones:

| Ruta | Protección |
| --- | --- |
| Tensura `ExistenceStorage.applyDeathPenalty` | Cancela únicamente ese método para `ServerPlayer`, aunque la regla se cambie después a un porcentaje positivo. Conserva el resto de la limpieza de muerte. |
| Ascension `ModifierEffects.onDeath` | Omite la rama de jugador que crea la pérdida y su Echo compensatorio. Conserva la rama de muerte de otros seres que beneficia al atacante con Open Wounds. |
| Ascension `ModifierEffects.tick` | Su única llamada a `EpUtil.takeMaxEp` corresponde a la pérdida de muerte pendiente. Se evita ese cobro; el código original retira después el tag `loss`. No se bloquea la utilidad global ni otros costes voluntarios. |
| Nightmare `WitchesEnvySkill.restoreSavedResources` | Sus dos escrituras de bases máximas no pueden reducir el progreso actual al valor de un savepoint antiguo. Sus escrituras de aura/magículas tampoco descuentan energía por volver a ese punto. Salud espiritual, posición y demás reglas permanecen en el método original. |
| Nightmare `WitchesEnvySkill.applyReturnByDeathPenalty` | Impide reducir el EP actual por la penalización explícita de muerte. Permite una restauración hacia arriba. Evita una escritura sin cambio de suma que redistribuya aura/magículas artificialmente. |
| Nightmare `TimeTravelerHelper.applyMaxPenalty` | Mismo límite para sus dos escrituras de máximos y sus dos descuentos de aura/magículas, usadas por las muertes de Time Traveler, Yog Sothoth y Yog Sotohort. No se cancela el helper completo. |
| Mysticism `DreamerSkill.onDeath` → `restoreStats` | Sus dos escrituras de recursos no pueden reducir aura/magículas durante la restauración de muerte. Las restauraciones voluntarias por el mismo helper mantienen su comportamiento original. HP/SHP, posición y cooldown se conservan. |
| Nightmare `PhoenixSkill` / `PhoenixNextSkill` | Se protegen únicamente las escrituras de recursos en sus lambdas de `onDeath`. Las restauraciones hacia arriba, salud, contador de resurrecciones y límite original del Fénix permanecen activos. |

El Echo de Ascension formaba parte de la pérdida de muerte: conservar su recompensa sin cobrar esa pérdida crearía una fuente gratuita de EP por suicidio. Por eso se omite junto con el castigo. Una pérdida de muerte ya pendiente se descarta cuando el código de Ascension intenta cobrarla; no se recuperan pérdidas antiguas ya aplicadas ni se conceden EP retroactivos.

La protección se aplica a progreso de EP y a descuentos de aura/magículas causados por esas penalidades de muerte, no a cada cambio global de energía consumible. No modifica gastos normales de habilidades, costes voluntarios, resets/reencarnación voluntarios, contador de resets, limpieza de HP/ataque de Dragon Slayer/Sealer ni la retirada de formas temporales. En particular, no conserva un snapshot bruto del cuerpo dragón que pudiera perpetuar su multiplicador de EP por cuatro.

Los mixins de Nightmare, Ascension y Mysticism son opcionales si esos mods no están instalados. Las versiones compatibles están acotadas a las auditadas y los redirects exigen exactamente sus números conocidos de escrituras. El constructor verifica SHA256 de los cuatro JAR originales antes de compilar. No hay datos de jugadores, contadores globales, comandos ni hooks de prueba en el JAR de producción.

Dreamer comparte su helper entre muerte y uso voluntario. Un contexto `ThreadLocal` identifica solo al jugador de la llamada síncrona desde `onDeath`; un `finally` retira el contexto o restaura el contexto anterior si la llamada está anidada. Otros jugadores y otros hilos mantienen su comportamiento. No se establece un suelo global para los setters de energía.

Ascension usa el archivo `ascension-3.0.1.jar`, pero su metadata interna declara la versión del mod `1.0.0`. La dependencia de este mod se acota a `[1.0.0,1.0.1)` para que NeoForge pueda resolver el JAR real; el constructor también exige su SHA256 auditado. El nombre del archivo no reemplaza la versión declarada al cargador.

Nightmare declara la versión completa `1.0.6.1-neoforge-1.21.1`; la dependencia opcional usa ese valor exacto, incluido su calificador de plataforma.

Mysticism declara internamente `2.2.1`; la dependencia opcional exige esa versión y el constructor valida el JAR corregido auditado por su hash.

Para construir con un JDK Java 21:

```powershell
python updater/patches/no-death-ep/build.py `
  --server-root "C:\Servidor Tensura" `
  --work-dir "C:\Servidor Tensura\maintenance\startup-review-2026-11-04\server\ep-audit\build" `
  --output "C:\Servidor Tensura\maintenance\startup-review-2026-11-04\server\ep-audit\candidate\NoDeathEPLoss-neoforge-1.21.1-1.1.0.jar" `
  --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
```

El constructor solo escribe artefactos privados bajo `maintenance`. Ordena entradas y fija las fechas ZIP para obtener un JAR reproducible; no instala, publica ni modifica manifiestos. Las pruebas funcionales de baseline/candidato se ejecutan por separado con jugadores ficticios y mundos privados, sin datos reales.

El alcance corresponde a las rutas auditadas de Tensura 2.0.1.4, Ascension 3.0.1, Nightmare 1.0.6.1 y Mysticism 2.2.1. Una habilidad nueva que cambie atributos máximos o recursos por otro mecanismo requiere revisión adicional; no se debe prometer cobertura de código futuro desconocido.
