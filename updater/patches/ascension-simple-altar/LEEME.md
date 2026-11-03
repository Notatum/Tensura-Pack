# Altar de Ascension sin estructura ni espera

En este pack el Altar del Despertar funciona colocando únicamente el bloque del altar. No requiere el piso ni los cuatro pilares, y no tiene tiempo de espera entre despertares, incluso si el jugador conserva la fecha de un ritual previo o una configuración antigua con `cooldownMinutes = 120`.

Se conservan catalizador y recetas, EP mínimos, estado de Verdadero Señor Demonio/Héroe, maestría, requisitos particulares de cada habilidad, regla de activación del mundo, elección aleatoria entre candidatas, duración y cancelación del ritual, bonificaciones y progreso del personaje. La modificación se aplica a todos los despertares de este altar.

El parche ASM cambia únicamente `AwakeningRitual` (validación, plano y dos lecturas de cooldown) y el valor predeterminado del cooldown en `AddonConfig`. Los textos del Códice y su plano muestran el altar solo; las traducciones se actualizan también en el paquete de recursos para evitar que sobrescriba las instrucciones nuevas. El archivo se conserva como `ascension-3.0.1.jar`, derivado de la versión traducida que ya distribuía el pack.

`build_patch.py` exige el SHA256 de esa entrada exacta y verifica las seis entradas modificadas del JAR. Necesita Java 21, Python y ASM/ASM-tree 9.10.1 en las bibliotecas del workspace. No ejecutar usando el mismo archivo como entrada y salida.

```powershell
python updater/patches/ascension-simple-altar/build_patch.py --original RUTA/ascension-3.0.1.jar --output RUTA-CANDIDATO/ascension-3.0.1.jar --resource-pack resourcepacks/Tensura-Espanol-2026.11.02.zip --resource-output RUTA-CANDIDATO/Tensura-Espanol-2026.11.02.zip
```

Distribuir el JAR y los textos a cliente y servidor mediante los manifiestos del pack. Actualizar con las instalaciones cerradas. La partida y el ejército de sombras no necesitan una migración de datos.

Verificación realizada en un servidor NeoForge aislado: 4 comprobaciones del comportamiento original y 21 del candidato, incluido altar sin estructura, compatibilidad con configuración antigua de 120 minutos, informe sin espera, inicio que consume catalizador, reemplazo por Monarca y un segundo despertar en el mismo tick. Se comprobaron también los rechazos por EP, estado, maestría, regla desactivada y requisito particular de Gran Mago. Los jugadores de prueba se prepararon en creativo para aislar los requisitos del altar de los costes de adquisición de habilidades. No se certificó visualmente la animación de 25 segundos en el cliente.
