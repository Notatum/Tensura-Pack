# Arco: daño por evolución y EP de las bajas

El Arco de Beelzebuth 1.4.8 usa daño plano **10, 20, 30, 40, 50 y 60** en sus seis formas. El aporte adicional es lineal: **EP guardado en el arco × 0,00002** (20 de daño por millón), el doble del coeficiente del orbe. El disparo real y el tooltip consultan la misma fórmula. La carga conserva su curva y Poder aporta 1,2 por nivel; se mantienen rayos secundarios, alcance, efectos y tiempos del arco.

Al confirmar una baja del propio arco, se le acredita el incremento real de EP máximo base que recibió el arquero durante ese daño, después de las reglas, topes y modificadores que intervengan. Jugador y arco conservan esa ganancia. Se eliminan la restricción por namespace y la estimación del 8 % del EP guardado de la víctima. Se acredita al arco que disparó, una sola vez por muerte confirmada; golpes no letales, daños/muertes cancelados, blancos ya muertos y bajas sin recompensa de EP no generan puntos. Se conserva la exclusión anterior de víctimas jugador.

Se preservan EP fraccionarios, UUID, ledger de comida, etapas antiguas, umbrales de evolución, irrompibilidad y protección de ambas armas frente a la alimentación. El constructor exige la entrada 1.4.6 con SHA256 `9ac27d6713166e87a157b4e42f00753c12a0b127fd8d532ddc7b5520ac148c68`. Cambia el manejador de bajas, redirige la fórmula compartida y el rótulo del daño plano, añade BowBalance y actualiza metadata/textos. No reconstruir desde una versión que carezca de la protección del orbe.

```powershell
python updater/patches/bow-kill-scaling/build_patch.py --original RUTA/Arco-de-Beelzebuth-neoforge-1.21.1-1.4.6.jar --output-dir RUTA-CANDIDATO --resource-pack resourcepacks/Tensura-Espanol-2026.11.02.zip
```

Requiere Java 21, Python, ASM 9.10.1 y las bibliotecas de Minecraft/NeoForge del workspace. Publicar el JAR nuevo, retirar el anterior de los manifiestos y actualizar el paquete de idioma y el índice de Packwiz.

La versión 1.4.7 pasó 29 comprobaciones en servidor aislado, incluida la carga de las gamerules desde una copia del mundo limpio, daño de las seis formas, carga/Poder, EP fraccionarios y etapas antiguas, bajas reales de orco y vaca con coincidencia entre el EP del personaje y del arco, ausencia de premio duplicado y casos cancelados/no letales/sin EP. La revisión 1.4.8 duplica únicamente el coeficiente de EP del helper; se conserva el manejador de bajas. Sus 16 comprobaciones en NeoForge verifican la proporción exacta con el orbe, las seis bases, la fórmula compartida, carga/Poder y conservación de datos. Se compararon los JAR: únicamente difieren BowBalance y la versión de metadata. No se abrió una partida real del usuario.
