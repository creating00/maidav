# ADR-000: Arquitectura actual de Maidav

**Estado:** aceptado como descripción del estado actual.
**Fecha:** 2026-09-18.
**Alcance:** registra la arquitectura observada; no aprueba un rediseño.

## Contexto

Maidav concentra la gestión comercial, catálogo, ventas, crédito, presupuestos y administración en una aplicación web Java. El relevamiento identifica Java 21, Spring Boot 3.5.9, Thymeleaf y PostgreSQL. La aplicación necesita controlar acceso basado en roles y permisos, mantener trazabilidad de datos y generar documentos PDF/XLSX.

Rutas relevantes: `src/main/resources/db/migration/`, `Dockerfile`, `docker-compose.yml`, `.codex/agents/` y `.codex/workflows/`.

## Decisión

La arquitectura actual es un **monolito MVC modular por paquete técnico y dominio**:

```text
Navegador
  -> Controladores MVC
    -> Servicios (*Service / *ServiceImpl)
      -> Repositorios JPA
        -> PostgreSQL
  -> Plantillas Thymeleaf y recursos estáticos
```

- Spring MVC atiende la interfaz renderizada en servidor con Thymeleaf.
- La persistencia se realiza con JPA directo y migraciones Flyway sobre PostgreSQL.
- Spring Security implementa inicio de sesión por formulario, RBAC, permisos, `@PreAuthorize` y un filtro de recálculo de permisos.
- Los archivos cargados usan filesystem y se exponen por `/uploads/**`.
- La ejecución se empaqueta en Docker; Compose define la aplicación y PostgreSQL. Render provee el despliegue detectado.

Esta ADR es descriptiva: no introduce una decisión nueva ni prescribe una migración arquitectónica.

## Consecuencias

### Positivas observadas

- El modelo MVC con JPA ofrece una ruta directa desde la interfaz hasta la persistencia.
- Las migraciones Flyway versionan la evolución del esquema (V1–V50).
- Las convenciones de servicios, rutas y permisos dan consistencia a los módulos.
- Docker y Compose permiten reproducir la topología aplicación–PostgreSQL.

### Costos y riesgos observados

- Las capas están acopladas al framework y a JPA; no hay puertos/adaptadores que aíslen infraestructura.
- Parte de la lógica y plantillas presenta alta concentración de tamaño (`CreditAccountServiceImpl.java`, `CreditAccountController.java`, `sales/form.html`, `products/index.html`).
- El filesystem de cargas requiere atención operativa y de seguridad en despliegues con disco persistente.
- No se detectó CI/CD versionado ni una cobertura de pruebas suficiente para dar retroalimentación automatizada amplia.

## Alternativas consideradas/no adoptadas

| Alternativa | Estado | Motivo documentado |
|---|---|---|
| Arquitectura hexagonal | No adoptada | No se detectaron límites de puertos/adaptadores; el acceso es JPA directo desde las capas actuales. |
| Microservicios | No adoptada | No se detectaron despliegues ni fronteras de servicio independientes. |
| Kubernetes | No adoptado | No se encontró configuración de Kubernetes. |
| CI/CD versionado | No adoptado/detectado | Render posee autodeploy, pero no se encontró una pipeline en el repositorio. |

## Pendientes de confirmación

- Validar en arranque la interacción entre `LocalDataSourceConfig` y `application-local.yml`.
- Confirmar las políticas efectivas de backup, retención y acceso al directorio de cargas en Render/Dockploy.

## Ausencias detectadas

No se encontraron APIs server-side de email, pagos, nube, colas o mensajería, ni pruebas Testcontainers o end-to-end. La ausencia se limita al repositorio relevado y no afirma que esos servicios no existan fuera de él.
