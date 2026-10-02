# Calculadora de Materiales Drywall 🏗️

Herramienta profesional para la estimación de materiales de construcción ligera, gestión de proyectos y administración de negocios.

## 📱 Características

- **Calculadora Avanzada**: Estimación precisa de montantes, canales, láminas de yeso, tornillos y masilla.
- **Gestión de Proyectos**: Organiza tu trabajo por obras, zonas y fotos.
- **Gestión de Clientes**: Base de datos de clientes con sistema de validación de identidad.
- **Control de Inventario**: Seguimiento de materiales y transacciones.
- **Herramientas Financieras**: Gestión de cuentas bancarias, configuración de impuestos y precios de mano de obra.
- **Exportación PDF**: Genera presupuestos y reportes profesionales para tus clientes.
- **Diario de Obra**: Registro diario del progreso en tus sitios de construcción.
- **Escáner de Tarjetas**: Agrega detalles bancarios fácilmente usando la cámara.

## 🛠️ Stack Tecnológico

- **Kotlin**: Lenguaje moderno y expresivo.
- **Jetpack Compose**: Toolkit moderno para UI nativa en Android.
- **Hilt**: Inyección de dependencias.
- **Room**: Base de datos local para funcionamiento offline.
- **Coroutines & Flow**: Programación asíncrona y flujos de datos reactivos.
- **Arquitectura MVVM**: Separación limpia de responsabilidades.

## 🚀 Configuración

1. Clonar el repositorio.
2. Abrir con Android Studio (Ladybug o posterior).
3. Sincronizar Gradle y ejecutar el módulo `:app`.

## 🔒 Seguridad

Este proyecto sigue las mejores prácticas de seguridad:
- **Sin Credenciales Expuestas**: Las llaves de API y datos sensibles se manejan vía `local.properties`.
- **Validación de Datos**: Validación estricta de entrada para datos de clientes y registros financieros.

## 📄 Licencia

Copyright © 2026 Drywall Materials Calculator. Todos los derechos reservados.

## 🔄 Historial de Actualizaciones

### Versión 1.1.0 (Actual)
- **CRUD de Proveedores**: Gestión completa (Crear, Leer, Editar, Eliminar).
- **Reportes de Inventario**: Exportación directa a PDF desde la pantalla de Almacén.
- **Carnet Digital Profesional**: Rediseño completo con previsualización profesional.
- **Órdenes de Compra**: Flujo mejorado con exportación a PDF para evitar documentos en blanco.
- **Captura Inteligente**: Recorte automático de fotos para guardar solo el área del documento.
- **Perfil de Empresa**: Integración global del nombre del negocio en todos los documentos PDF.

### Versión 1.0.5
- Validación estricta de CI (Sexo por 10mo dígito).
- Escáner de tarjetas bancarias integrado en Perfil.
- Accesos rápidos en el Dashboard.
- Traducción completa al español.
