# Sistema actual de Maidav

**Estado:** relevamiento descriptivo.
**Alcance:** estado presente detectado en el repositorio; no define una evolución futura.
**Fecha:** 2026-09-18.

## Resumen

Maidav es un monolito web MVC para gestión comercial. Renderiza la interfaz en servidor con Thymeleaf y persiste mediante JPA en PostgreSQL. La organización responde a capas técnicas por dominio (`controller`, `service`, `repository`, `model`); no se detectó arquitectura hexagonal.

## Hechos comprobados

### Plataforma y dependencias

| Área | Tecnología detectada |
|---|---|
| Lenguaje y compilación | Java 21; Maven Wrapper 3.9.12. |
| Framework | Spring Boot 3.5.9 con Spring MVC, Spring Data JPA, Spring Security y Thymeleaf. |
| Base de datos | PostgreSQL 16 en Compose; JDBC PostgreSQL 42.7.4; Flyway PostgreSQL. |
| Utilidades | Lombok; OpenPDF 2.2.2; Apache POI OOXML 5.3.0; WebP ImageIO 0.1.6. |
| Frontend | Thymeleaf, Tailwind CDN, Alpine.js CDN, Google Fonts y `html5-qrcode@2.3.8`. |

### Arquitectura y seguridad

- Monolito MVC modular por paquete técnico y dominio.
- Acceso a persistencia mediante JPA directo; las plantillas, recursos estáticos y migraciones integran el mismo despliegue.
- Auditoría JPA y zona horaria de Argentina configuradas en la aplicación.
- Inicio de sesión por formulario con Spring Security, RBAC, permisos y `@PreAuthorize`.
- Un filtro recalcula permisos durante el procesamiento de solicitudes.

### Módulos funcionales

| Dominio | Capacidades detectadas |
|---|---|
| Identidad | Usuarios, roles/permisos, perfil y recuperación de contraseña. |
| Clientes | Clientes y zonas. |
| Catálogo y abastecimiento | Proveedores, productos, stock, imágenes, EAN-13, listas, exportación y ajustes. |
| Ventas y crédito | Ventas e ítems, cuentas de crédito, cuotas, pagos masivos, anulaciones, mora y cambio de vendedor. |
| Presupuestos | Presupuestos, financiación y generación de PDF. |
| Administración | Dashboard, configuración de empresa y exportación PDF/XLSX. |

### Persistencia y archivos

- Existen 50 migraciones Flyway, de `V1` a `V50`, en `src/main/resources/db/migration/`.
- La migración `V48` incorpora `unaccent`.
- Los archivos cargados se almacenan en filesystem y se exponen mediante `/uploads/**`.

### Operación y despliegue

- `Dockerfile`: construcción multi-etapa Maven y ejecución con Temurin JRE 21.
- El entrypoint normaliza URL de PostgreSQL y crea el directorio de cargas.
- `docker-compose.yml` define aplicación y PostgreSQL, volúmenes y healthcheck de base de datos.
- Render está configurado con Blueprint, PostgreSQL, disco persistente, autodeploy y healthcheck en `/login`.
- Se detectaron Docker/Dockploy y `scripts/db-refresh-testing.sh` para sincronización entre producción y pruebas.

### Integraciones externas

- Enlace a WhatsApp mediante `wa.me`.
- Cámara y lectura de códigos desde el navegador mediante `html5-qrcode`.
- Dependencias de UI distribuidas por CDN y Google Fonts.
- Exportación PDF/Excel local mediante OpenPDF y Apache POI.

### Calidad y convenciones

- Se detectaron 11 pruebas JUnit/Mockito, incluyendo una con `@SpringBootTest`.
- Convenciones: `*Service`/`*ServiceImpl`, rutas MVC plurales, `POST` para mutaciones, permisos CRUD en mayúsculas y migraciones Flyway descriptivas.
- Hay workflows y roles de apoyo en `.codex/workflows/` y `.codex/agents/`.

## Ausencias detectadas

- No se detectó configuración de Kubernetes.
- No se detectó CI/CD versionado en el repositorio. El autodeploy de Render no sustituye una canalización CI.
- No se encontraron APIs server-side de correo, pagos, nube, colas o mensajería.
- No se detectaron pruebas con Testcontainers ni pruebas end-to-end.

## Pendientes de confirmación

- La coexistencia de `LocalDataSourceConfig` y `application-local.yml` puede producir conflicto de perfil local; requiere validación mediante arranque controlado.
- Este relevamiento no certifica configuraciones de infraestructura externas ni secretos desplegados.

## Rutas de referencia

| Ruta | Propósito |
|---|---|
| `src/main/resources/db/migration/` | Migraciones Flyway. |
| `Dockerfile` | Imagen de aplicación multi-etapa. |
| `docker-compose.yml` | Aplicación, PostgreSQL, volúmenes y healthcheck. |
| `scripts/db-refresh-testing.sh` | Sincronización de datos de producción a pruebas. |
| `.codex/agents/` | Roles de apoyo disponibles. |
| `.codex/workflows/` | Flujos de feature y bugfix. |

## Fuera de alcance

No se modificó código, configuración, dependencias ni infraestructura durante este relevamiento.
