# Orbe 1.3.2: 30 bloques adicionales de radio solo en la forma final

Suma 30 bloques al radio de pulso y absorción únicamente en la décima forma:

| Forma | Antes | Ahora |
| --- | ---: | ---: |
| 1 | 15 | 15 |
| 2 | 18 | 18 |
| 3 | 22 | 22 |
| 4 | 25 | 25 |
| 5 | 29 | 29 |
| 6 | 32 | 32 |
| 7 | 36 | 36 |
| 8 | 39 | 39 |
| 9 | 43 | 43 |
| 10 | 50 | 80 |

Es la distancia desde el centro: 30 más a cada lado, 60 más de diámetro. Se
conserva la comprobación de distancia radial; no se convierte el área en un
cubo. Combate, absorción, tooltip, onda visual y alcance de distribución del
efecto consultan el mismo método. El radio de audio configurado sigue igual.

El constructor exige el SHA exacto del orbe 1.3.1 y cambia únicamente
OrbBalance.class y la versión 1.3.2 en metadata. Cambia solo la última entrada
de la tabla RADII de 50 a 80 y actualiza MAX_RADIUS a 80. El método radius(stage)
conserva su implementación. Todos los demás métodos y entradas
del JAR conservan su lógica: daño, EP, evoluciones, UUID, NBT, protección de
objetos, pertenencias, cadencia, música y protocolo 4. No migra partidas.

Construcción con Java 21:

`python build.py --server-root "C:\Servidor Tensura" --original "C:\Servidor Tensura\mods\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.1.jar" --work-dir "C:\Servidor Tensura\maintenance\orb-range-2026-11-04\build" --output "C:\Servidor Tensura\maintenance\orb-range-2026-11-04\candidate\Orbe-de-Beelzebuth-neoforge-1.21.1-1.3.2.jar" --java-home "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"`

`tests/RangeVerification.java` ejecuta las clases reales de ambos JAR mediante
classloaders separados: comprueba radios, fronteras, forma radial, progresión
visual, daño y valores de balance conservados. Compilar con Java 21 y ejecutar
`RangeVerification <JAR anterior> <JAR nuevo> <informe.json>`. No inicia Minecraft.
