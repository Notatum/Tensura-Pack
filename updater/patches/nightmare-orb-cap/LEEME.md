# Compatibilidad de Nightmare con el daño del orbe

Nightmare incorpora topes de daño por golpe y un presupuesto de daño para jefes. Al restaurar el mod, esos límites reducirían el aporte lineal de EP del Orbe de Beelzebuth aunque su fórmula siguiera calculando el daño correcto.

Este adaptador excluye únicamente `devouring_orb:abyssal_pulse` del tope por golpe y del presupuesto. Conserva los límites para las demás fuentes de daño y mantiene las barreras de cambio de fase de los jefes. No añade al orbe la capacidad de ignorar invulnerabilidad ni modifica las configuraciones globales de Nightmare.

El constructor modifica dos consumidores de `DamageCapService.apply`, verifica que ambos existan y añade un adaptador. El resto de las clases y recursos del JAR de entrada se conservan. Las pruebas comprueban daño común, pulsos repetidos contra un jefe y una barrera de fase registrada.
