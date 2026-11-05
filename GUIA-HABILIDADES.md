# Aprendizaje, maestría y progresión de habilidades

Guía para este pack: Minecraft 1.21.1, NeoForge 21.1.250, Tensura 2.0.1.4 y el complemento Great Sage 0.0.7.1. Revisión del 2 de noviembre de 2026.

## Qué significa cada progreso

| Indicador | Qué representa | Cómo avanza |
| --- | --- | --- |
| Puntos de aprendizaje | Aprender una habilidad que todavía aparece en gris o aprender uno de sus modos. | Intenta usar la habilidad o el modo equipado. El requisito general de este servidor es 100 puntos. |
| Maestría | Dominar una habilidad que ya aprendiste. Puede mejorar su efecto o habilitar modos. | Realiza las acciones que reconoce esa habilidad: activarla, acertar un ataque, mantenerla o disparar su efecto pasivo. |
| XP y puntos del árbol de Great Sage | Mejoras del complemento: análisis, alcance, tareas simultáneas y otras ramas. | Descubre contenido y completa análisis. No es la maestría de la habilidad Gran Sabio. |
| EP | Puntos de existencia del personaje, usados en requisitos de poder y evolución. | Es un sistema distinto del aprendizaje y de la maestría. |

La experiencia verde de Minecraft tampoco sustituye ninguno de estos indicadores.

## De una habilidad gris a una habilidad aprendida

1. Abre el menú de habilidades y selecciona una habilidad que tengas disponible. Revisa sus requisitos y su costo de adquisición.
2. Equípala en una ranura y usa la tecla asignada a esa ranura. Puedes consultar o cambiar las teclas en **Opciones → Controles**.
3. Espera a que termine el tiempo de reutilización y vuelve a intentarlo. La barra muestra puntos de aprendizaje; llegar al requisito permite aprenderla.
4. Si la habilidad tiene varios modos, comprueba cuál está seleccionado. Un modo puede necesitar su propio aprendizaje, maestría previa u otra condición.

En esta configuración, la ganancia normal de aprendizaje es **1 punto base más una bonificación aleatoria de 0 a 4**. El tiempo de reutilización de aprendizaje general es de 10 segundos. La energía disponible, los estados alterados y los requisitos particulares también pueden impedir una activación. La configuración contempla penalizaciones de aprendizaje; no confundas un intento de aprendizaje con un análisis del complemento.

Adquirir determinadas habilidades puede reducir la **reserva máxima de magículas**, además de los costos de energía de sus acciones. Mira el costo concreto: aprender una habilidad no siempre equivale a gastar únicamente la energía que luego se regenera.

## Cómo subir la maestría

Después de aprenderla, usa la habilidad de una forma que active su efecto. Pulsar una tecla mientras el movimiento está bloqueado, sin energía o fuera de alcance no garantiza progreso.

La configuración general tiene una ganancia base de maestría de 1 y multiplicadores de 2 para impactos y muertes. Las acciones concretas que conceden puntos dependen de la habilidad; no significa que todos los ataques ni todas las activaciones den siempre la misma cantidad. Para habilidades que se mantienen pulsadas, el intervalo general es de **60 ticks, unos 3 segundos a 20 TPS**. Algunas pasivas conceden progreso cada **10 activaciones de su efecto**, no cada diez segundos.

Umbrales configurados para las categorías siguientes:

| Categoría | Maestría máxima habitual |
| --- | ---: |
| Intrínseca | 100 |
| Extra | 500 |
| Única | 1.000 |
| Única de pecado | 1.500 |
| Definitiva | 10.000 |

Los complementos y algunas habilidades pueden fijar otros límites. La barra de la habilidad es la referencia para el caso concreto. Llegar a la maestría máxima no concede automáticamente su evolución: puede faltar EP, una raza, una habilidad previa, una condición o un evento.

## Habilidades que aceleran el entrenamiento

Los siguientes valores están comprobados en el código y en `config/tensura/ability/skill/unique_config.toml` de esta revisión:

