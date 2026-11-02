# Cosméticos de Nightmare en español

Este parche muestra en español las etiquetas de la pantalla de cosméticos, incluidos los tipos y cuerpos de dragón. Envuelve únicamente las llamadas que dibujan o miden texto de `CosmeticScreen`.

Los nombres ingleses de las opciones también se utilizan internamente para decidir los linajes y las formas de dragón. Por eso no se modifican `DragonFormTypes`, los JSON de cosméticos, los IDs, los paquetes de red ni los valores guardados. La selección sigue usando los valores originales; la traducción se aplica al mostrarlos.

Se mantiene intacto todo el código y los recursos del JAR de entrada salvo `CosmeticScreen.class`; se añade una sola clase de texto. El constructor verifica esa condición antes de dar el resultado por válido. No modifica Great Sage.

Requiere Java 21 y ASM de la instalación existente:

```text
python updater/patches/nightmare-cosmetics-spanish/build_patch.py --server-root RUTA_SERVIDOR --original ORIGINAL.jar --output SALIDA.jar
```

Este parche acompaña al mod completo. La pantalla depende de los registros de razas, estados cosméticos, transformación de dragón, modelos y red de Nightmare; no constituye una extracción independiente de sus cosméticos.
