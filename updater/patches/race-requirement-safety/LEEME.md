# Consultas de razas ausentes al adquirir habilidades y reencarnar

Los cierres del cliente del 02/11/2026 mostraron un proveedor de `trstarlight:aspect_of_regression` sin valor cuando Nightmare evalúa la habilidad Lethe. También se registró una excepción de `mysticism:insect` al elegir una raza en la reencarnación.

Este parche comprueba la presencia de la raza requerida antes de evaluar Lethe y devuelve requisito no cumplido cuando falta. En el callback de Mysticism, la comparación opcional con Insect usa `orElse(null)`; las razas disponibles mantienen la comparación y el sorteo habituales. No crea razas, no cambia requisitos satisfechos ni modifica datos del jugador, EP, inventarios o habilidades adquiridas. Nightmare y Great Sage permanecen en sus versiones vigentes.

El constructor requiere los JAR derivados actualmente distribuidos, preserva sus arreglos anteriores y verifica que cambie exactamente una clase en cada archivo. No reconstruir a partir del original oficial si eso elimina las traducciones o la reparación anterior de evolución de insectos.

La guarda evita las dos excepciones observadas. La causa de que un proveedor falte en una sesión concreta debe distinguirse de esta protección; no se afirma una corrección general de todos los registros ni de todas las causas de FPS bajos.