| Habilidad | Con su pasiva activada |
| --- | --- |
| Analista | Añade 2 a la ganancia de aprendizaje y 2 a la de maestría. |
| Cocinero — Cook | Añade 4 a ambas ganancias. |
| Gran Sabio | Añade 9 a ambas ganancias. |
| Luchador — Fighter | Permite aprender al instante técnicas de voluntad de combate y magia; añade 2 a la ganancia de maestría. Tiene condiciones de uso y no concede todas las habilidades del juego. |

Tener la habilidad en la lista no equivale a tener su pasiva activada. Las bonificaciones de atributos de Analista, Cocinero y Gran Sabio se suman mientras estén activadas. Por ejemplo, Gran Sabio convierte una ganancia base de maestría de 1 en 10, antes de otros modificadores; en aprendizaje sigue existiendo la bonificación aleatoria configurada.

**Great Sage se distribuye en su versión oficial, sin el acceso universal del pack.** Su menú, análisis y comentarios espontáneos respetan los requisitos originales de Gran Sabio o su evolución reconocida. Para obtener la bonificación de +9 del mod base debes tener y activar la habilidad correspondiente.

## Copiar habilidades con Beelzebuth

Esta explicación corresponde a **Beelzebuth, Señor de la Gula**, la habilidad definitiva propia del pack con ID `beelzebuth:beelzebuth`.

Se obtiene automáticamente cuando tenés **2.000.000 de EP máximos base**, **Predator (Depredador)** y **Gula** aprendidos permanentemente. La comprobación se realiza al entrar y aproximadamente cada segundo: no exige dominarlas, despertar como Héroe/Señor Demonio ni usar un altar. No consume EP ni esas dos habilidades. La versión duplicada `trnightmare:beelzebuth` queda deshabilitada para jugadores; quien ya la tenga pasa a la propia con su maestría conservada y una copia de sus datos anteriores. La habilidad diferente Beelzebub de Nightmare conserva sus reglas.

Seleccioná **Depredación Abisal** y mantené pulsada la tecla de la habilidad mientras el objetivo esté delante tuyo, dentro del cono de **25 bloques**. Las habilidades que el objetivo ya tiene adquiridas se incorporan en un único tick del servidor, junto con su maestría y el progreso de sus modos. Incluye resistencias, intrínsecas, comunes, extras, únicas y definitivas. Si tu maestría era mayor, se conserva.

Podés volver a copiar al mismo objetivo cuando aprenda algo nuevo. Cada jugador recibe su propia copia, sin quitar habilidades al objetivo. Las habilidades nuevas tienen sus tiempos de reutilización limpios; las condiciones particulares para activar sus efectos siguen aplicándose. Los estómagos y los objetos guardados son personales y se conservan.

Las adquisiciones nuevas generan los avisos de la **Voz del Mundo de Great Sage**. El cliente agrupa los avisos durante aproximadamente medio segundo y los presenta en secuencia; las habilidades ya están copiadas mientras se reproducen. Repetir la depredación sin novedades no repite los anuncios.

La política actual copia las habilidades **ya aprendidas por el objetivo**. Si vos tenías una de ellas en gris, pasa a estar aprendida de inmediato. Las habilidades que el propio objetivo todavía tiene en aprendizaje quedan sujetas a la política publicada `config/beelzebuth-copy.json`; por defecto no se desbloquean mediante esta copia. Una habilidad temporal conserva la duración restante del objetivo.

## El árbol del complemento Great Sage

El complemento tiene su propio nivel y sus propios puntos. Su código registra, antes de multiplicadores configurables, estas recompensas base:

| Acción reconocida por el complemento | XP base del árbol |
| --- | ---: |
| Registrar una especie nueva | 25 |
| Registrar una habilidad nueva | 10 |
| Completar un análisis | 50 |
| Evaluar un objeto nuevo | 1, 3, 8 o 20 según su rareza |

