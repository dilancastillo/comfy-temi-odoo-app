Comfy Temi Odoo App
Comfy es una aplicación Android desarrollada en Kotlin para el robot Temi V3, diseñada específicamente para entornos retail como tiendas de hogar y construcción. Integra la API JSON-RPC de Odoo 17 para proporcionar un catálogo interactivo parlante y visual con stock en tiempo real. Los usuarios pueden filtrar productos por categorías (piso, baño, grifería), visualizar fichas con imágenes, precios y stock por ubicación, y recibir notificaciones diarias de reposición. Soporta interacciones por voz y toque, ideal para asesores en tiendas.

Características Principales
- Catálogo Interactivo: Filtrado por categorías (Piso, Baño, Grifería) mediante toque o comandos de voz. Muestra productos con imágenes (base64 de Odoo), precios, stock real por tienda y descripciones.
- Integración con Odoo 17: Usa JSON-RPC para consultas en modelos como product.product, stock.quant y stock.move. Autenticación segura con API Key.
- Notificaciones de Reposición: Programadas diariamente con JobService para alertar sobre productos acabados, llegados o con cambios de precio, con salida por voz.
- Interfaz Responsiva: UI con Material Design, RecyclerView en grid para tablets/robots, animaciones fade-in y cards elevadas para un look moderno.
- Soporte para Temi V3: Integración con Temi SDK para speech (tts.speak), reconocimiento de voz y manejo de eventos.

Requisitos
- Android Studio (Koala o superior).
- Temi V3 robot para pruebas reales (emulador para desarrollo básico).
- Odoo 17 Enterprise con External API habilitada, API Key y módulos (Ventas, Inventario, Compras).
- Dependencias: Temi SDK 1.136.0, Retrofit 2.11.0, Gson, Glide 4.16.0, Material 1.12.0.

Dependencias
- Temi SDK: com.robotemi:sdk:1.136.0
- Retrofit: com.squareup.retrofit2:retrofit:2.11.0
- Gson Converter: com.squareup.retrofit2:converter-gson:2.11.0
- Glide: com.github.bumptech.glide:glide:4.16.0
- Material: com.google.android.material:material:1.12.0
- Otras: AndroidX (Core, AppCompat, ConstraintLayout, Fragment, RecyclerView).

Licencia
Desarrollado por Dilan Castillo. Para issues, abre un ticket en GitHub.
