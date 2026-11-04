# Compatibilidad del contador de reencarnación

Reconoce las seis razas divinas finales de dragón de Nightmare 1.0.6.1 como
despertar válido para el contador de reinicios de Tensura 2.0.1.4. Se comprueba
también que la raza no tenga evoluciones posteriores. La misma regla se aplica
en el pergamino del servidor y en el progreso mostrado en el menú cliente.

Conserva los despertares nativos Héroe/Señor Demonio, la evolución final,
los jefes requeridos y las opciones del contador. No escribe banderas, EP,
inventarios ni razas del jugador. No modifica otros rituales o requisitos.

Construcción con Java 21 y las dependencias del servidor auditadas:

`python build.py --server-root "C:\Servidor Tensura" --work-dir "C:\Servidor Tensura\maintenance\progression-cosmetics-2026-11-04\build" --output "C:\Servidor Tensura\maintenance\progression-cosmetics-2026-11-04\candidate\tensura-progression-compat-1.0.0.jar" --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"`