Las especies, habilidades y objetos ya registrados no se vuelven a considerar descubrimientos nuevos. El árbol calcula sus umbrales según el contenido instalado; no uses una tabla de niveles de otro servidor como si fuera idéntica. Los puntos disponibles se calculan como **nivel del árbol × 8 menos puntos gastados**.

Para el análisis, `analysisTicks = 1200` equivale a un minuto base a 20 TPS. Con un solo análisis, sin mejoras ni excepciones, los factores actuales dan estas duraciones orientativas: intrínseca o común, 2 minutos; extra, 3; única, 5; definitiva, 8. Una habilidad todavía latente aplica un factor de tiempo de 3 y un costo adicional de magículas del 75 %. El tiempo real depende del servidor y de las mejoras.

- **Concentración** aumenta la capacidad total de análisis y permite completarlos más rápido.
- **Hilos** permite más análisis simultáneos. Comparten esa capacidad: abrir más tareas no multiplica la velocidad total.
- No puedes utilizar una habilidad mientras está siendo analizada.
- Los rangos de análisis se desbloquean mediante los nodos originales del árbol; se retiró el parche que los habilitaba gratuitamente para todos.
- La configuración original establece una probabilidad base de fallo del análisis del **25 %**. **Precisión** reduce esa posibilidad y **Segunda mirada** puede recuperar parte del coste. Se conservan las condiciones y los valores que establezca el autor.

## Si una habilidad parece no subir

Comprueba, en este orden: si la barra es de aprendizaje o maestría; si elegiste el modo correcto; si terminó la reutilización; si tienes energía; si la habilidad exige objetivo, alcance o impacto; si su pasiva está activada; y si alcanzaste ya el límite. Para una evolución que diga **Dominar X**, necesitas completar la maestría de X, no solo aprenderla ni subir el árbol.

## Referencias y diferencias de este pack

### Orbe de Beelzebuth: daño y EP propios

Los EP guardados en el orbe pertenecen al arma y son distintos de los EP del personaje. En la versión 1.3.0 su daño por enemigo y pulso es:

`daño = 5 × (1 + evoluciones alcanzadas) + EP del orbe × 0,00001`

Empieza con 5 de daño base y cada evolución añade otros 5. La décima forma, tras nueve evoluciones, tiene 50 de base. Cada millón de EP aporta 10 de daño adicional, también después de la última evolución. Por ejemplo, la forma final con 10 millones de EP causa 150 de daño antes de las defensas del objetivo; al añadirle otros 10 millones pasa a 250. El tooltip distingue daño base, aporte de EP y daño total. Desde el orbe 1.3.2, solo la forma final gana 30 bloques de radio para pulso y absorción: pasa de 50 a 80 bloques desde el centro, con 160 de diámetro. Las primeras nueve formas conservan sus radios de 15, 18, 22, 25, 29, 32, 36, 39 y 43 bloques. La cadencia y el porcentaje de EP recibido por bajas se conservan.

### Arco de Beelzebuth: evolución y EP de bajas

Desde el arco **1.4.8**, sus seis formas tienen **10, 20, 30, 40, 50 y 60 de daño plano**. Con carga completa y sin Poder, el daño principal es `10 × número de forma + EP del arco × 0,00002`. Cada millón de EP añade 20 de daño, el doble del aporte del orbe; la forma final con 10 millones tiene 260 antes de las defensas. Los rayos secundarios mantienen su porcentaje.

Cuando el arco mata una criatura y el personaje recibe EP por esa baja, el arco acumula también esa ganancia real. Los EP se guardan en el arma que disparó y siguen aumentando después de la última forma. Se conserva la ganancia del personaje y el progreso previo del arco. Los golpes sin muerte o las bajas sin recompensa no aportan EP al arco.

En el mundo del servidor se activaron `keepInventory=true` y `resetCounterBonusUnique=1`: se conserva el inventario al morir y cada punto del contador de reencarnaciones completas de Tensura añade una habilidad única al sorteo habitual. Los requisitos originales para incrementar ese contador se mantienen.

