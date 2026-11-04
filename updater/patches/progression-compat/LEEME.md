# Compatibilidad de progresión 1.1.0

## Beelzebuth personalizado

Bloquea la adquisición de `trnightmare:beelzebuth` para jugadores, incluido
el aprendizaje normal y la copia directa a SkillStorage. Su comprobación
automática devuelve falso antes de consumir Gula/Despiadado. El ID permanece
registrado para que las partidas y referencias de Nightmare sigan cargando.
La habilidad distinta `trnightmare:beelzebub` conserva sus reglas.

Otorga `beelzebuth:beelzebuth` al entrar y en comprobaciones cada 20 ticks
si el jugador vivo tiene al menos 2.000.000 EP máximos base y Predator y Gula
aprendidos de forma permanente. No exige maestría, despertar ni altar, no
consume EP ni esas dos habilidades. No renueva ni reinicializa una habilidad
ya aprendida. El JAR del Beelzebuth personalizado 1.3.8 queda intacto.

Los poseedores de la habilidad duplicada se convierten una vez a la propia,
conservando maestría, temporalidad y progreso de modos compatibles. Se guarda
la instancia original completa en PlayerPersisted/tensura_progression_compat
antes de retirar el duplicado. No se copian etiquetas privadas incompatibles
al nuevo tipo. Una habilidad ya obtenida no se pierde por estar debajo del
umbral de adquisición nuevo. Las instancias en aprendizaje conservan ese
estado hasta que cumplan los requisitos; las temporales siguen siendo temporales.

## Contador de reencarnación

Reconoce las seis razas divinas finales de dragón de Nightmare 1.0.6.1 como
despertar válido para el contador de reinicios de Tensura 2.0.1.4. Se comprueba
también que la raza no tenga evoluciones posteriores. La misma regla se aplica
en el pergamino del servidor y en el progreso mostrado en el menú cliente.

Conserva los despertares nativos Héroe/Señor Demonio, la evolución final,
los jefes requeridos y las opciones del contador. No escribe banderas, EP,
inventarios ni razas del jugador. No modifica otros rituales o requisitos.

Construcción con Java 21 y las dependencias del servidor auditadas:

`python build.py --server-root "C:\Servidor Tensura" --work-dir "C:\Servidor Tensura\maintenance\beelzebuth-progression-2026-11-04\build" --output "C:\Servidor Tensura\maintenance\beelzebuth-progression-2026-11-04\candidate\tensura-progression-compat-1.1.0.jar" --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"`
