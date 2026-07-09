# GameStore UPN - Versión reparada

Migración corregida para usar lo que el profesor enseñó:

- Frontend: HTML + CSS + JavaScript puro
- Backend: Spring Boot
- Acceso a datos: JdbcTemplate dentro de Spring Boot, con dependencia Spring Data JPA incluida para el proyecto
- Base de datos: tu MySQL actual `tienda_videojuegos`

## Importante

Esta versión ya no usa React, Vite ni Node.js.

También se recuperó:

- Diseño oscuro estilo GameStore/Zerox del proyecto original
- Imágenes locales desde `frontend/img`
- RAWG API key tomada de tu proyecto original
- Rutas API iguales o equivalentes a las del backend Node.js
- Uso de tus tablas actuales: `usuarios`, `videojuegos`, `categorias`, `carrito`, `detalle_carrito`, `ventas`, `detalle_venta`, `videojuego_categorias`

## Antes de correr

Abre MySQL Workbench y ejecuta:

```sql
PARCHE_MYSQL_ACTUAL.sql
```

No crea otra base de datos. Solo asegura columnas/tablas necesarias para evitar errores.

## Configurar contraseña MySQL

Abre:

```text
backend/src/main/resources/application.properties
```

Revisa:

```properties
spring.datasource.username=root
spring.datasource.password=321654987
```

Cambia la contraseña si tu MySQL usa otra.

## Ejecutar

Desde VS Code:

```powershell
cd C:\Users\Mauricio\Desktop\GameStoreSpringUPN_Reparado\backend
mvn spring-boot:run
```

Luego abre:

```text
http://localhost:8080
```

## Usuarios de prueba

Usa los mismos usuarios que tienes en MySQL. Por ejemplo:

```text
cliente / 123456
admin / 123456
gerente / 123456
```
