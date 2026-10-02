# Modernización de Iconos de Aplicación

Se han rediseñado y actualizado los iconos de las tres aplicaciones del ecosistema para dotarlas de una apariencia más profesional, atractiva y coherente con su funcionalidad.

## Nuevos Diseños de Iconos

````carousel
![Icono Calculadora Drywall](file:///D:/Proyectos/DrywallMaterialsCalculator/app/src/main/res/drawable/ic_launcher_foreground.xml)
<!-- slide -->
![Icono Keygen Pro](file:///D:/Proyectos/DrywallMaterialsCalculator/keygen/src/main/res/drawable/ic_launcher_foreground.xml)
<!-- slide -->
![Icono Trial Cleaner](file:///D:/Proyectos/DrywallMaterialsCalculator/cleaner/src/main/res/drawable/ic_launcher_foreground.xml)
````

### 1. Calculadora Drywall (`:app`)
- **Estilo**: Construcción Moderna.
- **Visual**: Esquina de panel de yeso en gris neutro con una calculadora profesional en azul corporativo y una espátula de acabado. Transmite precisión y herramientas de grado industrial.

### 2. Keygen Pro YHQuintero (`:keygen`)
- **Estilo**: Seguridad Premium.
- **Visual**: Escudo de seguridad en azul noche profundo con una llave criptográfica dorada central. Incluye detalles de circuitos digitales y la letra "P" de profesional, evocando exclusividad y potencia administrativa.

### 3. Trial Cleaner (`:cleaner`)
- **Estilo**: Mantenimiento y Utilidad.
- **Visual**: Flecha circular de reinicio en naranja vibrante combinada con destellos verdes de limpieza. Es un icono funcional que comunica claramente el propósito de "refrescar" o "limpiar" el sistema.

## Cambios Técnicos

- **Iconos Adaptativos**: Todas las aplicaciones ahora utilizan iconos adaptativos compatibles con las últimas versiones de Android, asegurando que se vean bien en cualquier lanzador (círculo, cuadrado, ardilla, etc.).
- **Vectores Optimizados**: Se utilizaron `VectorDrawables` puros para garantizar nitidez total en cualquier resolución de pantalla, sin importar la densidad de píxeles.
- **Coherencia Visual**: Se estableció una paleta de colores coherente que ayuda a distinguir las aplicaciones por su función (Azul = Trabajo, Dorado = Administración, Naranja/Verde = Soporte).

## Verificación

- [x] **Compilación Exitosa**: Se verificó que los recursos XML son válidos y compilan en las 3 aplicaciones.
- [x] **Compatibilidad API 26+**: Se configuraron las carpetas `mipmap-anydpi-v26` correctamente.

> [!TIP]
> Al instalar las aplicaciones, notarás que ahora tienen una presencia mucho más sólida en el menú de inicio del móvil, lo que aumenta la confianza del usuario final.
