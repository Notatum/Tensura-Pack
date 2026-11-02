# Teclas del pack Tensura

La distribución da prioridad a Tensura, sus complementos y las dos armas personalizadas. Los accesos de mapas, inventarios adicionales y utilidades se apartan de esas teclas para evitar que una pulsación abra varias pantallas o active acciones distintas durante el combate.

El perfil `2026.11.02-teclas-1` se aplica **una sola vez por revisión** mediante el sistema administrado. Después puedes personalizarlo en **Opciones → Controles → Asignación de teclas**, con el buscador y las herramientas de Controlling. Las actualizaciones que mantengan esta revisión respetan tus cambios posteriores. Una revisión nueva del perfil podrá corregir de nuevo las asignaciones que publique el administrador.

Las teclas de movimiento, ataque, uso, inventario, barra rápida y demás controles originales de Minecraft conservan sus asignaciones. También se conservan los controles de JEI y las preferencias que no aparecen en este perfil.

## Tensura y habilidades

| Acción | Tecla |
| --- | --- |
| Menú principal de Tensura | **B** |
| Nombrar | **N** |
| Habilidad racial | **R** |
| Esquivar | **V** |
| Habilidad principal 1 | **Z** |
| Habilidad principal 2 | **Botón 4 del ratón**, normalmente lateral |
| Habilidad principal 3 | **C** |
| Siguiente modo de habilidad | **Alt izquierdo** |
| Habilidades secundarias 1, 2 y 3 | **1, 2 y 3 del teclado numérico** |
| Activar o desactivar todas las pasivas con Toggle All | **Botón 5 del ratón**, normalmente lateral |

Los números del teclado numérico son distintos de los números de la fila superior: la fila superior sigue seleccionando los objetos de la barra rápida. Los botones 4 y 5 del ratón usan la numeración que muestra Minecraft y suelen corresponder a botones laterales. Si tu teclado o ratón no tiene esos botones, puedes adaptar estas asignaciones desde Controlling, en Controles.

## Gran Sabio, armas y otros complementos

| Acción | Tecla |
| --- | --- |
| Menú de Gran Sabio | **G** |
| Aceleración del pensamiento | **H** |
| Acción de emergencia de Gran Sabio | **Supr** |
| Escanear con Gran Sabio | **J** |
| Evaluar con Gran Sabio | **U** |
| Percepción mágica de Gran Sabio | **Y** |
| Zoom del Arco de Beelzebuth | **X** |
| Menú de sonido y parlante del Orbe de Beelzebuth | **O** |
| Códice de Ascension | **Ctrl + L** |
| Consejo de Elite Tensura | **I** |
| Indicador de depuración de Elite Tensura | **F8** |

El atajo de voz de Gran Sabio queda sin asignar. Sus avisos y demás funciones conservan el comportamiento del mod original.

## Nightmare

| Acción | Tecla |
| --- | --- |
| Cosméticos | **K** |
| Progresión racial | **Coma** |
| Evolución de habilidades definitivas | **Punto** |
| Descansar en forma de dragón | **Punto y coma** |
| Gesto de dragón | **Apóstrofo** |
| Hablar con subordinados | **0 del teclado numérico** |
| Ranuras adicionales de arma de NightmareUtils 1, 2 y 3 | **4, 5 y 6 del teclado numérico** |

Las opciones disponibles dependen de tu raza, clan, habilidades y equipo. Las teclas de puntuación pueden aparecer con un símbolo distinto según la distribución del teclado; Controles muestra la asignación que reconoce el juego.

## Inventarios, mapas y utilidades

| Acción | Tecla |
| --- | --- |
| Abrir mochila | **F6** |
| Abrir Curios | **F7** |
| Abrir el mapa de Xaero | **M** |
| Crear un punto de referencia en Xaero | **F9** |
| Administrar puntos de referencia de Xaero | **F10** |
| Ajustes del minimapa de Xaero | **F12** |
| Menú de grupos y terrenos de Open Parties and Claims | **Multiplicar del teclado numérico** |

Quedan sin atajo la interacción adicional de mochila, los ajustes generales del mapa de Xaero, el segundo mapa de FTB Chunks, la bolsa de Ars Elemental y el carcaj de Supplementaries. También se quitan los atajos Alt + Z y Alt + X de las dos primeras mejoras de mochila, porque pulsar Alt cambia el modo de Tensura. Así liberan teclas que utilizan las habilidades. Puedes consultar sus opciones en las interfaces de cada mod o asignarles otra tecla desde Controles.

Los cuatro atajos de Iris quedan sin asignar. Unbound sigue seleccionado mediante la política del cliente administrado.

## Cómo revisar conflictos

Usa el buscador de Controlling para encontrar una acción por nombre y consulta su filtro de conflictos. Hay repeticiones de teclado que pertenecen a contextos distintos: por ejemplo, **R** activa la habilidad racial durante la partida y muestra recetas de JEI al consultar objetos en una interfaz. Esas coincidencias no significan que ambas acciones se ejecuten juntas durante el combate.

Si reasignas una habilidad, comprueba también los controles de sus complementos. En particular, evita reutilizar Alt como modificador de muchos atajos: Alt izquierdo ya cambia el modo de Tensura.

La política distribuida está en [`config/tensura-keybindings.json`](config/tensura-keybindings.json). El updater la entrega a todas las PC; cada instalación aplica una vez la revisión y conserva las personalizaciones posteriores.
