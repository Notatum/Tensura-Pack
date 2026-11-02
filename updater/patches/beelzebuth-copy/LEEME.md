# Beelzebuth 1.3.8 — copia instantánea y Voz del Mundo

Esta corrección corresponde a **beelzebuth:beelzebuth**, la habilidad definitiva propia del pack. Se conservan el estómago persistente, sus menús y migraciones, los cuatro modos y el parche de niebla/bloques de la versión 1.3.7.

## Comportamiento

Al mantener Depredación Abisal, cada objetivo vivo dentro del cono de 25 bloques aporta sus habilidades adquiridas en el mismo tick del servidor. Se copian resistencias, intrínsecas, comunes, extras, únicas y definitivas. También se conservan la maestría y el progreso de aprendizaje de los modos. La maestría mayor que ya tenga el receptor se mantiene.

La copia no tiene la espera de cinco minutos compartida por objetivo. Dos jugadores pueden copiar el mismo objetivo y una habilidad adquirida después por ese objetivo se incorpora durante la siguiente depredación. Las comprobaciones repetidas sin novedades no generan avisos duplicados.

Las instancias se crean mediante la fábrica concreta de cada habilidad. Las habilidades nuevas empiezan con sus tiempos de reutilización y activaciones propios, sin heredar los del objetivo. Cada habilidad ejecuta su inicialización normal; un fallo de una inicialización se registra y permite continuar el resto del lote. Los datos del objetivo y los inventarios privados de ambos jugadores se conservan. Las habilidades temporales mantienen su duración restante. Las condiciones propias de uso de cada habilidad siguen vigentes.

Después de la copia se notifica a **SkillAnnouncer** de Great Sage y se registra el descubrimiento mediante **SageProgress.sawSkills**. La adquisición queda completada antes de esos avisos. Great Sage agrupa las habilidades recibidas y reproduce sus anuncios en secuencia; ese tiempo de presentación no retrasa la copia. Se respetan las reglas y preferencias de anuncios del propio complemento.

Se mantiene el mecanismo de copia especial de Beelzebuth, que no vuelve a cobrar la adquisición ni impone los filtros normales de compra de habilidades. No se habilita el overlay alternativo de Elite Tensura.

## Política publicada

El archivo fuente **config/beelzebuth-copy.json** define si se copian también las habilidades que el objetivo todavía tiene en aprendizaje. El valor publicado es **includeLearningSkills: false**: se copian las habilidades ya adquiridas por el objetivo. Si el receptor tenía una de ellas sin aprender, la adquiere de inmediato con la maestría del objetivo.

Al compilar, la política se incorpora al JAR en **META-INF/beelzebuth-copy.json**. Esa copia es la autoridad de la distribución administrada y viaja con las actualizaciones de cliente y servidor. Así el servidor recibe la misma política aunque su updater conserve los archivos locales de configuración. Para cambiarla en todas las PC, editá el archivo fuente, reconstruí el JAR y publicá la nueva revisión. En una distribución sin política incorporada se usa el archivo local, con valor predeterminado false.

## Reconstruir y publicar

1. Con Java/JDK 21 y una instalación de servidor disponible, ejecutá desde la raíz del repositorio: `python updater/patches/beelzebuth-copy/build_patch.py --server-root "C:\Servidor Tensura"`. La base 1.3.7 se comprueba por SHA-256 y puede descargarse del commit original verificado.
2. Revisá el JAR y el manifiesto generados en **maintenance/beelzebuth-build**. El indicador **--include-learning** genera una variante para probar la política alternativa.
3. Reemplazá la versión anterior en **mods**, actualizá los manifiestos SHA-256 de cliente y servidor, ejecutá las pruebas y **packwiz refresh**, y publicá el commit en main.

## Verificación

La versión 1.3.7 reprodujo doce fallos en la prueba inicial. La corrección pasó 27 comprobaciones dentro de NeoForge con jugadores de prueba, un mob y captura real de los paquetes de anuncios de Great Sage. Se comprobó la transferencia en un tick, las categorías, las instancias, maestría, modos, estados, dos receptores, nuevas habilidades del objetivo, latentes según política, ausencia de avisos repetidos, fallos de inicialización provocados, guardado/carga, duración de habilidades temporales y la ruta real de mantener Depredación Abisal.

El complemento de prueba **updater/tests/BeelzebuthCopyVerification.java** se utiliza únicamente en el entorno aislado y no se distribuye a los jugadores.