Desde **2026.11.04.6**, los talentos de Ascension de **Ctrl+L** son permanentes entre reinicios de personaje: se conservan el árbol comprado, los puntos disponibles y su progreso. También persisten al morir y volver a entrar; sus efectos se reaplican sin acumularse. Esto incluye los pergaminos de reinicio completo, de raza y de habilidades. La redistribución voluntaria dentro del propio árbol sigue permitiendo reasignar los talentos.

### Requisitos de Ascension

Las habilidades definitivas de Ascension usan sus propios límites; su códice indica 100 puntos de maestría, a diferencia de los valores generales de Tensura. Los niveles del Sistema y los puntos de maestría también son indicadores distintos. Lee el requisito específico de cada habilidad y el detalle del Altar del Despertar en el códice: además de dominar la habilidad única, exige poder y condiciones adicionales. La meditación del códice puede reducir el aura máxima de forma permanente; no es un gasto de energía que se regenere.

Desde la revisión **2026.11.03.2**, el Altar del Despertar funciona colocando solo el bloque: no necesita piso ni pilares y no tiene cooldown entre despertares. Arrojá un Catalizador Definitivo encima, esperá a que lo absorba y hacé clic derecho con la mano vacía. Se conservan los 5.000.000 de EP máximos, el estado de Verdadero Señor Demonio o Verdadero Héroe, la única dominada y sus requisitos particulares. El ritual dura 25 segundos; permanecé cerca hasta que termine. Si hay varias habilidades aptas, el altar elige una al azar. Cada nuevo ritual consume otro catalizador.

TR: Nightmare y NightmareUtils vuelven al pack desde 2026.11.02.7. El menú de cosméticos se abre con K por defecto; las opciones disponibles dependen de la raza, el clan y las habilidades. Se conserva el estado actual de los personajes y se mantienen los respaldos anteriores.

El Orbe 1.3.1 conserva la misma fórmula de daño. Su menú de audio permite nombres de archivo de hasta 255 caracteres: coloca la música en `orbe_audio/musica` de la instalación activa, entra en Parlante y pulsa Actualizar. Selecciona el archivo y pulsa Reproducir. El botón vuelve a leer la carpeta sin reiniciar el juego.

### Experiencia del orbe y tala de árboles

Desde **2026.11.05.1**, el pulso del Orbe de Beelzebuth recoge también la **experiencia verde del suelo** dentro de su alcance actual. La recibe el jugador y sirve para Reparación como una recogida normal; Clumps conserva todas las unidades agrupadas. No se convierte en EP del orbe. La forma final conserva 80 bloques de radio y las otras formas su alcance anterior.

**FallingTree:** rompé el tronco inferior de un árbol con un hacha para talar los troncos conectados. **Agachate para cortar un solo tronco.** Se aplica el desgaste oficial según la cantidad talada. El árbol debe tener hojas naturales; las hojas siguen su caída normal. El límite es 100 troncos totales por tala y la exploración se limita a 500 bloques. Clumps ya estaba incluido en el pack.

### Fuentes

La mecánica general se contrastó con [Ability Usage, en la wiki de Tensura](https://tensura.wiki.gg/wiki/Mechanics/Ability_Usage) y [Great Sage, en la wiki](https://tensura.wiki.gg/wiki/Great_Sage). La consulta directa de la wiki devolvió HTTP 403; se pudo consultar su contenido indexado. Los números específicos de esta guía se comprobaron también en la configuración y en el código de los JAR instalados.

La página de Gran Sabio describe la habilidad del mod base. El árbol, sus puntos, los hilos y el análisis pertenecen al [complemento oficial Great Sage](https://modrinth.com/mod/great-sage), que se distribuye sin modificaciones. Los comentarios de compañía requieren la habilidad; los anuncios de adquisición y de la Voz del Mundo conservan el comportamiento original del complemento.
