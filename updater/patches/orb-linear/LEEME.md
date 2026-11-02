# Orbe de Beelzebuth 1.3.0

El daño por enemigo y pulso se calcula así:

`daño = 5 × (1 + evoluciones alcanzadas) + EP acumulado × 0,00001`

El orbe empieza con 5 de daño base. Cada una de sus nueve evoluciones añade otros 5: la forma final tiene 50 de base. El aporte de EP equivale al **0,001 %** del total acumulado: 1 millón aporta 10; 10 millones, 100; 100 millones, 1.000. Después de alcanzar la forma final, cada EP nuevo conserva el mismo aporte. No se aplican logaritmos ni un tope de balance.

El combate, el tooltip y el menú de sonido comparten la misma fórmula. El tooltip desglosa base y EP; el daño se presenta con hasta seis decimales para mostrar incrementos pequeños. Los EP y el cálculo exacto se conservan como `BigDecimal`. Minecraft recibe el resultado con su precisión de daño habitual (`float`); su límite numérico sigue existiendo a valores extremos.

Se conservan los IDs de objetos, UUID, EP, contabilidad de alimentos, bajas, evoluciones obtenidas, radios, tiempos de reutilización y protecciones de propiedad. El daño base también respeta las evoluciones heredadas de versiones antiguas.

`build_patch.py` verifica el SHA-256 del orbe 1.2.1 con las protecciones de objetos antes de compilar. Sustituye los tres consumidores de daño mediante ASM y comprueba que cada sustitución se haya aplicado una sola vez. Usa Java 21 y las bibliotecas de NeoForge/ASM de `--server-root`.

Las pruebas del servidor están en `updater/tests/CustomModsVerification.java`. Comprueban los diez niveles de base, incrementos iguales de daño al añadir EP, fracciones, persistencia, evolución heredada y las protecciones del orbe, el arco y la dungeon.
